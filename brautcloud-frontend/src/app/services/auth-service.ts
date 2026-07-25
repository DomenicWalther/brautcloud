import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  EMPTY,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  take,
  tap,
  throwError,
} from 'rxjs';
import { API_URL } from '../core/tokens';

interface AuthResponse {
  accessToken: string;
}

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

  private sessionGeneration = 0;
  private refreshRequest$: Observable<string> | null = null;

  initializeAuth(): Promise<void> {
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
    if (this._isLoggingOut()) {
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
        map((res) => {
          if (generation !== this.sessionGeneration || this._isLoggingOut()) {
            throw new Error('Refresh completed after the session was cleared');
          }

          this._accessToken.set(res.accessToken);
          return res.accessToken;
        }),
        catchError((err) => {
          if (generation === this.sessionGeneration) {
            this._accessToken.set(null);
          }
          return throwError(() => err);
        }),
        finalize(() => {
          if (this.refreshRequest$ === request$) {
            this.refreshRequest$ = null;
          }
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    this.refreshRequest$ = request$;
    return request$;
  }

  logout(): void {
    if (this._isLoggingOut()) {
      return;
    }

    const pendingRefresh$ = this.refreshRequest$
      ? this.refreshRequest$.pipe(
          take(1),
          catchError(() => of(null)),
        )
      : of(null);

    this._isLoggingOut.set(true);
    this.sessionGeneration += 1;
    this._accessToken.set(null);

    // The browser is locally signed out immediately. A separately retained stateless access JWT
    // can remain server-valid until its configured expiry (currently up to ten minutes).
    pendingRefresh$
      .pipe(
        switchMap(() =>
          this.http.post(
            `${this.API_URL}/auth/logout`,
            {},
            {
              withCredentials: true,
              responseType: 'text',
            },
          ),
        ),
        catchError(() => EMPTY),
        finalize(() => {
          this.refreshRequest$ = null;
          this._accessToken.set(null);
          this._isLoggingOut.set(false);
          this._initialized.next(true);
          void this.router.navigateByUrl('/auth/sign-in', { replaceUrl: true });
        }),
      )
      .subscribe();
  }

  canAttemptRefresh(requestUrl: string): boolean {
    return (
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
}
