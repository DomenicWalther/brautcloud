import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../core/tokens';

@Injectable({
  providedIn: 'root',
})
export class ImageService {

  private http: HttpClient = inject(HttpClient);
  private readonly API_URL = inject(API_URL);


  getEventImages(eventId: number, page: number, size = 20): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/events/${eventId}/images?page=${page}&size=${size}`, { withCredentials: true })
  }
}

