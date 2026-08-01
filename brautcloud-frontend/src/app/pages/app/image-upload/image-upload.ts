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
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ObjectUrlRegistry } from '../../../core/object-url-registry';
import { ImageService } from '../../../services/image-service';
import { UserService } from '../../../services/user-service';
import { RouterLink } from '@angular/router';
import { AppShell } from '../../../components/app-shell/app-shell';
import { ToastService } from '../../../services/toast-service';

export interface SelectedFile {
  file: File;
  preview: string;
}

export interface UploadedImage {
  id: string;
  url: string;
}

@Component({
  selector: 'app-image-upload',
  imports: [RouterLink, AppShell],
  templateUrl: './image-upload.html',
  styles: ``,
})
export class ImageUpload {
  @ViewChild('confirmationCancelButton')
  private confirmationCancelButton?: ElementRef<HTMLButtonElement>;

  userService = inject(UserService);
  imageService = inject(ImageService);
  private readonly toastService = inject(ToastService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly objectUrls = new ObjectUrlRegistry();
  private returnFocusElement: HTMLElement | null = null;

  user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);

  readonly MAX_FILES = 100;

  selectedFiles = signal<SelectedFile[]>([]);
  uploadedImages = signal<UploadedImage[]>([]);
  isUploading = signal(false);
  uploadError = signal<string | null>(null);
  readonly pendingDeletionId = signal<string | null>(null);
  readonly deletingImageId = signal<string | null>(null);
  readonly deleteError = signal<string | null>(null);
  readonly deleteSuccess = signal<string | null>(null);
  readonly pendingDeletion = computed(() => {
    const imageId = this.pendingDeletionId();
    return imageId ? (this.uploadedImages().find((image) => image.id === imageId) ?? null) : null;
  });

  constructor() {
    effect(() => {
      if (this.pendingDeletion() && !this.deletingImageId()) {
        setTimeout(() => this.confirmationCancelButton?.nativeElement.focus());
      }
    });

    this.destroyRef.onDestroy(() => {
      this.releaseSelectedFiles();
      this.releaseUploadedImages();
      this.objectUrls.revokeAll();
    });
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (this.pendingDeletion() && !this.deletingImageId() && event.key === 'Escape') {
      event.preventDefault();
      this.cancelDeleteUploadedImage();
    }
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const validTypes = [
      'image/jpeg',
      'image/png',
      'image/webp',
      'image/gif',
      'image/heic',
      'image/heif',
    ];
    const newFiles = Array.from(input.files)
      .filter((file) => validTypes.includes(file.type))
      .map((file) => ({
        file,
        preview: this.objectUrls.create(file),
      }));

    const current = this.selectedFiles();
    const availableSlots = Math.max(this.MAX_FILES - current.length, 0);
    const acceptedFiles = newFiles.slice(0, availableSlots);
    newFiles.slice(acceptedFiles.length).forEach((file) => this.objectUrls.revoke(file.preview));
    this.selectedFiles.set([...current, ...acceptedFiles]);

    input.value = '';
  }

  removeSelectedFile(index: number): void {
    const current = this.selectedFiles();
    const file = current[index];
    if (!file) {
      return;
    }

    this.objectUrls.revoke(file.preview);
    this.selectedFiles.set(current.filter((_, i) => i !== index));
  }

  clearAllSelected(): void {
    this.releaseSelectedFiles();
    this.selectedFiles.set([]);
  }

  uploadSelected(): void {
    const eventId = this.event()?.id;
    if (!eventId || this.isUploading()) return;

    this.uploadError.set(null);
    this.isUploading.set(true);
    const files = this.selectedFiles();

    this.imageService
      .uploadImages(eventId, files)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => {
          const successfulFiles = new Set<SelectedFile>();
          const uploaded = results.flatMap((result, index) => {
            if (!result.success || !files[index]) {
              return [];
            }

            const selectedFile = files[index];
            successfulFiles.add(selectedFile);
            return [{ id: result.imageId, url: this.objectUrls.create(selectedFile.file) }];
          });
          if (uploaded.length) {
            this.uploadedImages.update((images) => [...images, ...uploaded]);
          }
          successfulFiles.forEach((file) => this.objectUrls.revoke(file.preview));
          this.selectedFiles.update((current) =>
            current.filter((file) => !successfulFiles.has(file)),
          );

          const failed = results.filter((r) => !r.success);
          const uploadedCount = results.length - failed.length;
          if (failed.length > 0) {
            const message = `${failed.length} ${failed.length === 1 ? 'photo' : 'photos'} could not be uploaded. Please try again.`;
            this.uploadError.set(message);
            this.toastService.show(message, 'error');
          } else if (uploadedCount > 0) {
            this.toastService.show(
              `${uploadedCount} ${uploadedCount === 1 ? 'photo' : 'photos'} uploaded successfully.`,
              'success',
            );
          }
          this.isUploading.set(false);
        },
        error: () => {
          const message = 'The upload could not be completed. Check your connection and try again.';
          this.uploadError.set(message);
          this.toastService.show(message, 'error');
          this.isUploading.set(false);
        },
      });
  }

  deleteUploadedImage(index: number): void {
    const image = this.uploadedImages()[index];
    if (!image || this.pendingDeletionId() || this.deletingImageId()) {
      return;
    }

    this.deleteError.set(null);
    this.deleteSuccess.set(null);
    this.returnFocusElement =
      this.document.activeElement instanceof HTMLElement ? this.document.activeElement : null;
    this.pendingDeletionId.set(image.id);
  }

  cancelDeleteUploadedImage(): void {
    if (!this.pendingDeletionId() || this.deletingImageId()) {
      return;
    }

    this.pendingDeletionId.set(null);
    this.deleteError.set(null);
    this.restoreFocus();
  }

  confirmDeleteUploadedImage(): void {
    const imageId = this.pendingDeletionId();
    if (!imageId || this.deletingImageId()) {
      return;
    }

    const image = this.uploadedImages().find((candidate) => candidate.id === imageId);
    if (!image) {
      this.pendingDeletionId.set(null);
      return;
    }

    this.deleteError.set(null);
    this.deleteSuccess.set(null);
    this.deletingImageId.set(image.id);
    this.imageService
      .deleteImage(image.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.objectUrls.revoke(image.url);
          this.uploadedImages.update((images) =>
            images.filter((candidate) => candidate.id !== image.id),
          );
          this.pendingDeletionId.set(null);
          this.deletingImageId.set(null);
          this.deleteSuccess.set('Photo deleted successfully.');
          this.toastService.show('Photo deleted successfully.', 'success');
          this.restoreFocus();
        },
        error: () => {
          const message = 'That photo could not be removed. Please try again.';
          this.pendingDeletionId.set(null);
          this.deletingImageId.set(null);
          this.deleteError.set(message);
          this.toastService.show(message, 'error');
          this.restoreFocus();
        },
      });
  }

  private releaseSelectedFiles(): void {
    this.selectedFiles().forEach((file) => this.objectUrls.revoke(file.preview));
  }

  private releaseUploadedImages(): void {
    this.uploadedImages().forEach((image) => this.objectUrls.revoke(image.url));
  }

  private restoreFocus(): void {
    const elementToFocus = this.returnFocusElement;
    this.returnFocusElement = null;
    setTimeout(() => {
      if (elementToFocus?.isConnected) {
        elementToFocus.focus();
      }
    });
  }
}
