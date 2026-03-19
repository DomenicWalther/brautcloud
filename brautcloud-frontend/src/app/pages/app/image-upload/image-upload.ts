import { Component, computed, inject } from '@angular/core';
import { UserService } from '../../../services/user-service';

@Component({
  selector: 'app-image-upload',
  imports: [],
  templateUrl: './image-upload.html',
  styles: ``,
})
export class ImageUpload {
  userService = inject(UserService);

  user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);

  photos = [
    {
      url: 'https://placehold.co/200x200',
    },
    {
      url: 'https://placehold.co/200x200',
    },
    {
      url: 'https://placehold.co/200x200',
    },
  ];
}
