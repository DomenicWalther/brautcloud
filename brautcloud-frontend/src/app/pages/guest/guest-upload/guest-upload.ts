import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { ImageService, SelectedFile } from '../../../services/image-service';
import { API_URL } from '../../../core/tokens';

interface GuestEventInfo {
  eventName: string;
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
}

@Component({
  selector: 'app-guest-upload',
  imports: [],
  templateUrl: './guest-upload.html',
  styles: ``,
})
export class GuestUpload implements OnInit {
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private imageService = inject(ImageService);
  private readonly API_URL = inject(API_URL);

  eventId = signal<string | null>(null);
  eventInfo = signal<GuestEventInfo | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);

  selectedFiles = signal<SelectedFile[]>([]);
  isUploading = signal(false);
  uploadCount = signal(0);

  readonly MAX_FILES = 100;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.eventId.set(id);

    if (!id) {
      this.error.set('Invalid event link.');
      this.isLoading.set(false);
      return;
    }

    this.http.get<GuestEventInfo>(`${this.API_URL}/events/${id}/guest`).subscribe({
      next: (info) => {
        this.eventInfo.set(info);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Event not found.');
        this.isLoading.set(false);
      },
    });
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const validTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic', 'image/heif'];
    const newFiles = Array.from(input.files)
      .filter((file) => validTypes.includes(file.type))
      .map((file) => ({ file, preview: URL.createObjectURL(file) }));

    const combined = [...this.selectedFiles(), ...newFiles].slice(0, this.MAX_FILES);
    this.selectedFiles.set(combined);
    input.value = '';
  }

  removeSelectedFile(index: number): void {
    const current = this.selectedFiles();
    URL.revokeObjectURL(current[index].preview);
    this.selectedFiles.set(current.filter((_, i) => i !== index));
  }

  clearAll(): void {
    this.selectedFiles().forEach((f) => URL.revokeObjectURL(f.preview));
    this.selectedFiles.set([]);
  }

  upload(): void {
    const id = this.eventId();
    if (!id || this.isUploading() || !this.selectedFiles().length) return;

    this.isUploading.set(true);
    const files = this.selectedFiles();

    this.imageService.uploadImages(id, files).subscribe({
      next: (results) => {
        const succeeded = results.filter((r) => r.success).length;
        this.uploadCount.update((n) => n + succeeded);
        this.clearAll();
        this.isUploading.set(false);
      },
      error: () => {
        this.isUploading.set(false);
      },
    });
  }
}
