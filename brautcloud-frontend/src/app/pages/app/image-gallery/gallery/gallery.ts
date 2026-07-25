import { Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ImageService } from '../../../../services/image-service';
import { EventImageDto } from '../../../../core/models/event-image.dto';
import { UserService } from '../../../../services/user-service';

@Component({
  selector: 'app-gallery',
  imports: [RouterLink],
  templateUrl: './gallery.html',
})
export class Gallery {
  private readonly imageService = inject(ImageService);
  private readonly userService = inject(UserService);

  private readonly allImages = signal<EventImageDto[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly images = this.allImages.asReadonly();
  readonly user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);

  private readonly eventId = computed(() => this.event()?.id);

  constructor() {
    effect(() => {
      const eventId = this.eventId();
      if (eventId === undefined) {
        this.allImages.set([]);
        this.loadError.set(null);
        this.loading.set(false);
        return;
      }

      this.loadAll(eventId);
    });
  }

  private loadAll(eventId: string): void {
    this.loading.set(true);
    this.loadError.set(null);

    this.imageService.getEventImages(eventId).subscribe({
      next: (images) => {
        this.allImages.set(images);
        this.loading.set(false);
      },
      error: () => {
        this.allImages.set([]);
        this.loadError.set('We could not load your gallery. Please try again from home.');
        this.loading.set(false);
      },
    });
  }
}
