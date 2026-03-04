import { Component, effect, inject } from '@angular/core';
import { ImageService } from '../../../services/image-service';

import { Gallery } from './gallery/gallery';

@Component({
  selector: 'app-image-gallery',
  imports: [Gallery],
  templateUrl: './image-gallery.html',
  styles: ``,
})
export class ImageGallery {

  imageService = inject(ImageService);
  readonly times = Array.from({ length: 12 }, (_, i) => i);
}
