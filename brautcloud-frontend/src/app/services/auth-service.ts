import { LocationChangeListener } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap, finalize } from 'rxjs';

interface LoginResponse {
  accessToken: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);
  private accessToken = signal<string | null>(null);
  private initialized = signal(false);

  readonly isLoggedIn = computed(() => !!this.accessToken());
  readonly isInitialized = computed(() => this.initialized());

  private readonly BASE_URL = 'http://localhost:8080/api/auth';

  login(): void {
    this.http
      .post<LoginResponse>(
        `${this.BASE_URL}/login`,
        {
          email: 'test@mail.de',
          password: 'mypassword',
        },
        { withCredentials: true },
      )
      .subscribe((response) => {
        console.log(response);
        this.accessToken.set(response.accessToken);
      });
  }

  refresh(): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.BASE_URL}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((response) => {
          this.accessToken.set(response.accessToken);
        }),
        finalize(() => {
          this.initialized.set(true);
        }),
      );
  }

  logout(): void {
    this.accessToken.set(null);
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }
}
