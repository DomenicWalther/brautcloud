import { AfterViewInit, Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { ImageService } from '../../../../services/image-service';

interface ImageDTO {
  id: number;
  url: string;
}

@Component({
  selector: 'app-gallery',
  imports: [],
  templateUrl: './gallery.html',
})
export class Gallery implements OnInit, AfterViewInit {

  private imageService = inject(ImageService);

  private allImages = signal<ImageDTO[]>([]);
  private eventId = 1;

  private currentPage = signal(0);

  hasMore = signal(true);
  loading = signal(false);

  images = this.allImages.asReadonly();

  @ViewChild('sentinel') sentinel!: ElementRef;

  ngAfterViewInit() {
    const observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting) {
        this.loadMore();
      }
    });
    observer.observe(this.sentinel.nativeElement);
  }

  loadMore() {
    console.log('load more');
    if (this.loading() || !this.hasMore()) return;

    this.loading.set(true);

    this.imageService.getEventImages(this.eventId, this.currentPage())
      .subscribe(response => {
        this.allImages.update(imgs => [...imgs, ...response.content]);
        this.hasMore.set(!response.last);
        this.currentPage.update(p => p + 1);
        this.loading.set(false);
      });
  }


  ngOnInit() {
    this.loadMore();
  }
}
