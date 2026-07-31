import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PublicEventDto } from '../../core/models/event.dto';
import { EventService } from '../../services/event-service';
import { ImageService } from '../../services/image-service';
import { UserService } from '../../services/user-service';
import { EventGallery } from './event-gallery';

const eventFixture: PublicEventDto = {
  id: 'event-1',
  eventName: 'Müller wedding',
  firstNameCoupleOne: 'Sophie',
  firstNameCoupleTwo: 'Marcus',
  date: '2030-06-15T00:00:00',
  location: 'Eichenfürst',
  passwordProtected: false,
};

describe('EventGallery', () => {
  let fixture: ComponentFixture<EventGallery>;
  let eventService: {
    getPublicEvent: ReturnType<typeof vi.fn>;
    registerPublicView: ReturnType<typeof vi.fn>;
  };
  let imageService: {
    getPublicEventImages: ReturnType<typeof vi.fn>;
    getEventImages: ReturnType<typeof vi.fn>;
    uploadPublicImages: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    sessionStorage.clear();
    eventService = {
      getPublicEvent: vi.fn(() => of(eventFixture)),
      registerPublicView: vi.fn(() => of(undefined)),
    };
    imageService = {
      getPublicEventImages: vi.fn(() => of([])),
      getEventImages: vi.fn(() => of([])),
      uploadPublicImages: vi.fn(() => of([])),
    };

    TestBed.configureTestingModule({
      imports: [EventGallery],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ eventId: 'event-1' }) } },
        },
        { provide: EventService, useValue: eventService },
        { provide: ImageService, useValue: imageService },
        { provide: UserService, useValue: { user: signal(null) } },
      ],
    });

    fixture = TestBed.createComponent(EventGallery);
    fixture.detectChanges();
  });

  it('loads public event details and public images without owner gallery requests', () => {
    expect(eventService.getPublicEvent).toHaveBeenCalledWith('event-1');
    expect(eventService.registerPublicView).toHaveBeenCalledWith('event-1');
    expect(imageService.getPublicEventImages).toHaveBeenCalledWith('event-1', undefined);
    expect(imageService.getEventImages).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Sophie & Marcus');
    expect(fixture.nativeElement.textContent).toContain(
      'This gallery is ready for its first moments',
    );
  });

  it('renders guest upload controls for an unlocked gallery', () => {
    const input = fixture.nativeElement.querySelector('#guest-file-input') as HTMLInputElement;

    expect(input).toBeTruthy();
    expect(input.multiple).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Share your photographs');
    expect(fixture.nativeElement.querySelector('.upload-preview-grid')).toBeNull();
    expect(fixture.nativeElement.querySelector('.uploaded-photo__delete')).toBeNull();
  });

  it('submits selected guest photographs and refreshes public gallery', () => {
    imageService.uploadPublicImages.mockReturnValue(of([{ imageId: 'image-1', success: true }]));
    fixture.componentInstance.selectedFiles.set([
      {
        file: new File(['photo'], 'guest.jpg', { type: 'image/jpeg' }),
        preview: 'blob:guest-preview',
      },
    ]);

    fixture.componentInstance.uploadSelected();
    fixture.detectChanges();

    expect(imageService.uploadPublicImages).toHaveBeenCalledWith(
      'event-1',
      expect.any(Array),
      undefined,
    );
    expect(fixture.nativeElement.textContent).toContain('1 photograph is now in the gallery.');
    expect(fixture.componentInstance.selectedFiles()).toHaveLength(0);
    expect(imageService.getPublicEventImages).toHaveBeenCalledTimes(2);
  });

  it('keeps selected photographs and announces guest upload failure', () => {
    imageService.uploadPublicImages.mockReturnValue(
      of([{ imageId: 'image-1', success: false, error: 'S3 upload failed' }]),
    );
    fixture.componentInstance.selectedFiles.set([
      {
        file: new File(['photo'], 'guest.jpg', { type: 'image/jpeg' }),
        preview: 'blob:guest-preview',
      },
    ]);

    fixture.componentInstance.uploadSelected();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedFiles()).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'photograph could not be shared',
    );
  });

  it('does not expose guest upload controls before protected gallery verification', () => {
    eventService.getPublicEvent.mockReturnValue(of({ ...eventFixture, passwordProtected: true }));
    const protectedFixture = TestBed.createComponent(EventGallery);
    protectedFixture.detectChanges();

    expect(protectedFixture.nativeElement.querySelector('#guest-file-input')).toBeNull();
    expect(protectedFixture.nativeElement.textContent).toContain('This gallery is protected');
    protectedFixture.destroy();
  });

  it('keeps protected gallery prompt and shows incorrect-password feedback after a 401', () => {
    eventService.getPublicEvent.mockReturnValue(of({ ...eventFixture, passwordProtected: true }));
    imageService.getPublicEventImages.mockReturnValue(
      throwError(() => ({ status: 401, statusText: 'Unauthorized' })),
    );
    const protectedFixture = TestBed.createComponent(EventGallery);
    protectedFixture.detectChanges();

    protectedFixture.componentInstance.enteredPassword.set('wrong-password');
    protectedFixture.componentInstance.submitPassword(new Event('submit'));
    protectedFixture.detectChanges();

    expect(protectedFixture.componentInstance.verifiedPassword()).toBeNull();
    expect(protectedFixture.nativeElement.textContent).toContain(
      'Incorrect password. Please try again.',
    );
    expect(protectedFixture.nativeElement.textContent).toContain('This gallery is protected');
    protectedFixture.destroy();
  });

  it('renders protected gallery after correct password verification', () => {
    eventService.getPublicEvent.mockReturnValue(of({ ...eventFixture, passwordProtected: true }));
    imageService.getPublicEventImages.mockReturnValue(of([]));
    const protectedFixture = TestBed.createComponent(EventGallery);
    protectedFixture.detectChanges();

    protectedFixture.componentInstance.enteredPassword.set('gallery-secret');
    protectedFixture.componentInstance.submitPassword(new Event('submit'));
    protectedFixture.detectChanges();

    expect(protectedFixture.componentInstance.verifiedPassword()).toBe('gallery-secret');
    expect(protectedFixture.nativeElement.textContent).toContain('Share your photographs');
    expect(protectedFixture.nativeElement.textContent).not.toContain('This gallery is protected');
    protectedFixture.destroy();
  });

  it('passes verified gallery password into guest upload submission', () => {
    eventService.getPublicEvent.mockReturnValue(of({ ...eventFixture, passwordProtected: true }));
    const protectedFixture = TestBed.createComponent(EventGallery);
    protectedFixture.detectChanges();
    protectedFixture.componentInstance.enteredPassword.set('guest-secret');
    imageService.getPublicEventImages.mockReturnValue(of([]));
    protectedFixture.componentInstance.submitPassword(new Event('submit'));
    protectedFixture.detectChanges();
    protectedFixture.componentInstance.selectedFiles.set([
      {
        file: new File(['photo'], 'guest.jpg', { type: 'image/jpeg' }),
        preview: 'blob:guest-preview',
      },
    ]);
    imageService.uploadPublicImages.mockReturnValue(of([{ imageId: 'image-1', success: true }]));

    protectedFixture.componentInstance.uploadSelected();

    expect(imageService.uploadPublicImages).toHaveBeenCalledWith(
      'event-1',
      expect.any(Array),
      'guest-secret',
    );
    protectedFixture.destroy();
  });

  it('shows recovery state when event identifier is missing', () => {
    const route = TestBed.inject(ActivatedRoute) as {
      snapshot: { paramMap: ReturnType<typeof convertToParamMap> };
    };
    route.snapshot.paramMap = convertToParamMap({});
    eventService.getPublicEvent.mockClear();
    fixture = TestBed.createComponent(EventGallery);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'This event link is missing its event identifier.',
    );
    expect(eventService.getPublicEvent).not.toHaveBeenCalled();
  });

  it('shows recovery state when public event cannot be found', () => {
    eventService.getPublicEvent.mockReturnValue(throwError(() => ({ status: 404 })));
    eventService.registerPublicView.mockClear();
    fixture = TestBed.createComponent(EventGallery);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Event link unavailable');
    expect(fixture.nativeElement.textContent).toContain('This event link is no longer available.');
    expect(eventService.registerPublicView).not.toHaveBeenCalled();
  });

  it('does not register a second view during one browser session', () => {
    sessionStorage.setItem('brautcloud-event-viewed-event-1', 'true');
    eventService.registerPublicView.mockClear();
    fixture = TestBed.createComponent(EventGallery);
    fixture.detectChanges();

    expect(eventService.registerPublicView).not.toHaveBeenCalled();
  });
});
