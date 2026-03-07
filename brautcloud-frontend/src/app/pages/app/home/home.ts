import { Component, computed, inject } from '@angular/core';
import { QrCodeComponent } from 'ng-qrcode';
import { UserService } from '../../../services/user-service';
import { HomeStats } from './home-stats/home-stats';

@Component({
  selector: 'app-home',
  imports: [HomeStats, QrCodeComponent],
  templateUrl: './home.html',
  styles: ``,
})
export class Home {

  private userService = inject(UserService);

  user = this.userService.user;


  daysTillWedding = computed(() => {
    const user = this.user();
    const date = new Date();
    if (!user?.events?.length) return 0;

    const weddingDate = new Date(user.events[0].date);
    return Math.floor((weddingDate.getTime() - date.getTime()) / (1000 * 3600 * 24));
  })

  eventUrl = 'https://www.domenicwalther.de';

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
