import { HttpResponse } from '@angular/common/http';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QrCodeComponent } from 'ng-qrcode';
import { of, Subject, throwError } from 'rxjs';
import { APP_URL } from '../../../core/tokens';
import { UserDto } from '../../../core/models/user.dto';
import { EventService } from '../../../services/event-service';
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

describe('Home defensive empty state', () => {
  const user = signal<UserDto | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const eventService = {
    downloadEventImages: vi.fn(),
  };
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    user.set(null);
    loading.set(false);
    error.set(null);
    eventService.downloadEventImages.mockReset();

    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
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
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Home);
  });

  it('does not render broken names, countdowns, stats, or QR data without an event', () => {
    fixture.detectChanges();
    const content = fixture.nativeElement.textContent as string;

    expect(content).toContain('No gallery is available yet');
    expect(content).not.toContain('Welcome,');
    expect(content).not.toContain('Days until your Wedding');
    expect(content).not.toContain('Live Gallery');
    expect(fixture.nativeElement.querySelector('qr-code')).toBeNull();
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

describe('Home download all photos button', () => {
  const activeUser: UserDto = {
    createdAt: '2026-01-01T00:00:00Z',
    email: 'couple@example.com',
    emailVerified: true,
    onboardingComplete: true,
    id: 'user-1',
    events: [
      {
        id: 'event-1',
        eventName: 'Alex & Sam Wedding',
        firstNameCoupleOne: 'Alex',
        firstNameCoupleTwo: 'Sam',
        location: 'Berlin',
        date: null,
        userId: 'user-1',
      },
    ],
  };
  const user = signal<UserDto | null>(activeUser);
  const eventService = {
    downloadEventImages: vi.fn(),
  };
  let fixture: ComponentFixture<Home>;
  let createObjectURLSpy: ReturnType<typeof vi.fn>;
  let revokeObjectURLSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    user.set(activeUser);
    eventService.downloadEventImages.mockReset();
    createObjectURLSpy = vi.fn().mockReturnValue('blob:mock-url');
    revokeObjectURLSpy = vi.fn();
    (URL as unknown as { createObjectURL: unknown }).createObjectURL = createObjectURLSpy;
    (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = revokeObjectURLSpy;

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
    return buttons.find((button) => button.textContent?.includes('DOWNLOAD'))!;
  }

  it('disables the button and shows a preparing label while the download is in flight, then completes the download', () => {
    const response$ = new Subject<HttpResponse<Blob>>();
    eventService.downloadEventImages.mockReturnValue(response$);
    fixture.detectChanges();

    downloadButton().click();
    fixture.detectChanges();

    expect(downloadButton().disabled).toBe(true);
    expect(downloadButton().textContent).toContain('PREPARING DOWNLOAD');

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
