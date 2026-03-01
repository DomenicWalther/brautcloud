import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

interface LoginResponse {
  accessToken: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);

  login() {
    this.http
      .post<LoginResponse>('http://localhost:8080/api/auth/login', {
        email: 'test@mail.de',
        password: 'mypassword',
      })
      .subscribe((response) => {
        console.log(response);
      });
  }
}
