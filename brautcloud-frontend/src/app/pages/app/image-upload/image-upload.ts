import { Component, computed, inject, signal } from '@angular/core';
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
  userService = inject(UserService);
  imageService = inject(ImageService);
  private readonly toastService = inject(ToastService);

  user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);

  readonly MAX_FILES = 100;

  selectedFiles = signal<SelectedFile[]>([]);
  uploadedImages = signal<UploadedImage[]>([]);
  isUploading = signal(false);
  uploadError = signal<string | null>(null);

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
        preview: URL.createObjectURL(file),
      }));

    const current = this.selectedFiles();
    const combined = [...current, ...newFiles].slice(0, this.MAX_FILES);
    this.selectedFiles.set(combined);

    input.value = '';
  }

  removeSelectedFile(index: number): void {
    const current = this.selectedFiles();
    const file = current[index];
    URL.revokeObjectURL(file.preview);
    this.selectedFiles.set(current.filter((_, i) => i !== index));
  }

  clearAllSelected(): void {
    const current = this.selectedFiles();
    current.forEach((f) => URL.revokeObjectURL(f.preview));
    this.selectedFiles.set([]);
  }

  uploadSelected(): void {
    const eventId = this.event()?.id;
    if (!eventId || this.isUploading()) return;

    this.uploadError.set(null);
    this.isUploading.set(true);
    const files = this.selectedFiles();

    this.imageService.uploadImages(eventId, files).subscribe({
      next: (results) => {
        results.forEach((r, i) => {
          if (!r.success) return;
          const file = files[i]?.file;
          if (!file) return;
          this.uploadedImages.update((images) => [
            ...images,
            { id: r.imageId, url: URL.createObjectURL(file) },
          ]);
        });

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
        this.clearAllSelected();
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
    this.imageService.deleteImage(image.id).subscribe({
      next: () => {
        URL.revokeObjectURL(image.url);
        this.uploadedImages.update((images) => images.filter((_, i) => i !== index));
        this.toastService.show('Photo deleted successfully.', 'success');
      },
      error: () => {
        const message = 'That photo could not be removed. Please try again.';
        this.uploadError.set(message);
        this.toastService.show(message, 'error');
      },
    });
  }
}
