import { AfterViewInit, Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { ImageService } from '../../../../services/image-service';
import { EventImageDTO } from '../../../../core/models/event-image-dto';

@Component({
  selector: 'app-gallery',
  imports: [],
  templateUrl: './gallery.html',
})
export class Gallery implements OnInit, AfterViewInit {

  private readonly imageService = inject(ImageService);
  private observer?: IntersectionObserver;


  @ViewChild('sentinel') sentinel!: ElementRef;

  private readonly allImages = signal<EventImageDTO[]>([]);
  private readonly currentPage = signal(0);
  private readonly eventId = 1;


  readonly images = this.allImages.asReadonly();
  readonly hasMore = signal(true);
  readonly loading = signal(false);

  ngAfterViewInit() {
    this.observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting) {
        this.loadMore();
      }
    });
    this.observer.observe(this.sentinel.nativeElement);
  }

  ngOnInit() {
    this.loadMore();
  }

  ngOnDestroy() {
    this.observer?.disconnect();
  }

  loadMore() {
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
}
