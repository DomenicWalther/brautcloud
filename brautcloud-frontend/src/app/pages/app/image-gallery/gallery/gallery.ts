import { DOCUMENT } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  ViewChild,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventImageDto } from '../../../../core/models/event-image.dto';
import { PublicEventDto } from '../../../../core/models/event.dto';
import { ImageService } from '../../../../services/image-service';
import { UserService } from '../../../../services/user-service';

@Component({
  selector: 'app-gallery',
  imports: [RouterLink],
  templateUrl: './gallery.html',
})
export class Gallery {
  private readonly imageService = inject(ImageService);
  private readonly userService = inject(UserService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;

  private readonly allImages = signal<EventImageDto[]>([]);
  private readonly failedImageIds = signal<Set<string>>(new Set());
  readonly selectedIndex = signal(0);
  readonly selectedImageFailed = signal(false);
  private previousBodyOverflow = '';
  private returnFocusElement: HTMLElement | null = null;

  readonly publicMode = input(false);
  readonly publicEvent = input<PublicEventDto | null>(null);
  readonly galleryPassword = input<string | undefined>(undefined);
  readonly refreshToken = input(0);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly lightboxOpen = signal(false);

  readonly images = this.allImages.asReadonly();
  readonly user = this.userService.user;
  readonly event = computed(() =>
    this.publicMode() ? this.publicEvent() : (this.user()?.events?.[0] ?? null),
  );
  readonly selectedImage = computed(() => this.images()[this.selectedIndex()] ?? null);
  readonly selectedPosition = computed(
    () => `${this.selectedIndex() + 1} of ${this.images().length}`,
  );

  private readonly eventId = computed(() => this.event()?.id);

  constructor() {
    effect(() => {
      const eventId = this.eventId();
      this.refreshToken();
      if (eventId === undefined) {
        this.allImages.set([]);
        this.loadError.set(null);
        this.loading.set(false);
        return;
      }

      this.loadAll(eventId);
    });

    effect(() => {
      if (!this.lightboxOpen()) {
        return;
      }

      setTimeout(() => this.closeButton?.nativeElement.focus());
    });

    this.destroyRef.onDestroy(() => this.restoreBodyScroll());
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (!this.lightboxOpen()) {
      return;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeLightbox();
      return;
    }

    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.navigate(-1);
      return;
    }

    if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.navigate(1);
    }
  }

  openLightbox(index: number): void {
    if (!this.images()[index]) {
      return;
    }

    this.selectedIndex.set(index);
    this.selectedImageFailed.set(false);
    this.returnFocusElement =
      this.document.activeElement instanceof HTMLElement ? this.document.activeElement : null;
    this.previousBodyOverflow = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    this.lightboxOpen.set(true);
  }

  closeLightbox(): void {
    if (!this.lightboxOpen()) {
      return;
    }

    this.lightboxOpen.set(false);
    this.restoreBodyScroll();
    const elementToFocus = this.returnFocusElement;
    this.returnFocusElement = null;
    setTimeout(() => elementToFocus?.focus());
  }

  navigate(direction: -1 | 1): void {
    const imageCount = this.images().length;
    if (!imageCount) {
      return;
    }

    this.selectedIndex.update((index) => (index + direction + imageCount) % imageCount);
    this.selectedImageFailed.set(false);
  }

  selectImage(index: number): void {
    if (!this.images()[index]) {
      return;
    }

    this.selectedIndex.set(index);
    this.selectedImageFailed.set(false);
  }

  isImageFailed(imageId: string): boolean {
    return this.failedImageIds().has(imageId);
  }

  onGalleryImageError(imageId: string): void {
    this.failedImageIds.update((failedIds) => new Set(failedIds).add(imageId));
  }

  onSelectedImageError(): void {
    this.selectedImageFailed.set(true);
  }

  private restoreBodyScroll(): void {
    if (this.document.body.style.overflow === 'hidden') {
      this.document.body.style.overflow = this.previousBodyOverflow;
    }
  }

  private loadAll(eventId: string): void {
    this.loading.set(true);
    this.loadError.set(null);

    const images$ = this.publicMode()
      ? this.imageService.getPublicEventImages(eventId, this.galleryPassword())
      : this.imageService.getEventImages(eventId);

    images$.subscribe({
      next: (images) => {
        this.allImages.set(images);
        this.failedImageIds.set(new Set());
        this.selectedIndex.set(0);
        this.selectedImageFailed.set(false);
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
