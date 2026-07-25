import { HttpResponse } from '@angular/common/http';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { QrCodeComponent } from 'ng-qrcode';
import { of, Subject, throwError } from 'rxjs';
import { APP_URL } from '../../../core/tokens';
import { EventImageDto } from '../../../core/models/event-image.dto';
import { EventDto } from '../../../core/models/event.dto';
import { UserDto } from '../../../core/models/user.dto';
import { EventService } from '../../../services/event-service';
import { ImageService } from '../../../services/image-service';
import { UserService } from '../../../services/user-service';
import { Home } from './home';

@Component({
  selector: 'qr-code',
  template: '',
})
class StubQrCode {
  value = input('');
  size = input<number>();
  errorCorrectionLevel = input('M');
  styleClass = input('');
}

function eventFixture(overrides: Partial<EventDto> = {}): EventDto {
  return {
    id: 'event-1',
    date: null,
    eventName: 'Wedding',
    firstNameCoupleOne: 'Alex',
    firstNameCoupleTwo: 'Sam',
    location: 'Berlin',
    userId: 'user-1',
    viewCount: 0,
    guestCount: 0,
    ...overrides,
  };
}

describe('Home defensive empty state', () => {
  const user = signal<UserDto | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const eventService = {
    downloadEventImages: vi.fn(),
    registerView: vi.fn(),
  };
  const imageService = {
    getEventImages: vi.fn(),
  };
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    user.set(null);
    loading.set(false);
    error.set(null);
    sessionStorage.clear();
    eventService.downloadEventImages.mockReset();
    eventService.registerView.mockReset().mockReturnValue(of(undefined));
    imageService.getEventImages.mockReset();

    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideRouter([]),
        { provide: APP_URL, useValue: 'http://app.test' },
        {
          provide: UserService,
          useValue: {
            user: user.asReadonly(),
            loading: loading.asReadonly(),
            error: error.asReadonly(),
          },
        },
        { provide: EventService, useValue: eventService },
        { provide: ImageService, useValue: imageService },
      ],
    })
      .overrideComponent(Home, {
        remove: { imports: [QrCodeComponent] },
        add: { imports: [StubQrCode] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(Home);
  });

  it('does not render broken names, countdowns, stats, or QR data without an event', () => {
    fixture.detectChanges();
    const content = fixture.nativeElement.textContent as string;

    expect(content).toContain('No gallery is available yet');
    expect(content).not.toContain('Welcome,');
    expect(content).not.toContain('days until your wedding');
    expect(content).not.toContain('Live gallery');
    expect(fixture.nativeElement.querySelector('qr-code')).toBeNull();
    expect(imageService.getEventImages).not.toHaveBeenCalled();
    expect(eventService.registerView).not.toHaveBeenCalled();
  });

  it('renders explicit loading and error states', () => {
    loading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading your gallery');

    loading.set(false);
    error.set('Unable to load');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Your gallery is temporarily unavailable');
    expect(fixture.nativeElement.textContent).toContain('Unable to load');
  });
});

