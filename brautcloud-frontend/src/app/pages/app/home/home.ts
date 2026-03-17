import { Component, computed, inject } from '@angular/core';
import { QrCodeComponent } from 'ng-qrcode';
import { UserService } from '../../../services/user-service';
import { HomeStats } from './home-stats/home-stats';

import { APP_URL } from '../../../core/tokens';

@Component({
  selector: 'app-home',
  imports: [HomeStats, QrCodeComponent],
  templateUrl: './home.html',
  styles: ``,
})
export class Home {
  private userService = inject(UserService);

  private readonly user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);
  readonly APP_URL = inject(APP_URL);

  daysTillWedding = computed(() => {
    const user = this.user();
    const date = new Date();
    if (!user?.events?.length) return 0;

    const weddingDate = new Date(user.events[0].date);
    return Math.floor((weddingDate.getTime() - date.getTime()) / (1000 * 3600 * 24));
  });

  eventUrl = computed(() => `${this.APP_URL}/event/${this.event()?.id}`);

  photos = [
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

  stats = {
    photos: '1,243',
    guests: '340',
    views: '3.8k',
  };
}
