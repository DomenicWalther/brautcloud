import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicEventDto } from '../../core/models/event.dto';
import { EventService } from '../../services/event-service';
import { ImageService, SelectedFile } from '../../services/image-service';
import { Gallery } from '../app/image-gallery/gallery/gallery';

const VIEWED_EVENT_SESSION_KEY_PREFIX = 'brautcloud-event-viewed-';

@Component({
  selector: 'app-event-gallery',
  imports: [RouterLink, Gallery],
  templateUrl: './event-gallery.html',
  styleUrl: './event-gallery.css',
})
export class EventGallery {
  private readonly eventService = inject(EventService);
  private readonly imageService = inject(ImageService);
  private readonly eventId = inject(ActivatedRoute).snapshot.paramMap.get('eventId');

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly event = signal<PublicEventDto | null>(null);
  readonly eventDate = computed(() => {
    const date = this.event()?.date;
    if (!date) {
      return null;
    }

    const parsedDate = new Date(date);
    return Number.isNaN(parsedDate.getTime())
      ? null
      : new Intl.DateTimeFormat(undefined, { dateStyle: 'long' }).format(parsedDate);
  });

  readonly enteredPassword = signal('');
  readonly passwordError = signal<string | null>(null);
  readonly verifiedPassword = signal<string | null>(null);
  readonly verifyingPassword = signal(false);

  readonly MAX_FILES = 100;
  readonly selectedFiles = signal<SelectedFile[]>([]);
  readonly isUploading = signal(false);
  readonly uploadError = signal<string | null>(null);
  readonly uploadSuccess = signal<string | null>(null);
  readonly galleryRefreshToken = signal(0);

  constructor() {
    if (!this.eventId) {
      this.error.set('This event link is missing its event identifier.');
      this.loading.set(false);
      return;
    }

    this.eventService.getPublicEvent(this.eventId).subscribe({
      next: (event) => {
        this.event.set(event);
        this.loading.set(false);
        if (!event.passwordProtected) {
          this.registerView(event.id);
        }
      },
      error: (response: HttpErrorResponse) => {
        this.error.set(
          response.status === 404
            ? 'This event link is no longer available.'
            : 'We could not load this event. Please try again.',
        );
        this.loading.set(false);
      },
    });
  }

  submitPassword(event: Event): void {
    event.preventDefault();
    if (this.verifyingPassword() || !this.eventId || !this.enteredPassword()) {
      return;
    }

    const password = this.enteredPassword();
    this.verifyingPassword.set(true);
    this.passwordError.set(null);

    this.imageService.getPublicEventImages(this.eventId, password).subscribe({
      next: () => {
        this.verifiedPassword.set(password);
        this.verifyingPassword.set(false);
        this.registerView(this.eventId!);
      },
      error: (response: HttpErrorResponse) => {
        if (response.status === 401) {
          this.passwordError.set('Incorrect password. Please try again.');
        } else {
          this.passwordError.set('We could not verify the password. Please try again.');
        }
        this.verifyingPassword.set(false);
      },
    });
  }

  onPasswordInput(event: Event): void {
    this.enteredPassword.set((event.target as HTMLInputElement).value);
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (this.isUploading() || !input.files?.length) {
      return;
    }

    const validTypes = [
      'image/jpeg',
      'image/png',
      'image/webp',
      'image/gif',
      'image/heic',
      'image/heif',
    ];
    const selected = Array.from(input.files);
    const validFiles = selected
      .filter((file) => validTypes.includes(file.type))
      .map((file) => ({ file, preview: URL.createObjectURL(file) }));
    const rejectedCount = selected.length - validFiles.length;
    const availableSlots = this.MAX_FILES - this.selectedFiles().length;

    if (rejectedCount > 0) {
      this.uploadError.set(
        `${rejectedCount} ${rejectedCount === 1 ? 'file is' : 'files are'} not a supported image type.`,
      );
    } else {
      this.uploadError.set(null);
    }
    this.uploadSuccess.set(null);

    const acceptedFiles = validFiles.slice(0, Math.max(availableSlots, 0));
    if (validFiles.length > acceptedFiles.length) {
      validFiles.slice(acceptedFiles.length).forEach((file) => URL.revokeObjectURL(file.preview));
      this.uploadError.set(`You can share up to ${this.MAX_FILES} photographs at once.`);
    }
    this.selectedFiles.update((files) => [...files, ...acceptedFiles]);
    input.value = '';
  }

  removeSelectedFile(index: number): void {
    const file = this.selectedFiles()[index];
    if (!file) {
      return;
    }
    URL.revokeObjectURL(file.preview);
    this.selectedFiles.update((files) => files.filter((_, fileIndex) => fileIndex !== index));
  }

  clearAllSelected(): void {
    this.selectedFiles().forEach((file) => URL.revokeObjectURL(file.preview));
    this.selectedFiles.set([]);
  }

  uploadSelected(): void {
    const activeEvent = this.event();
    const files = this.selectedFiles();
    if (!activeEvent || !files.length || this.isUploading()) {
      return;
    }

    this.uploadError.set(null);
    this.uploadSuccess.set(null);
    this.isUploading.set(true);

    this.imageService
      .uploadPublicImages(activeEvent.id, files, this.verifiedPassword() ?? undefined)
      .subscribe({
        next: (results) => {
          const successfulIndexes = new Set(
            results.flatMap((result, index) => (result.success ? [index] : [])),
          );
          files.forEach((file, index) => {
            if (successfulIndexes.has(index)) {
              URL.revokeObjectURL(file.preview);
            }
          });
          this.selectedFiles.set(files.filter((_, index) => !successfulIndexes.has(index)));

          const failedCount = results.filter((result) => !result.success).length;
          const uploadedCount = results.length - failedCount;
          if (failedCount > 0) {
            this.uploadError.set(
              `${failedCount} ${failedCount === 1 ? 'photograph could not' : 'photographs could not'} be shared. Please try again.`,
            );
          }
          if (uploadedCount > 0) {
            this.uploadSuccess.set(
              `${uploadedCount} ${uploadedCount === 1 ? 'photograph is' : 'photographs are'} now in the gallery.`,
            );
            this.galleryRefreshToken.update((token) => token + 1);
          }
          this.isUploading.set(false);
        },
        error: (response: HttpErrorResponse) => {
          if (response.status === 401 && this.event()?.passwordProtected) {
            this.verifiedPassword.set(null);
            this.passwordError.set(
              'Your gallery access expired. Enter the password again to continue.',
            );
          } else {
            this.uploadError.set('Your photographs could not be shared. Please try again.');
          }
          this.isUploading.set(false);
        },
      });
  }

  private registerView(eventId: string): void {
    const sessionKey = `${VIEWED_EVENT_SESSION_KEY_PREFIX}${eventId}`;
    if (sessionStorage.getItem(sessionKey)) {
      return;
    }

    sessionStorage.setItem(sessionKey, 'true');
    this.eventService.registerPublicView(eventId).subscribe({ error: () => undefined });
  }
}
