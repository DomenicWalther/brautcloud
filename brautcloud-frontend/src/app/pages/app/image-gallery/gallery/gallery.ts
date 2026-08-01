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
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of, Subscription } from 'rxjs';
import { EventImageDto } from '../../../../core/models/event-image.dto';
import { PublicEventDto } from '../../../../core/models/event.dto';
import { ImageService } from '../../../../services/image-service';
import { ToastService } from '../../../../services/toast-service';
import { UserService } from '../../../../services/user-service';

@Component({
  selector: 'app-gallery',
  imports: [RouterLink],
  templateUrl: './gallery.html',
})
export class Gallery {
  private readonly imageService = inject(ImageService);
  private readonly userService = inject(UserService);
  private readonly toastService = inject(ToastService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;
  @ViewChild('confirmationCancelButton')
  private confirmationCancelButton?: ElementRef<HTMLButtonElement>;

  private readonly allImages = signal<EventImageDto[]>([]);
  private readonly failedImageIds = signal<Set<string>>(new Set());
  readonly selectedIndex = signal(0);
  readonly selectedImageFailed = signal(false);
  readonly deletingImageId = signal<string | null>(null);
  readonly pendingDeleteIds = signal<Set<string>>(new Set());
  readonly deleteError = signal<string | null>(null);
  private readonly preloadedImages = new Map<string, HTMLImageElement>();
  private previousBodyOverflow = '';
  private loadRequestId = 0;
  private deleteSubscription?: Subscription;
  private returnFocusElement: HTMLElement | null = null;

  readonly publicMode = input(false);
  readonly publicEvent = input<PublicEventDto | null>(null);
  readonly galleryPassword = input<string | undefined>(undefined);
  readonly refreshToken = input(0);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly lightboxOpen = signal(false);
  readonly deleteConfirmation = signal<{ images: EventImageDto[]; indices: number[] } | null>(null);
  readonly selectedImageIds = signal<Set<string>>(new Set());

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
  readonly selectableImages = computed(() =>
    this.images().filter((image) => this.canDelete(image)),
  );
  readonly selectedImageCount = computed(() => this.selectedImageIds().size);

  private readonly eventId = computed(() => this.event()?.id);

  constructor() {
    effect(() => {
      const eventId = this.eventId();
      this.refreshToken();
      if (eventId === undefined) {
        this.loadRequestId += 1;
        this.deleteSubscription?.unsubscribe();
        this.deleteSubscription = undefined;
        this.pendingDeleteIds.set(new Set());
        this.deletingImageId.set(null);
        this.releasePreloadedImages();
        this.allImages.set([]);
        this.loadError.set(null);
        this.loading.set(false);
        return;
      }

      this.loadAll(eventId);
    });

    effect(() => {
      if (this.deleteConfirmation()) {
        setTimeout(() => this.confirmationCancelButton?.nativeElement.focus());
        return;
      }

      if (this.lightboxOpen()) {
        setTimeout(() => this.closeButton?.nativeElement.focus());
      }
    });

    this.destroyRef.onDestroy(() => {
      this.restoreBodyScroll();
      this.releasePreloadedImages();
    });
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (this.deleteConfirmation()) {
      if (event.key === 'Escape') {
        event.preventDefault();
        this.cancelDelete();
      }
      return;
    }

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

  toggleImageSelection(image: EventImageDto): void {
    if (!this.canDelete(image) || this.deletingImageId()) {
      return;
    }

    this.selectedImageIds.update((selected) => {
      const next = new Set(selected);
      if (next.has(image.id)) {
        next.delete(image.id);
      } else {
        next.add(image.id);
      }
      return next;
    });
  }

  selectAllImages(): void {
    this.selectedImageIds.set(new Set(this.selectableImages().map((image) => image.id)));
  }

  clearImageSelection(): void {
    this.selectedImageIds.set(new Set());
  }

  isImageSelected(imageId: string): boolean {
    return this.selectedImageIds().has(imageId);
  }

  deleteSelectedImages(): void {
    const selected = this.images().filter(
      (image) => this.selectedImageIds().has(image.id) && this.canDelete(image),
    );
    if (selected.length) {
      this.openDeleteConfirmation(selected);
    }
  }

  deleteImage(image: EventImageDto, _index: number): void {
    if (!this.canDelete(image) || this.deletingImageId()) {
      return;
    }

    const selected = this.images().filter(
      (candidate) => this.selectedImageIds().has(candidate.id) && this.canDelete(candidate),
    );
    this.openDeleteConfirmation(
      selected.includes(image) && selected.length > 1 ? selected : [image],
    );
  }

  private openDeleteConfirmation(images: EventImageDto[]): void {
    this.returnFocusElement =
      this.document.activeElement instanceof HTMLElement ? this.document.activeElement : null;
    this.deleteConfirmation.set({
      images,
      indices: images.map((image) =>
        this.images().findIndex((candidate) => candidate.id === image.id),
      ),
    });
  }

  cancelDelete(): void {
    if (!this.deleteConfirmation()) {
      return;
    }

    this.deleteConfirmation.set(null);
    this.restoreFocus();
  }

  confirmDelete(): void {
    const confirmation = this.deleteConfirmation();
    if (!confirmation || this.deletingImageId()) {
      return;
    }

    this.deleteConfirmation.set(null);
    const eventId = this.eventId();
    if (!eventId) {
      this.restoreFocus();
      return;
    }

    const { images, indices } = confirmation;
    const previousImages = this.images();
    const previousSelectedIndex = this.selectedIndex();
    const previousSelectedImage = previousImages[previousSelectedIndex];
    this.selectedImageIds.set(new Set());
    this.pendingDeleteIds.set(new Set(images.map((image) => image.id)));
    this.deletingImageId.set('__gallery_delete__');
    this.deleteError.set(null);

    const requests = images.map((image) => {
      const deletion$ = this.publicMode()
        ? this.imageService.deletePublicImage(eventId, image.id, this.galleryPassword())
        : this.imageService.deleteImage(image.id);
      return deletion$.pipe(catchError(() => of(null)));
    });

    this.deleteSubscription = forkJoin(requests)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((results) => {
        const successfulImages = images.filter((_, index) => results[index] !== null);
        const failedImages = images.filter((_, index) => results[index] === null);
        if (successfulImages.length) {
          this.allImages.update((current) =>
            current.filter((image) => !successfulImages.includes(image)),
          );
          const selectedImageStillVisible = previousSelectedImage
            ? this.images().some((image) => image.id === previousSelectedImage.id)
            : false;
          if (selectedImageStillVisible) {
            this.selectedIndex.set(
              this.images().findIndex((image) => image.id === previousSelectedImage!.id),
            );
          } else if (this.images().length) {
            const removedBeforeSelection = indices.filter(
              (index, resultIndex) =>
                index < previousSelectedIndex && results[resultIndex] !== null,
            ).length;
            this.selectedIndex.set(
              Math.min(
                Math.max(previousSelectedIndex - removedBeforeSelection, 0),
                this.images().length - 1,
              ),
            );
            this.selectedImageFailed.set(false);
          } else if (this.lightboxOpen()) {
            this.closeLightbox();
          }
        }

        if (failedImages.length) {
          this.deleteError.set(
            failedImages.length === images.length
              ? 'These photographs could not be deleted. Please try again.'
              : `${failedImages.length} of ${images.length} photographs could not be deleted. Please try again.`,
          );
          this.toastService.show(this.deleteError()!, 'error');
        } else {
          this.toastService.show(
            images.length === 1
              ? 'Photograph deleted successfully.'
              : `${images.length} photographs deleted successfully.`,
            'success',
          );
        }
        this.pendingDeleteIds.set(new Set());
        this.deletingImageId.set(null);
        this.restoreFocus();
        this.deleteSubscription = undefined;
      });
  }

  isImagePending(imageId: string): boolean {
    return this.pendingDeleteIds().has(imageId);
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
    if (!url || this.preloadedImages.has(url)) {
      return;
    }

    const image = new globalThis.Image();
    image.decoding = 'async';
    image.src = url;
    this.preloadedImages.set(url, image);
  }

  private restoreFocus(): void {
    const elementToFocus = this.returnFocusElement;
    this.returnFocusElement = null;
    setTimeout(() => {
      if (elementToFocus?.isConnected) {
        elementToFocus.focus();
      } else {
        this.closeButton?.nativeElement.focus();
      }
    });
  }

  private restoreBodyScroll(): void {
    if (this.document.body.style.overflow === 'hidden') {
      this.document.body.style.overflow = this.previousBodyOverflow;
    }
  }

  private loadAll(eventId: string): void {
    const requestId = ++this.loadRequestId;
    this.deleteSubscription?.unsubscribe();
    this.deleteSubscription = undefined;
    this.pendingDeleteIds.set(new Set());
    this.deletingImageId.set(null);
    this.releasePreloadedImages();
    this.loading.set(true);
    this.loadError.set(null);

    const images$ = this.publicMode()
      ? this.imageService.getPublicEventImages(eventId, this.galleryPassword())
      : this.imageService.getEventImages(eventId);

    images$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (images) => {
        if (requestId !== this.loadRequestId) {
          return;
        }

        this.allImages.set(images);
        images.slice(0, 1).forEach((image) => this.preloadImage(image.url));
        this.failedImageIds.set(new Set());
        this.selectedIndex.set(0);
        this.selectedImageFailed.set(false);
        this.loading.set(false);
      },
      error: () => {
        if (requestId !== this.loadRequestId) {
          return;
        }

        this.allImages.set([]);
        this.loadError.set('We could not load your gallery. Please try again from home.');
        this.loading.set(false);
      },
    });
  }

  private releasePreloadedImages(): void {
    this.preloadedImages.forEach((image) => {
      image.src = '';
    });
    this.preloadedImages.clear();
  }
}
