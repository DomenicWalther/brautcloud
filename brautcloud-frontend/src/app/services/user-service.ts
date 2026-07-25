import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { UserDto } from '../core/models/user.dto';
import { API_URL } from '../core/tokens';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = inject(API_URL);

  private readonly _user = signal<UserDto | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly user = this._user.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  reload(): void {
    this._loading.set(true);
    this._error.set(null);

    this.http.get<UserDto>(`${this.API_URL}/user`, { withCredentials: true }).subscribe({
      next: (user) => {
        this._user.set(user);
        this._loading.set(false);
      },
      error: () => {
        this._user.set(null);
        this._error.set('We could not load your gallery. Please try again.');
        this._loading.set(false);
      },
    });
  }
}
