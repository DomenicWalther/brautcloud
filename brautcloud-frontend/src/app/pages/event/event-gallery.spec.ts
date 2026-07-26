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
  };

  beforeEach(() => {
    sessionStorage.clear();
    eventService = {
      getPublicEvent: vi.fn(() => of(eventFixture)),
      registerPublicView: vi.fn(() => of(undefined)),
    };
    imageService = {
      getPublicEventImages: vi.fn(() => of([])),
      getEventImages: vi.fn(),
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
    expect(imageService.getPublicEventImages).toHaveBeenCalledWith('event-1');
    expect(imageService.getEventImages).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Sophie & Marcus');
    expect(fixture.nativeElement.textContent).toContain(
      'This gallery is ready for its first moments',
    );
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
