import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';

interface LoginResponse {
  accessToken: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);
  private accessToken = signal<string | null>(null);

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

  refresh(onSuccess: (token: string) => void, onError: () => void): void {
    this.http
      .post<LoginResponse>(`${this.BASE_URL}/refresh`, {}, { withCredentials: true })
      .subscribe({
        next: (response) => {
          this.accessToken.set(response.accessToken);
          onSuccess(response.accessToken);
        },
        error: () => {
          this.logout();
          onError();
        },
      });
  }

  logout(): void {
    this.accessToken.set(null);
  }

  getAccessToken(): string | null {
    return this.accessToken();
  }

  isLoggedIn(): boolean {
    return this.accessToken() !== null;
  }
}
