import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { EventDto } from '../../../core/models/event.dto';
import { EventService } from '../../../services/event-service';
import { ToastService } from '../../../services/toast-service';
import { UserService } from '../../../services/user-service';
import { Settings } from './settings';

const eventFixture: EventDto = {
  id: 'event-1',
  userId: 'user-1',
  eventName: 'Müller wedding',
  firstNameCoupleOne: 'Sophie',
  firstNameCoupleTwo: 'Marcus',
  date: '2030-06-15T00:00:00',
  location: 'Eichenfürst',
  viewCount: 4,
  guestCount: 2,
};

function userFixture(event: EventDto = eventFixture) {
  return {
    id: 'user-1',
    createdAt: '2030-01-01T00:00:00',
    email: 'owner@example.com',
    emailVerified: true,
    onboardingComplete: true,
    events: [event],
  };
}

describe('Settings', () => {
  let fixture: ComponentFixture<Settings>;
  let component: Settings;
  let eventService: { updateEvent: ReturnType<typeof vi.fn> };
  let userService: {
    user: ReturnType<typeof signal>;
    loading: ReturnType<typeof signal>;
    error: ReturnType<typeof signal>;
    reload: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    eventService = { updateEvent: vi.fn(() => of(eventFixture)) };
    userService = {
      user: signal(userFixture()),
      loading: signal(false),
      error: signal<string | null>(null),
      reload: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [
        provideRouter([]),
        { provide: EventService, useValue: eventService },
        { provide: UserService, useValue: userService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    TestBed.inject(ToastService).clear();
    fixture.detectChanges();
    userService.reload.mockClear();
  });

  it('loads current event values into labeled settings fields', () => {
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector<HTMLInputElement>('input[autocomplete="organization"]')?.value).toBe(
      'Müller wedding',
    );
    expect(root.querySelector<HTMLInputElement>('input[type="date"]')?.value).toBe('2030-06-15');
    expect(root.textContent).toContain('Event settings');
    expect(root.querySelector('a[href="/app/settings"]')).toBeTruthy();
  });

  it('saves supported event edits and reports success', () => {
    component.model.set({
      eventName: 'Our wedding',
      firstNameCoupleOne: 'Sophie',
      firstNameCoupleTwo: 'Alex',
      date: '2031-07-20',
      location: 'Berlin',
    });
    fixture.detectChanges();

    component.save(new Event('submit'));
    fixture.detectChanges();

    expect(eventService.updateEvent).toHaveBeenCalledWith('event-1', {
      eventName: 'Our wedding',
      firstNameCoupleOne: 'Sophie',
      firstNameCoupleTwo: 'Alex',
      date: '2031-07-20T00:00:00',
      location: 'Berlin',
    });
    expect(userService.reload).toHaveBeenCalledOnce();
    expect(component.saved()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Changes saved');
  });

  it('blocks missing event dates before calling the update API', () => {
    component.model.update((model) => ({ ...model, date: '' }));
    fixture.detectChanges();

    component.save(new Event('submit'));

    expect(component.settingsForm().invalid()).toBe(true);
    expect(eventService.updateEvent).not.toHaveBeenCalled();
  });

  it('shows server errors without losing entered values', () => {
    const response = new Subject<EventDto>();
    eventService.updateEvent.mockReturnValue(response.asObservable());
    component.model.update((model) => ({ ...model, eventName: 'Updated wedding' }));
    fixture.detectChanges();

    component.save(new Event('submit'));
    response.error({ error: { message: 'Event date is required' } });
    fixture.detectChanges();

    expect(component.saveError()).toBe('Event date is required');
    expect(component.saving()).toBe(false);
    expect(component.model().eventName).toBe('Updated wedding');
    expect(fixture.nativeElement.textContent).toContain('Event date is required');
  });

  it('uses fallback copy for unexpected save errors', () => {
    eventService.updateEvent.mockReturnValue(throwError(() => new Error('offline')));
    component.save(new Event('submit'));

    expect(component.saveError()).toBe('We could not save your event details. Please try again.');
  });
});
