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
  readonly deletingImageId = signal<string | null>(null);
  readonly deleteError = signal<string | null>(null);
  private readonly preloadedImageUrls = new Set<string>();
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
  readonly visibleLightbox = computed(() => this.images().length > 0);

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

    const image = this.images()[index];
    this.preloadImage(image.url);
    this.preloadImage(this.images()[(index + 1) % this.images().length]?.url);
    this.preloadImage(
      this.images()[(index - 1 + this.images().length) % this.images().length]?.url,
    );
    this.selectedIndex.set(index);
    this.selectedImageFailed.set(false);
    this.deleteError.set(null);
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
    const image = this.images()[index];
    if (!image) {
      return;
    }

    this.preloadImage(image.url);
    this.selectedIndex.set(index);
    this.selectedImageFailed.set(false);
  }

  onImageIntent(image: EventImageDto): void {
    this.preloadImage(image.url);
  }

  canDelete(image: EventImageDto): boolean {
    return !this.publicMode() || image.canDelete === true;
  }

  deleteImage(image: EventImageDto, index: number): void {
    if (!this.canDelete(image) || this.deletingImageId()) {
      return;
    }

    const confirmed =
      this.document.defaultView?.confirm('Delete this photograph? This action cannot be undone.') ??
      true;
    if (!confirmed) {
      return;
    }

    const eventId = this.eventId();
    if (!eventId) {
      return;
    }

    this.deletingImageId.set(image.id);
    this.deleteError.set(null);
    const deletion$ = this.publicMode()
      ? this.imageService.deletePublicImage(eventId, image.id, this.galleryPassword())
      : this.imageService.deleteImage(image.id);

    deletion$.subscribe({
      next: () => {
        const wasSelected = this.selectedIndex() === index;
        this.allImages.update((images) => images.filter((candidate) => candidate.id !== image.id));
        const remainingCount = this.images().length;
        if (!remainingCount) {
          this.closeLightbox();
        } else if (this.selectedIndex() > index || wasSelected) {
          this.selectedIndex.set(Math.min(index, remainingCount - 1));
          this.selectedImageFailed.set(false);
        }
        this.deletingImageId.set(null);
      },
      error: () => {
        this.deletingImageId.set(null);
        this.deleteError.set('This photograph could not be deleted. Please try again.');
      },
    });
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

  private preloadImage(url: string | undefined): void {
    if (!url || this.preloadedImageUrls.has(url)) {
      return;
    }

    this.preloadedImageUrls.add(url);
    const image = new globalThis.Image();
    image.decoding = 'async';
    image.src = url;
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
        images.slice(0, 1).forEach((image) => this.preloadImage(image.url));
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
