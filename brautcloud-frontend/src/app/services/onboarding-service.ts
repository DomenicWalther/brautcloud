import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { OnboardingDto, OnboardingResponse } from '../core/models/onboarding.dto';
import { API_URL } from '../core/tokens';

@Injectable({
  providedIn: 'root',
})
export class OnboardingService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = inject(API_URL);

  submitOnboarding(data: OnboardingDto): Observable<OnboardingResponse> {
    const payload = {
      ...data,
      date: data.date ? `${data.date}T00:00:00` : data.date,
    };

    return this.http.post<OnboardingResponse>(`${this.API_URL}/onboarding`, payload, {
      withCredentials: true,
    });
  }
}
