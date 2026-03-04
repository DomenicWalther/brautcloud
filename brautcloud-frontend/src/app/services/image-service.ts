import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ImageService {

  private http: HttpClient = inject(HttpClient);
  private readonly BASE_URL: string = 'http://localhost:8080/api/events/';


  getEventImages(): Observable<any> {
    return this.http.get<any>(`${this.BASE_URL}1/images?page=0&size=20`, { withCredentials: true })
  }
}

