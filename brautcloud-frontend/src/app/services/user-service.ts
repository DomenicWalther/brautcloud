import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { API_URL } from '../core/tokens';


interface Event {
  coupleName: string;
  date: string;
  eventName: string;
  id: number;
  location: string;
  userID: number;
}

interface UserResponse {
  createdAt: string;
  email: string;
  emailVerified: boolean;
  events: Event[];
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  id: number;
  lastName: string;
}

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private http: HttpClient = inject(HttpClient);
  private readonly API_URL = inject(API_URL);

  readonly user = toSignal(this.getUser(), { initialValue: undefined });

  private getUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.API_URL}/user`, { withCredentials: true })
  }


}
