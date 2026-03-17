import {
  AfterViewInit,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  ViewChild,
} from '@angular/core';
import { ImageService } from '../../../../services/image-service';
import { EventImageDto } from '../../../../core/models/event-image.dto';
import { UserService } from '../../../../services/user-service';

@Component({
  selector: 'app-gallery',
  imports: [],
  templateUrl: './gallery.html',
})
export class Gallery implements AfterViewInit {
  private readonly imageService = inject(ImageService);
  private readonly userService = inject(UserService);
  private observer?: IntersectionObserver;

  @ViewChild('sentinel') sentinel!: ElementRef;

  private readonly allImages = signal<EventImageDto[]>([]);
  private readonly currentPage = signal(0);

  readonly images = this.allImages.asReadonly();
  readonly hasMore = signal(true);
  readonly loading = signal(false);
  readonly user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);

  private readonly eventId = computed(() => this.event()?.id);

  constructor() {
    effect(() => {
      const eventId = this.eventId();
      if (eventId !== undefined) {
        this.loadMore();
      }
    });
  }

  ngAfterViewInit() {
    this.observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) {
        this.loadMore();
      }
    });
    this.observer.observe(this.sentinel.nativeElement);
  }

  ngOnDestroy() {
    this.observer?.disconnect();
  }

  loadMore() {
    if (this.loading() || !this.hasMore()) return;

    const eventId = this.eventId();
    if (eventId === undefined) return;

    this.loading.set(true);

    this.imageService.getEventImages(eventId, this.currentPage()).subscribe((response) => {
      this.allImages.update((imgs) => [...imgs, ...response.content]);
      this.hasMore.set(!response.last);
      this.currentPage.update((p) => p + 1);
      this.loading.set(false);
    });
  }
}
