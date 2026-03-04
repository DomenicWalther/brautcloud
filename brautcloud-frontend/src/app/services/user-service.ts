import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';


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
  private readonly BASE_URL: string = 'http://localhost:8080/api/user';


  getUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>(this.BASE_URL, { withCredentials: true })
  }


}
