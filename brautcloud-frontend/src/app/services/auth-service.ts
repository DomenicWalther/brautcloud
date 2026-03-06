import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import {
  Observable,
  tap,
  BehaviorSubject,
  filter,
  take,
  switchMap,
  catchError,
  throwError,
} from 'rxjs';

interface AuthResponse {
  accessToken: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);
  private readonly BASE_URL = 'http://localhost:8080/api/auth';

  private _accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this._accessToken() !== null);

  private _initialized = new BehaviorSubject<boolean>(false);
  readonly initialized$ = this._initialized.asObservable();

  private _isRefreshing = false;
  private _refreshTokenSubject = new BehaviorSubject<string | null>(null);

  initializeAuth(): Promise<void> {
    return new Promise((resolve) => {
      this.http
        .post<AuthResponse>(`${this.BASE_URL}/refresh`, {}, { withCredentials: true })
        .subscribe({
          next: (res) => {
            this._accessToken.set(res.accessToken);
            this._initialized.next(true);
            resolve();
          },
          error: () => {
            this._accessToken.set(null);
            this._initialized.next(true);
            resolve();
          },
        });
    });
  }

  login({ email, password }: { email: string; password: string }): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(
        `${this.BASE_URL}/login`,
        {
          email,
          password,
        },
        { withCredentials: true },
      )
      .pipe(tap((res) => this._accessToken.set(res.accessToken)));
  }

  register({ email, password }: { email: string; password: string }): Observable<any> {
    return this.http.post(
      `${this.BASE_URL}/register`,
      {
        email,
        password,
      },
      { withCredentials: true },
    );
  }

  refreshToken(): Observable<string> {
    if (this._isRefreshing) {
      return this._refreshTokenSubject.pipe(
        filter((token) => token !== null),
        take(1),
      ) as Observable<string>;
    }

    this._isRefreshing = true;
    this._refreshTokenSubject.next(null);

    return this.http
      .post<AuthResponse>(`${this.BASE_URL}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => {
          this._isRefreshing = false;
          this._accessToken.set(res.accessToken);
          this._refreshTokenSubject.next(res.accessToken);
        }),
        switchMap((res) => [res.accessToken]),
        catchError((err) => {
          this._isRefreshing = false;
          this._accessToken.set(null);
          return throwError(() => err);
        }),
      );
  }

  logout(): void {
    this._accessToken.set(null);
  }

  getAccessToken(): string | null {
    return this._accessToken();
  }
}
