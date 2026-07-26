import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { form, FormField, required, validate } from '@angular/forms/signals';
import { EventDto, EventUpdateDto } from '../../../core/models/event.dto';
import { AppShell } from '../../../components/app-shell/app-shell';
import { EventService } from '../../../services/event-service';
import { ToastService } from '../../../services/toast-service';
import { UserService } from '../../../services/user-service';

interface SettingsModel {
  eventName: string;
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  date: string;
  location: string;
}

@Component({
  selector: 'app-settings',
  imports: [AppShell, FormField],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings {
  private readonly eventService = inject(EventService);
  private readonly toastService = inject(ToastService);
  private readonly userService = inject(UserService);
  private initialized = false;

  readonly user = this.userService.user;
  readonly loading = this.userService.loading;
  readonly loadError = this.userService.error;
  readonly event = computed(() => this.user()?.events?.[0] ?? null);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly saved = signal(false);
  readonly model = signal<SettingsModel>({
    eventName: '',
    firstNameCoupleOne: '',
    firstNameCoupleTwo: '',
    date: '',
    location: '',
  });

  readonly savingPassword = signal(false);
  readonly savePasswordError = signal<string | null>(null);
  readonly passwordValue = signal('');

  readonly settingsForm = form(this.model, (schema) => {
    required(schema.eventName, { message: 'Please enter an event name' });
    required(schema.firstNameCoupleOne, { message: 'Please enter the first name' });
    required(schema.firstNameCoupleTwo, { message: 'Please enter the second name' });
    required(schema.date, { message: 'Please enter the event date' });
    required(schema.location, { message: 'Please enter a location' });
    validate(schema.date, ({ value }) => {
      if (!value() || /^\d{4}-\d{2}-\d{2}$/.test(value())) {
        return null;
      }

      return { kind: 'invalidDate', message: 'Please enter a valid event date' };
    });
  });

  constructor() {
    effect(() => {
      const event = this.event();
      if (!event || this.initialized) {
        return;
      }

      this.model.set(this.toSettingsModel(event));
      this.initialized = true;
    });
  }

  save(event: Event): void {
    event.preventDefault();
    const activeEvent = this.event();
    if (!activeEvent || this.settingsForm().invalid() || this.saving()) {
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);
    this.saved.set(false);

    const model = this.model();
    const payload: EventUpdateDto = {
      ...model,
      date: `${model.date}T00:00:00`,
    };

    this.eventService.updateEvent(activeEvent.id, payload).subscribe({
      next: (updatedEvent) => {
        this.model.set(this.toSettingsModel(updatedEvent));
        this.userService.reload();
        this.saving.set(false);
        this.saved.set(true);
        this.toastService.show('Event details saved.', 'success');
      },
      error: (response: HttpErrorResponse) => {
        const message =
          response.error?.message ?? 'We could not save your event details. Please try again.';
        this.saveError.set(message);
        this.saving.set(false);
        this.toastService.show(message, 'error');
      },
    });
  }

  savePassword(event: Event): void {
    event.preventDefault();
    const activeEvent = this.event();
    if (!activeEvent || this.savingPassword()) {
      return;
    }

    this.savingPassword.set(true);
    this.savePasswordError.set(null);

    const model = this.model();
    const payload: EventUpdateDto = {
      ...model,
      date: `${model.date}T00:00:00`,
      password: this.passwordValue(),
    };

    this.eventService.updateEvent(activeEvent.id, payload).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.passwordValue.set('');
        this.userService.reload();
        this.toastService.show('Gallery password saved.', 'success');
      },
      error: (response: HttpErrorResponse) => {
        const message =
          response.error?.message ?? 'We could not save the gallery password. Please try again.';
        this.savePasswordError.set(message);
        this.savingPassword.set(false);
        this.toastService.show(message, 'error');
      },
    });
  }

  onPasswordInput(event: Event): void {
    this.passwordValue.set((event.target as HTMLInputElement).value);
  }

  private toSettingsModel(event: EventDto): SettingsModel {
    return {
      eventName: event.eventName ?? '',
      firstNameCoupleOne: event.firstNameCoupleOne ?? '',
      firstNameCoupleTwo: event.firstNameCoupleTwo ?? '',
      date: event.date?.slice(0, 10) ?? '',
      location: event.location ?? '',
    };
  }
}
