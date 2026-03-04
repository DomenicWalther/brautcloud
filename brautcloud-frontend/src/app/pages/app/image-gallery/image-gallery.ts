import { Component, effect, inject } from '@angular/core';
import { ImageService } from '../../../services/image-service';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-image-gallery',
  imports: [],
  templateUrl: './image-gallery.html',
  styles: ``,
})
export class ImageGallery {

  imageService = inject(ImageService);

  images = toSignal(
    this.imageService.getEventImages(),
    { initialValue: undefined },
  )

  constructor() {
    effect(() => {
      const i = this.images;
      console.log(i());
    })

  }



  readonly times = Array.from({ length: 12 }, (_, i) => i);
}
