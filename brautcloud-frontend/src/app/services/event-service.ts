import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class EventService {
  private http = inject(HttpClient);

  private readonly BASE_URL = 'http://localhost:8080/api/events';

  getEvents(): void {
    this.http.get(this.BASE_URL).subscribe((events) => {
      console.log(events);
    });
  }
}
