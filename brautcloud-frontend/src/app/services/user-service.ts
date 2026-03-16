import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { API_URL } from '../core/tokens';
import { UserDto } from '../core/models/user.dto';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private http: HttpClient = inject(HttpClient);
  private readonly API_URL = inject(API_URL);

  readonly user = toSignal(this.getUser(), { initialValue: undefined });

  private getUser(): Observable<UserDto> {
    return this.http.get<UserDto>(`${this.API_URL}/user`, { withCredentials: true });
  }
}
