import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  EMPTY,
  Observable,
  ReplaySubject,
  Subscription,
  tap,
  timeout,
  throwError,
} from 'rxjs';
import { API_URL } from '../core/tokens';

interface AuthResponse {
  accessToken: string;
}

const EXPLICIT_LOGOUT_STORAGE_KEY = 'brautcloud.explicit-logout';
const LOGOUT_REQUEST_TIMEOUT_MS = 1000;

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly API_URL = inject(API_URL);

  private readonly _accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this._accessToken() !== null);

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
      this._accessToken.set(null);
      return new Promise((resolve) => this.finishInitialization(resolve));
    }

    const generation = this.sessionGeneration;

    return new Promise((resolve) => {
      this.http
        .post<AuthResponse>(`${this.API_URL}/auth/refresh`, {}, { withCredentials: true })
        .subscribe({
          next: (res) => {
            if (generation === this.sessionGeneration && !this._isLoggingOut()) {
              this._accessToken.set(res.accessToken);
            }
            this.finishInitialization(resolve);
          },
          error: () => {
            if (generation === this.sessionGeneration) {
              this._accessToken.set(null);
            }
            this.finishInitialization(resolve);
          },
        });
    });
  }

  login({ email, password }: { email: string; password: string }): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(
        `${this.API_URL}/auth/login`,
        {
          email,
          password,
        },
        { withCredentials: true },
      )
      .pipe(
        tap((res) => {
          this.clearExplicitLogoutTombstone();
          this.sessionGeneration += 1;
          this._accessToken.set(res.accessToken);
        }),
      );
  }

  register({ email, password }: { email: string; password: string }): Observable<unknown> {
    return this.http.post(
      `${this.API_URL}/auth/register`,
      {
        email,
        password,
      },
      { withCredentials: true },
    );
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

          this._accessToken.set(res.accessToken);
          response$.next(res.accessToken);
          response$.complete();
          this.clearRefreshRequest(request$);
        },
        error: (err) => {
          if (generation === this.sessionGeneration) {
            this._accessToken.set(null);
          }
          response$.error(err);
          this.clearRefreshRequest(request$);
        },
      });

    return request$;
  }

  logout(): void {
    if (this._isLoggingOut()) {
      return;
    }

    this._isLoggingOut.set(true);
    this.sessionGeneration += 1;
    this.setExplicitLogoutTombstone();
    this._accessToken.set(null);
    this.cancelRefreshRequest();

    this.http
      .post(
        `${this.API_URL}/auth/logout`,
        {},
        {
          withCredentials: true,
          responseType: 'text',
        },
      )
      .pipe(
        timeout(LOGOUT_REQUEST_TIMEOUT_MS),
        catchError(() => EMPTY),
      )
      .subscribe({
        complete: () => {
          this._accessToken.set(null);
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

  private cancelRefreshRequest(): void {
    const request$ = this.refreshRequest$;
    const refreshRequestSubscription = this.refreshRequestSubscription;
    const refreshResponse$ = this.refreshResponse$;

    if (!request$ || !refreshRequestSubscription || !refreshResponse$) {
      this.clearRefreshRequest(null);
      return;
    }

    this.clearRefreshRequest(request$);
    refreshRequestSubscription.unsubscribe();
    refreshResponse$.error(new Error('Refresh canceled by logout'));
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
