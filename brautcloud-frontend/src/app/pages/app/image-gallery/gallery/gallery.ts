import { Component, computed, effect, inject, signal } from '@angular/core';
import { ImageService } from '../../../../services/image-service';
import { EventImageDto } from '../../../../core/models/event-image.dto';
import { UserService } from '../../../../services/user-service';

@Component({
  selector: 'app-gallery',
  imports: [],
  templateUrl: './gallery.html',
})
export class Gallery {
  private readonly imageService = inject(ImageService);
  private readonly userService = inject(UserService);

  private readonly allImages = signal<EventImageDto[]>([]);
  readonly loading = signal(true);

  readonly images = this.allImages.asReadonly();
  readonly user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);

  private readonly eventId = computed(() => this.event()?.id);

  constructor() {
    effect(() => {
      const eventId = this.eventId();
      if (eventId !== undefined) {
        this.loadAll(eventId);
      }
    });
  }

  private loadAll(eventId: string) {
    this.loading.set(true);

    this.imageService.getEventImages(eventId).subscribe((images) => {
      this.allImages.set(images);
      this.loading.set(false);
    });
  }
}