describe('Home gallery preview and photos stat', () => {
  const user = signal<UserDto | null>(null);
  const eventService = {
    downloadEventImages: vi.fn(),
    registerView: vi.fn(),
  };
  const imageService = {
    getEventImages: vi.fn(),
  };
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    user.set(null);
    sessionStorage.clear();
    eventService.downloadEventImages.mockReset();
    eventService.registerView.mockReset().mockReturnValue(of(undefined));
    imageService.getEventImages.mockReset();

    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideRouter([]),
        { provide: APP_URL, useValue: 'http://app.test' },
        {
          provide: UserService,
          useValue: {
            user: user.asReadonly(),
            loading: signal(false).asReadonly(),
            error: signal<string | null>(null).asReadonly(),
          },
        },
        { provide: EventService, useValue: eventService },
        { provide: ImageService, useValue: imageService },
      ],
    })
      .overrideComponent(Home, {
        remove: { imports: [QrCodeComponent] },
        add: { imports: [StubQrCode] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(Home);
  });

  it('shows a loading state for the gallery preview until images resolve', () => {
    user.set({ ...userTemplate, events: [event] });
    imageService.getEventImages.mockReturnValue(of([]));

    fixture.detectChanges();

    expect(imageService.getEventImages).toHaveBeenCalledWith('event-1');
  });

  it('shows an empty state and zero photos stat when the event has no images', () => {
    user.set({ ...userTemplate, events: [event] });
    imageService.getEventImages.mockReturnValue(of([]));

    fixture.detectChanges();

    expect(fixture.componentInstance.imagesLoading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No photos yet');
    expect(fixture.nativeElement.querySelectorAll('.dashboard-preview img').length).toBe(0);
    expect(fixture.componentInstance.stats().photos).toBe('0');
  });

  it('renders fewer tiles than slots when there are fewer real images than slots', () => {
    user.set({ ...userTemplate, events: [event] });
    imageService.getEventImages.mockReturnValue(of([image1]));

    fixture.detectChanges();

    const tiles = fixture.nativeElement.querySelectorAll(
      '.dashboard-preview img',
    ) as NodeListOf<HTMLImageElement>;
    expect(tiles.length).toBe(1);
    expect(tiles[0].src).toBe(image1.url);
    expect(fixture.nativeElement.textContent).not.toContain('+');
    expect(fixture.componentInstance.stats().photos).toBe('1');
  });

  it('shows the real remaining count overlay when there are more images than preview slots', () => {
    user.set({ ...userTemplate, events: [event] });
    imageService.getEventImages.mockReturnValue(of([image1, image2, image3, image4, image5]));

    fixture.detectChanges();

    const tiles = fixture.nativeElement.querySelectorAll(
      '.dashboard-preview img',
    ) as NodeListOf<HTMLImageElement>;
    expect(tiles.length).toBe(3);
    expect(fixture.nativeElement.textContent).toContain('+2');
    expect(fixture.componentInstance.stats().photos).toBe('5');
  });

  it('does not render stale images and treats failed fetches as empty', () => {
    user.set({ ...userTemplate, events: [event] });
    imageService.getEventImages.mockReturnValue(throwError(() => new Error('failed')));

    fixture.detectChanges();

    expect(fixture.componentInstance.imagesLoading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No photos yet');
    expect(fixture.componentInstance.stats().photos).toBe('0');
  });
});

describe('Home live guest and view stats', () => {
  const user = signal<UserDto | null>(null);
  const eventService = {
    downloadEventImages: vi.fn(),
    registerView: vi.fn(),
  };
  const imageService = {
    getEventImages: vi.fn(),
  };
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    user.set(null);
    sessionStorage.clear();
    eventService.downloadEventImages.mockReset();
    eventService.registerView.mockReset().mockReturnValue(of(undefined));
    imageService.getEventImages.mockReset().mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        { provide: APP_URL, useValue: 'http://app.test' },
        {
          provide: UserService,
          useValue: {
            user: user.asReadonly(),
            loading: signal(false).asReadonly(),
            error: signal<string | null>(null).asReadonly(),
          },
        },
        { provide: EventService, useValue: eventService },
        { provide: ImageService, useValue: imageService },
      ],
    })
      .overrideComponent(Home, {
        remove: { imports: [QrCodeComponent] },
        add: { imports: [StubQrCode] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(Home);
  });

  it('renders live guest and view counts from the loaded event', () => {
    user.set({
      id: 'user-1',
      createdAt: '2030-01-01T00:00:00',
      email: 'couple@example.test',
      emailVerified: true,
      onboardingComplete: true,
      events: [eventFixture({ guestCount: 4, viewCount: 12 })],
    });

    fixture.detectChanges();

    const content = fixture.nativeElement.textContent as string;
    expect(content).toContain('4');
    expect(content).toContain('12');
  });

  it('registers a view exactly once per event per browser session', () => {
    user.set({
      id: 'user-1',
      createdAt: '2030-01-01T00:00:00',
      email: 'couple@example.test',
      emailVerified: true,
      onboardingComplete: true,
      events: [eventFixture({ id: 'event-once' })],
    });

    fixture.detectChanges();
    fixture.detectChanges();
    fixture.detectChanges();

    expect(eventService.registerView).toHaveBeenCalledTimes(1);
    expect(eventService.registerView).toHaveBeenCalledWith('event-once');
  });

  it('does not register a view again after a simulated page refresh with a persisted session flag', () => {
    sessionStorage.setItem('brautcloud-event-viewed-event-refreshed', 'true');
    user.set({
      id: 'user-1',
      createdAt: '2030-01-01T00:00:00',
      email: 'couple@example.test',
      emailVerified: true,
      onboardingComplete: true,
      events: [eventFixture({ id: 'event-refreshed' })],
    });

    fixture.detectChanges();

    expect(eventService.registerView).not.toHaveBeenCalled();
  });
});

