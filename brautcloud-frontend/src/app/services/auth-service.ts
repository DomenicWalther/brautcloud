import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  EMPTY,
  Observable,
  of,
  ReplaySubject,
  Subscription,
  switchMap,
  take,
  tap,
  timeout,
  throwError,
} from 'rxjs';
import { AuthDTO, AuthResponse } from '../core/models/auth.dto';
import { API_URL } from '../core/tokens';

const EXPLICIT_LOGOUT_STORAGE_KEY = 'brautcloud.explicit-logout';
const LOGOUT_REQUEST_TIMEOUT_MS = 1000;
const LOGOUT_REFRESH_WAIT_MS = 1000;

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly API_URL = inject(API_URL);

  private readonly _accessToken = signal<string | null>(null);
  private readonly _onboardingComplete = signal(false);

  readonly isAuthenticated = computed(() => this._accessToken() !== null);
  readonly isOnboardingComplete = computed(
    () => this.isAuthenticated() && this._onboardingComplete(),
  );

  private readonly _initialized = new BehaviorSubject<boolean>(false);
  readonly initialized$ = this._initialized.asObservable();

  private readonly _isLoggingOut = signal(false);
  readonly isLoggingOut = this._isLoggingOut.asReadonly();

  private refreshBlocked = this.hasExplicitLogoutTombstone();
  private sessionGeneration = 0;
  private refreshRequest$: Observable<string> | null = null;
  private refreshRequestSubscription: Subscription | null = null;
  private refreshResponse$: ReplaySubject<string> | null = null;

  initializeAuth(): Promise<void> {
    if (this.refreshBlocked) {
      this.clearSession();
      return new Promise((resolve) => this.finishInitialization(resolve));
    }

    const generation = this.sessionGeneration;

    return new Promise((resolve) => {
      this.http
        .post<AuthResponse>(`${this.API_URL}/auth/refresh`, {}, { withCredentials: true })
        .subscribe({
          next: (response) => {
            if (generation === this.sessionGeneration && !this._isLoggingOut()) {
              this.applySession(response);
            }
            this.finishInitialization(resolve);
          },
          error: () => {
            if (generation === this.sessionGeneration) {
              this.clearSession();
            }
            this.finishInitialization(resolve);
          },
        });
    });
  }

  login(credentials: AuthDTO): Observable<AuthResponse> {
    return this.createSession(`${this.API_URL}/auth/login`, credentials);
  }

  register(credentials: AuthDTO): Observable<AuthResponse> {
    return this.createSession(`${this.API_URL}/auth/register`, credentials);
  }

  refreshToken(): Observable<string> {
    if (this._isLoggingOut() || this.refreshBlocked) {
      return throwError(() => new Error('Cannot refresh while signing out'));
    }

    if (this.refreshRequest$) {
      return this.refreshRequest$;
    }

    const generation = this.sessionGeneration;
    const response$ = new ReplaySubject<string>(1);
    const request$ = response$.asObservable();

    this.refreshResponse$ = response$;
    this.refreshRequest$ = request$;
    this.refreshRequestSubscription = this.http
      .post<AuthResponse>(`${this.API_URL}/auth/refresh`, {}, { withCredentials: true })
      .subscribe({
        next: (res) => {
          if (
            generation !== this.sessionGeneration ||
            this._isLoggingOut() ||
            this.refreshBlocked
          ) {
            response$.error(new Error('Refresh completed after the session was cleared'));
            this.clearRefreshRequest(request$);
            return;
          }

          this.applySession(res);
          response$.next(res.accessToken);
          response$.complete();
          this.clearRefreshRequest(request$);
        },
        error: (err) => {
          if (generation === this.sessionGeneration) {
            this.clearSession();
          }
          response$.error(err);
          this.clearRefreshRequest(request$);
        },
      });

    return request$;
  }

  markOnboardingComplete(): void {
    if (this.isAuthenticated()) {
      this._onboardingComplete.set(true);
    }
  }

  logout(): void {
    if (this._isLoggingOut()) {
      return;
    }

    const accessTokenAtLogout = this.getAccessToken();
    const pendingRefresh$ = this.refreshRequest$
      ? this.refreshRequest$.pipe(
          take(1),
          timeout(LOGOUT_REFRESH_WAIT_MS),
          catchError(() => of(null)),
        )
      : of(null);

    this._isLoggingOut.set(true);
    this.sessionGeneration += 1;
    this.setExplicitLogoutTombstone();
    this.clearSession();

    pendingRefresh$
      .pipe(
        switchMap(() =>
          this.http
            .post(
              `${this.API_URL}/auth/logout`,
              {},
              {
                withCredentials: true,
                responseType: 'text',
                ...(accessTokenAtLogout
                  ? { headers: { Authorization: `Bearer ${accessTokenAtLogout}` } }
                  : {}),
              },
            )
            .pipe(
              timeout(LOGOUT_REQUEST_TIMEOUT_MS),
              catchError(() => EMPTY),
            ),
        ),
      )
      .subscribe({
        complete: () => {
          this._isLoggingOut.set(false);
          this._initialized.next(true);
          void this.router.navigateByUrl('/auth/sign-in', { replaceUrl: true });
        },
      });
  }

  canAttemptRefresh(requestUrl: string): boolean {
    return (
      !this.refreshBlocked &&
      !this._isLoggingOut() &&
      !requestUrl.includes('/auth/refresh') &&
      !requestUrl.includes('/auth/logout')
    );
  }

  getAccessToken(): string | null {
    return this._accessToken();
  }

  private createSession(url: string, credentials: AuthDTO): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(url, credentials, { withCredentials: true }).pipe(
      tap((response) => {
        this.clearExplicitLogoutTombstone();
        this.sessionGeneration += 1;
        this.applySession(response);
      }),
    );
  }

  private applySession(response: AuthResponse): void {
    this._accessToken.set(response.accessToken);
    this._onboardingComplete.set(response.onboardingComplete);
  }

  private clearSession(): void {
    this._accessToken.set(null);
    this._onboardingComplete.set(false);
  }

  private finishInitialization(resolve: () => void): void {
    this._initialized.next(true);
    resolve();
  }

  private clearRefreshRequest(request$: Observable<string> | null): void {
    if (request$ && this.refreshRequest$ !== request$) {
      return;
    }

    this.refreshRequest$ = null;
    this.refreshRequestSubscription = null;
    this.refreshResponse$ = null;
  }

  private hasExplicitLogoutTombstone(): boolean {
    try {
      return globalThis.localStorage?.getItem(EXPLICIT_LOGOUT_STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  }

  private setExplicitLogoutTombstone(): void {
    this.refreshBlocked = true;

    try {
      globalThis.localStorage?.setItem(EXPLICIT_LOGOUT_STORAGE_KEY, 'true');
    } catch {}
  }

  private clearExplicitLogoutTombstone(): void {
    this.refreshBlocked = false;

    try {
      globalThis.localStorage?.removeItem(EXPLICIT_LOGOUT_STORAGE_KEY);
    } catch {}
  }
}
