import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  EMPTY,
  finalize,
  map,
  mergeMap,
  Observable,
  shareReplay,
  Subject,
  takeUntil,
  tap,
  timeout,
  throwError,
} from 'rxjs';
import { API_URL, LOGOUT_COMPLETION_DEADLINE_MS } from '../core/tokens';

interface AuthResponse {
  accessToken: string;
}

const EXPLICIT_LOGOUT_STORAGE_KEY = 'brautcloud.explicit-logout';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly API_URL = inject(API_URL);
  private readonly logoutCompletionDeadlineMs = inject(LOGOUT_COMPLETION_DEADLINE_MS);

  private readonly _accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this._accessToken() !== null);

  private readonly _initialized = new BehaviorSubject<boolean>(false);
  readonly initialized$ = this._initialized.asObservable();

  private readonly _isLoggingOut = signal(false);
  readonly isLoggingOut = this._isLoggingOut.asReadonly();

  private refreshBlocked = this.hasExplicitLogoutTombstone();
  private sessionGeneration = 0;
  private refreshRequest$: Observable<string> | null = null;
  private readonly refreshCancellation$ = new Subject<void>();

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
    let request$: Observable<string>;

    request$ = this.http
      .post<AuthResponse>(`${this.API_URL}/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        takeUntil(
          this.refreshCancellation$.pipe(
            mergeMap(() => throwError(() => new Error('Refresh canceled by logout'))),
          ),
        ),
        map((res) => {
          if (
            generation !== this.sessionGeneration ||
            this._isLoggingOut() ||
            this.refreshBlocked
          ) {
            throw new Error('Refresh completed after the session was cleared');
          }

          this._accessToken.set(res.accessToken);
          return res.accessToken;
        }),
        catchError((err) => {
          if (generation === this.sessionGeneration && !this.refreshBlocked) {
            this._accessToken.set(null);
          }
          return throwError(() => err);
        }),
        finalize(() => {
          if (this.refreshRequest$ === request$) {
            this.refreshRequest$ = null;
          }
        }),
        shareReplay({ bufferSize: 1, refCount: true }),
      );

    this.refreshRequest$ = request$;
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
        timeout(this.logoutCompletionDeadlineMs),
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

  private cancelRefreshRequest(): void {
    this.refreshCancellation$.next();
    this.refreshRequest$ = null;
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
