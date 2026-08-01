import { Component, computed, effect, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { QrCodeComponent } from 'ng-qrcode';
import { catchError, of, switchMap, tap } from 'rxjs';
import { APP_URL } from '../../../core/tokens';
import { AppShell } from '../../../components/app-shell/app-shell';
import { EventImageDto } from '../../../core/models/event-image.dto';
import { EventService } from '../../../services/event-service';
import { ImageService } from '../../../services/image-service';
import { UserService } from '../../../services/user-service';
import { HomeStats } from './home-stats/home-stats';
import { ToastService } from '../../../services/toast-service';

const GALLERY_PREVIEW_SLOTS = 3;
const MAX_SYNC_EXPORT_IMAGES = 100;
const MAX_SYNC_EXPORT_BYTES = 500 * 1024 * 1024;
const VIEWED_EVENT_SESSION_KEY_PREFIX = 'brautcloud-event-viewed-';

@Component({
  selector: 'app-home',
  imports: [HomeStats, QrCodeComponent, AppShell],
  templateUrl: './home.html',
  styles: ``,
})
export class Home {
  @ViewChild('qrContainer') private readonly qrContainer?: ElementRef<HTMLElement>;

  private readonly userService = inject(UserService);
  private readonly eventService = inject(EventService);
  private readonly imageService = inject(ImageService);
  private readonly APP_URL = inject(APP_URL);
  private readonly toastService = inject(ToastService);

  readonly user = this.userService.user;
  readonly loading = this.userService.loading;
  readonly loadError = this.userService.error;
  readonly event = computed(() => this.user()?.events?.[0] ?? null);
  readonly coupleName = computed(() => {
    const event = this.event();
    return event ? `${event.firstNameCoupleOne} & ${event.firstNameCoupleTwo}` : 'Your celebration';
  });

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

    effect(() => {
      const eventId = this.event()?.id;
      if (!eventId) {
        return;
      }

      const sessionKey = `${VIEWED_EVENT_SESSION_KEY_PREFIX}${eventId}`;
      if (sessionStorage.getItem(sessionKey)) {
        return;
      }

      sessionStorage.setItem(sessionKey, 'true');
      this.eventService.registerView(eventId).subscribe();
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

  private readonly weddingDate = computed<Date | null>(() => {
    const weddingDateValue = this.event()?.date;
    if (!weddingDateValue) {
      return null;
    }

    const weddingDate = new Date(weddingDateValue);
    return Number.isNaN(weddingDate.getTime()) ? null : weddingDate;
  });

  readonly weddingDatePassed = computed(() => {
    const weddingDate = this.weddingDate();
    return weddingDate !== null && weddingDate.getTime() < Date.now();
  });

  readonly daysTillWedding = computed<number | null>(() => {
    const weddingDate = this.weddingDate();
    if (!weddingDate || this.weddingDatePassed()) {
      return null;
    }

    return Math.ceil((weddingDate.getTime() - Date.now()) / (1000 * 3600 * 24));
  });

  readonly eventUrl = computed(() => {
    const eventId = this.event()?.id;
    return eventId ? `${this.APP_URL.replace(/\/$/, '')}/event/${eventId}` : null;
  });

  async copyGalleryLink(): Promise<void> {
    const url = this.eventUrl();
    if (!url) {
      this.toastService.show('Gallery link is not available yet.', 'warning');
      return;
    }

    try {
      await this.copyToClipboard(url);
      this.toastService.show('Gallery link copied.', 'success');
    } catch {
      this.toastService.show('Could not copy gallery link. Please try again.', 'error');
    }
  }

  readonly stats = computed(() => ({
    photos: String(this.allImages().length),
    guests: String(this.event()?.guestCount ?? 0),
    views: String(this.event()?.viewCount ?? 0),
  }));

  readonly downloadingAllPhotos = signal(false);
  readonly downloadAllPhotosError = signal<string | null>(null);

  downloadAllPhotos(): void {
    const activeEvent = this.event();
    if (!activeEvent || this.downloadingAllPhotos()) {
      return;
    }

    this.downloadAllPhotosError.set(null);
    if (this.allImages().length > MAX_SYNC_EXPORT_IMAGES) {
      const message =
        'This gallery is too large for a direct download. Download fewer than 100 photos at once.';
      this.downloadAllPhotosError.set(message);
      this.toastService.show(message, 'warning');
      return;
    }
    this.downloadingAllPhotos.set(true);

    this.eventService.downloadEventImages(activeEvent.id).subscribe({
      next: (response) => {
        this.downloadingAllPhotos.set(false);
        if (response.status === 204 || !response.body) {
          const message = 'No photos to download yet.';
          this.downloadAllPhotosError.set(message);
          this.toastService.show(message, 'warning');
          return;
        }

        if (response.body.size > MAX_SYNC_EXPORT_BYTES) {
          const message = 'This export exceeds the 500 MB download limit.';
          this.downloadAllPhotosError.set(message);
          this.toastService.show(message, 'warning');
          return;
        }

        const downloadUrl = URL.createObjectURL(response.body);
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = `${this.slugify(activeEvent.eventName)}-photos.zip`;
        link.click();
        URL.revokeObjectURL(downloadUrl);
        this.toastService.show('Photos downloaded successfully.', 'success');
      },
      error: () => {
        this.downloadingAllPhotos.set(false);
        const message = 'We could not download your photos. Please try again.';
        this.downloadAllPhotosError.set(message);
        this.toastService.show(message, 'error');
      },
    });
  }

  private async copyToClipboard(value: string): Promise<void> {
    if (window.isSecureContext && navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(value);
        return;
      } catch {
        // Continue to the legacy path when clipboard permission is unavailable.
      }
    }

    const textarea = document.createElement('textarea');
    textarea.value = value;
    textarea.setAttribute('readonly', '');
    textarea.setAttribute('aria-hidden', 'true');
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      if (!document.execCommand('copy')) {
        throw new Error('Clipboard fallback failed');
      }
    } finally {
      textarea.remove();
    }
  }

  downloadQrCode(): void {
    const canvas = this.qrContainer?.nativeElement?.querySelector(
      'canvas',
    ) as HTMLCanvasElement | null;

    if (!canvas) {
      this.toastService.show('QR code is not ready yet. Please try again.', 'warning');
      return;
    }

    const size = 1200;
    const offscreen = document.createElement('canvas');
    offscreen.width = size;
    offscreen.height = size;
    const ctx = offscreen.getContext('2d')!;
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(canvas, 0, 0, size, size);

    const dataUrl = offscreen.toDataURL('image/png');
    const eventName = this.event()?.eventName ?? 'event';
    const link = document.createElement('a');
    link.href = dataUrl;
    link.download = `${this.slugify(eventName)}-qr-code.png`;
    link.click();
    link.remove();
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
