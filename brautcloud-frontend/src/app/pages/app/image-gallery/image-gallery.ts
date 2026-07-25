import { Component, computed, inject } from '@angular/core';

import { Gallery } from './gallery/gallery';
import { UserService } from '../../../services/user-service';
import { AppShell } from '../../../components/app-shell/app-shell';

@Component({
  selector: 'app-image-gallery',
  imports: [Gallery, AppShell],
  templateUrl: './image-gallery.html',
})
export class ImageGallery {
  private userService = inject(UserService);
  event = computed(() => this.userService.user()?.events?.[0]);
}
