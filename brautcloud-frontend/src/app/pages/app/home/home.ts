import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { QrCodeComponent } from 'ng-qrcode';
import { catchError, of, switchMap, tap } from 'rxjs';
import { APP_URL } from '../../../core/tokens';
import { EventImageDto } from '../../../core/models/event-image.dto';
import { EventService } from '../../../services/event-service';
import { ImageService } from '../../../services/image-service';
import { UserService } from '../../../services/user-service';
import { HomeStats } from './home-stats/home-stats';

const GALLERY_PREVIEW_SLOTS = 3;

@Component({
  selector: 'app-home',
  imports: [HomeStats, QrCodeComponent],
  templateUrl: './home.html',
  styles: ``,
})
export class Home {
  private readonly userService = inject(UserService);
  private readonly eventService = inject(EventService);
  private readonly imageService = inject(ImageService);
  private readonly APP_URL = inject(APP_URL);

  readonly user = this.userService.user;
  readonly loading = this.userService.loading;
  readonly loadError = this.userService.error;
  readonly event = computed(() => this.user()?.events?.[0] ?? null);

  private readonly allImages = signal<EventImageDto[]>([]);
  readonly imagesLoading = signal(true);

  private readonly eventId = computed(() => this.event()?.id);

  constructor() {
    toObservable(this.eventId)
      .pipe(
        tap(() => this.imagesLoading.set(true)),
        switchMap((eventId) => {
          if (!eventId) {
            return of<EventImageDto[]>([]);
          }
          return this.imageService
            .getEventImages(eventId)
            .pipe(catchError(() => of<EventImageDto[]>([])));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((images) => {
        this.allImages.set(images);
        this.imagesLoading.set(false);
      });
  }

  readonly previewPhotos = computed(() => {
    const images = this.allImages();
    const shownCount = Math.min(images.length, GALLERY_PREVIEW_SLOTS);
    const remaining = images.length - shownCount;

    return images.slice(0, shownCount).map((image, index) => ({
      url: image.url,
      extraCount: index === shownCount - 1 && remaining > 0 ? remaining : null,
    }));
  });

  readonly daysTillWedding = computed<number | null>(() => {
    const weddingDateValue = this.event()?.date;
    if (!weddingDateValue) {
      return null;
    }

    const weddingDate = new Date(weddingDateValue);
    if (Number.isNaN(weddingDate.getTime())) {
      return null;
    }

    return Math.ceil((weddingDate.getTime() - Date.now()) / (1000 * 3600 * 24));
  });

  readonly eventUrl = computed(() => {
    const eventId = this.event()?.id;
    return eventId ? `${this.APP_URL}/event/${eventId}` : null;
  });

  readonly stats = computed(() => ({
    photos: String(this.allImages().length),
    guests: '340',
    views: '3.8k',
  }));

  readonly downloadingAllPhotos = signal(false);
  readonly downloadAllPhotosError = signal<string | null>(null);

  downloadAllPhotos(): void {
    const activeEvent = this.event();
    if (!activeEvent || this.downloadingAllPhotos()) {
      return;
    }

    this.downloadAllPhotosError.set(null);
    this.downloadingAllPhotos.set(true);

    this.eventService.downloadEventImages(activeEvent.id).subscribe({
      next: (response) => {
        this.downloadingAllPhotos.set(false);
        if (response.status === 204 || !response.body) {
          this.downloadAllPhotosError.set('No photos to download yet.');
          return;
        }

        const downloadUrl = URL.createObjectURL(response.body);
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = `${this.slugify(activeEvent.eventName)}-photos.zip`;
        link.click();
        URL.revokeObjectURL(downloadUrl);
      },
      error: () => {
        this.downloadingAllPhotos.set(false);
        this.downloadAllPhotosError.set('We could not download your photos. Please try again.');
      },
    });
  }

  private slugify(value: string): string {
    return (
      value
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '') || 'event'
    );
  }
}
