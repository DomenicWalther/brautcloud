import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ImageService {

  private http: HttpClient = inject(HttpClient);
  private readonly BASE_URL: string = 'http://localhost:8080/api/events/';


  getEventImages(eventId: number, page: number, size = 20): Observable<any> {
    return this.http.get<any>(`${this.BASE_URL}${eventId}/images?page=${page}&size=${size}`, { withCredentials: true })
  }
}

