import { Component, inject } from '@angular/core';
import { HomeStats } from './home-stats/home-stats';
import { QrCodeComponent } from 'ng-qrcode';
import { UserService } from '../../../services/user-service';
import { toSignal } from '@angular/core/rxjs-interop';


interface Event {
  coupleName: string;
  date: string;
  eventName: string;
  id: number;
  location: string;
  userID: number;
}

interface UserResponse {
  createdAt: string;
  email: string;
  emailVerified: boolean;
  events: Event[];
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  id: number;
  lastName: string;
}

@Component({
  selector: 'app-home',
  imports: [HomeStats, QrCodeComponent],
  templateUrl: './home.html',
  styles: ``,
})
export class Home {

  private userService = inject(UserService);

  user = toSignal(
    this.userService.getUser(),
    { initialValue: undefined },
  )

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
  daysTillWedding: number = 13;

  stats = {
    photos: '1,243',
    guests: '340',
    views: '3.8k',
  };
}
