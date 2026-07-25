import { Component, computed, inject } from '@angular/core';
import { QrCodeComponent } from 'ng-qrcode';
import { APP_URL } from '../../../core/tokens';
import { UserService } from '../../../services/user-service';
import { HomeStats } from './home-stats/home-stats';

@Component({
  selector: 'app-home',
  imports: [HomeStats, QrCodeComponent],
  templateUrl: './home.html',
  styles: ``,
})
export class Home {
  private readonly userService = inject(UserService);
  private readonly APP_URL = inject(APP_URL);

  readonly user = this.userService.user;
  readonly loading = this.userService.loading;
  readonly loadError = this.userService.error;
  readonly event = computed(() => this.user()?.events?.[0] ?? null);

  readonly daysTillWedding = computed<number | null>(() => {
    const weddingDateValue = this.event()?.date;
    if (!weddingDateValue) {
      return null;
    }

    const weddingDate = new Date(weddingDateValue);
    if (Number.isNaN(weddingDate.getTime())) {
      return null;
    }

    return Math.ceil((weddingDate.getTime() - Date.now()) / (1000 * 3600 * 24));
  });

  readonly eventUrl = computed(() => {
    const eventId = this.event()?.id;
    return eventId ? `${this.APP_URL}/event/${eventId}` : null;
  });

  readonly photos = [
    {
      url: 'https://placehold.co/200x200',
    },
    {
      url: 'https://placehold.co/200x200',
    },
    {
      url: 'https://placehold.co/200x200',
      extraCount: '1.5k',
    },
  ];

  readonly stats = {
    photos: '1,243',
    guests: '340',
    views: '3.8k',
  };
}
