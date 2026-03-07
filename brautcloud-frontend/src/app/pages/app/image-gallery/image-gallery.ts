import {Component, inject} from '@angular/core';

import {Gallery} from './gallery/gallery';
import {UserService} from '../../../services/user-service';

@Component({
  selector: 'app-image-gallery',
  imports: [Gallery],
  templateUrl: './image-gallery.html',
})
export class ImageGallery {
  private userService = inject(UserService);
  user = this.userService.user;
}
