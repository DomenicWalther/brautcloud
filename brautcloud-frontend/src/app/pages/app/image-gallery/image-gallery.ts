import { Component, effect, inject } from '@angular/core';
import { ImageService } from '../../../services/image-service';

import { Gallery } from './gallery/gallery';
import { UserService } from '../../../services/user-service';

@Component({
  selector: 'app-image-gallery',
  imports: [Gallery],
  templateUrl: './image-gallery.html',
  styles: ``,
})
export class ImageGallery {

  imageService = inject(ImageService);
  private userService = inject(UserService);
  user = this.userService.user;
}
