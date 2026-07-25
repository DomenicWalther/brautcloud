import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../core/tokens';

const VISITOR_ID_STORAGE_KEY = 'brautcloud-visitor-id';

@Injectable({
  providedIn: 'root',
})
export class EventService {
  private http = inject(HttpClient);

  private readonly API_URL = inject(API_URL);

  registerView(eventId: string): Observable<void> {
    return this.http.post<void>(
      `${this.API_URL}/events/${eventId}/view`,
      { visitorId: this.getOrCreateVisitorId() },
      { withCredentials: true },
    );
  }

  private getOrCreateVisitorId(): string {
    let visitorId = localStorage.getItem(VISITOR_ID_STORAGE_KEY);
    if (!visitorId) {
      visitorId = crypto.randomUUID();
      localStorage.setItem(VISITOR_ID_STORAGE_KEY, visitorId);
    }
    return visitorId;
  }

  downloadEventImages(eventId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.API_URL}/events/${eventId}/images/download`, {
      withCredentials: true,
      responseType: 'blob',
      observe: 'response',
    });
  }
}
