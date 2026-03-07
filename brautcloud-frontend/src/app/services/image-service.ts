import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../core/tokens';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface EventImageDTO {
  id: number;
  url: string;
}

@Injectable({
  providedIn: 'root',
})
export class ImageService {

  private http: HttpClient = inject(HttpClient);
  private readonly API_URL = inject(API_URL);

  getEventImages(eventId: number, page: number, size = 20): Observable<Page<EventImageDTO>> {
    return this.http.get<Page<EventImageDTO>>(`${this.API_URL}/events/${eventId}/images?page=${page}&size=${size}`, { withCredentials: true })
  }
}

