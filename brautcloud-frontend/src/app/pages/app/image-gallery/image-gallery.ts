import { Component } from '@angular/core';

@Component({
  selector: 'app-image-gallery',
  imports: [],
  templateUrl: './image-gallery.html',
  styles: ``,
})
export class ImageGallery {
  readonly times = Array.from({ length: 12 }, (_, i) => i);
}
