import { inject, Injectable } from '@angular/core';
import { OnboardingDto } from '../core/models/onboarding.dto';
import { HttpClient } from '@angular/common/http';
import { API_URL } from '../core/tokens';
import { Observable } from 'rxjs';
import { UserDto } from '../core/models/user.dto';

@Injectable({
  providedIn: 'root',
})
export class OnboardingService {
  private http: HttpClient = inject(HttpClient);
  private readonly API_URL = inject(API_URL);

  submitOnboarding(data: OnboardingDto): Observable<UserDto> {
    return this.http.patch<UserDto>(`${this.API_URL}/user`, data, { withCredentials: true });
  }
}
