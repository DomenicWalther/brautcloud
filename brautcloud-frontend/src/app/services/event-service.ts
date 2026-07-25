import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../core/tokens';

@Injectable({
  providedIn: 'root',
})
export class EventService {
  private http = inject(HttpClient);

  private readonly API_URL = inject(API_URL);

  getEvents(): void {
    this.http.get(`${this.API_URL}/events`).subscribe((events) => {
      console.log(events);
    });
  }

  downloadEventImages(eventId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.API_URL}/events/${eventId}/images/download`, {
      withCredentials: true,
      responseType: 'blob',
      observe: 'response',
    });
  }
}