describe('Home download all photos button', () => {
  const activeUser: UserDto = {
    createdAt: '2026-01-01T00:00:00Z',
    email: 'couple@example.com',
    emailVerified: true,
    onboardingComplete: true,
    id: 'user-1',
    events: [event],
  };
  const user = signal<UserDto | null>(activeUser);
  const eventService = {
    downloadEventImages: vi.fn(),
    registerView: vi.fn(),
  };
  const imageService = {
    getEventImages: vi.fn(),
  };
  let fixture: ComponentFixture<Home>;
  let createObjectURLSpy: ReturnType<typeof vi.fn>;
  let revokeObjectURLSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    user.set(activeUser);
    sessionStorage.clear();
    eventService.downloadEventImages.mockReset();
    eventService.registerView.mockReset().mockReturnValue(of(undefined));
    imageService.getEventImages.mockReset();
    imageService.getEventImages.mockReturnValue(of([]));
    createObjectURLSpy = vi.fn().mockReturnValue('blob:mock-url');
    revokeObjectURLSpy = vi.fn();
    (URL as unknown as { createObjectURL: unknown }).createObjectURL = createObjectURLSpy;
    (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = revokeObjectURLSpy;

    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideRouter([]),
        { provide: APP_URL, useValue: 'http://app.test' },
        {
          provide: UserService,
          useValue: {
            user: user.asReadonly(),
            loading: signal(false).asReadonly(),
            error: signal<string | null>(null).asReadonly(),
          },
        },
        { provide: EventService, useValue: eventService },
        { provide: ImageService, useValue: imageService },
      ],
    })
      .overrideComponent(Home, {
        remove: { imports: [QrCodeComponent] },
        add: { imports: [StubQrCode] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(Home);
  });

  function downloadButton(): HTMLButtonElement {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    return buttons.find((button) => button.textContent?.toLowerCase().includes('download'))!;
  }

  it('disables the button and shows a preparing label while the download is in flight, then completes the download', () => {
    const response$ = new Subject<HttpResponse<Blob>>();
    eventService.downloadEventImages.mockReturnValue(response$);
    fixture.detectChanges();

    downloadButton().click();
    fixture.detectChanges();

    expect(downloadButton().disabled).toBe(true);
    expect(downloadButton().textContent).toContain('Preparing download');

    response$.next(new HttpResponse<Blob>({ body: new Blob(['x']), status: 200 }));
    response$.complete();
    fixture.detectChanges();

    expect(fixture.componentInstance.downloadingAllPhotos()).toBe(false);
    expect(downloadButton().disabled).toBe(false);
    expect(createObjectURLSpy).toHaveBeenCalled();
    expect(revokeObjectURLSpy).toHaveBeenCalled();
  });

  it('shows an inline message instead of downloading when the gallery is empty', () => {
    eventService.downloadEventImages.mockReturnValue(
      of(new HttpResponse<Blob>({ body: null, status: 204 })),
    );
    fixture.detectChanges();

    downloadButton().click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No photos to download yet.');
    expect(createObjectURLSpy).not.toHaveBeenCalled();
  });

  it('shows an inline error and re-enables the button when the download request fails', () => {
    eventService.downloadEventImages.mockReturnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    downloadButton().click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'We could not download your photos. Please try again.',
    );
    expect(downloadButton().disabled).toBe(false);
  });
});

const userTemplate: UserDto = {
  createdAt: '2024-01-01T00:00:00Z',
  email: 'test@example.com',
  emailVerified: true,
  onboardingComplete: true,
  id: 'user-1',
  events: [],
};

const event: EventDto = {
  id: 'event-1',
  date: null,
  eventName: 'Sophie & Marcus',
  firstNameCoupleOne: 'Sophie',
  firstNameCoupleTwo: 'Marcus',
  location: 'Eichenfürst',
  userId: 'user-1',
  viewCount: 0,
  guestCount: 0,
};

const image1: EventImageDto = { id: 'image-1', url: 'https://cdn.test/image-1.jpg' };
const image2: EventImageDto = { id: 'image-2', url: 'https://cdn.test/image-2.jpg' };
const image3: EventImageDto = { id: 'image-3', url: 'https://cdn.test/image-3.jpg' };
const image4: EventImageDto = { id: 'image-4', url: 'https://cdn.test/image-4.jpg' };
const image5: EventImageDto = { id: 'image-5', url: 'https://cdn.test/image-5.jpg' };
