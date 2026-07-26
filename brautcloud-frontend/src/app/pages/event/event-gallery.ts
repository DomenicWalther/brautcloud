import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicEventDto } from '../../core/models/event.dto';
import { EventService } from '../../services/event-service';
import { ImageService } from '../../services/image-service';
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

  private registerView(eventId: string): void {
    const sessionKey = `${VIEWED_EVENT_SESSION_KEY_PREFIX}${eventId}`;
    if (sessionStorage.getItem(sessionKey)) {
      return;
    }

    sessionStorage.setItem(sessionKey, 'true');
    this.eventService.registerPublicView(eventId).subscribe({ error: () => undefined });
  }
}
