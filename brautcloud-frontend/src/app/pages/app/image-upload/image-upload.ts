import { Component, computed, inject, signal } from '@angular/core';
import { ImageService } from '../../../services/image-service';
import { UserService } from '../../../services/user-service';
import { RouterLink } from '@angular/router';

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
  imports: [RouterLink],
  templateUrl: './image-upload.html',
  styles: ``,
})
export class ImageUpload {
  userService = inject(UserService);
  imageService = inject(ImageService);

  user = this.userService.user;
  readonly event = computed(() => this.user()?.events?.[0]);

  readonly MAX_FILES = 100;

  selectedFiles = signal<SelectedFile[]>([]);
  uploadedImages = signal<UploadedImage[]>([]);
  isUploading = signal(false);

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

    this.isUploading.set(true);
    const files = this.selectedFiles();

    this.imageService.uploadImages(eventId, files).subscribe({
      next: (results) => {
        const successful = results.filter((r) => r.success);
        successful.forEach((r) => {
          this.uploadedImages.update((images) => [
            ...images,
            { id: r.imageId, url: URL.createObjectURL(files[results.indexOf(r)].file) },
          ]);
        });

        const failed = results.filter((r) => !r.success);
        if (failed.length > 0) {
          console.error('Some uploads failed:', failed);
        }
        this.clearAllSelected();
        this.isUploading.set(false);
      },
      error: (err) => {
        console.error('Upload failed:', err);
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
      },
      error: (err) => {
        console.error('Failed to delete image:', err);
      },
    });
  }
}
