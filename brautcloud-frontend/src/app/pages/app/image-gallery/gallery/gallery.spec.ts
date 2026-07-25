import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { EventImageDto } from '../../../../core/models/event-image.dto';
import { EventDto } from '../../../../core/models/event.dto';
import { ImageService } from '../../../../services/image-service';
import { UserService } from '../../../../services/user-service';
import { Gallery } from './gallery';

describe('Gallery empty states', () => {
  const user = signal<{ events: EventDto[] } | null>(null);
  const imageService = {
    getEventImages: vi.fn(),
  };
  let fixture: ComponentFixture<Gallery>;

  beforeEach(async () => {
    user.set(null);
    imageService.getEventImages.mockReset();

    await TestBed.configureTestingModule({
      imports: [Gallery],
      providers: [
        provideRouter([]),
        {
          provide: UserService,
          useValue: { user: user.asReadonly() },
        },
        { provide: ImageService, useValue: imageService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Gallery);
  });

  it('stops loading and offers home navigation when no event exists', () => {
    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No gallery is available yet');
    expect(fixture.nativeElement.querySelector('a[routerlink="/app/home"]')).toBeTruthy();
    expect(imageService.getEventImages).not.toHaveBeenCalled();
  });

  it('renders upload navigation when event has no images', () => {
    user.set({ events: [event] });
    imageService.getEventImages.mockReturnValue(of([]));

    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Your gallery is ready for photos');
    expect(fixture.nativeElement.querySelector('a[routerlink="/app/upload"]')).toBeTruthy();
  });

  it('stops loading and shows recovery navigation when image loading fails', () => {
    user.set({ events: [event] });
    imageService.getEventImages.mockReturnValue(throwError(() => new Error('request failed')));

    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Gallery unavailable');
    expect(fixture.nativeElement.textContent).toContain('Back to home');
  });

  it('keeps rendering images for populated galleries', () => {
    user.set({ events: [event] });
    imageService.getEventImages.mockReturnValue(of([image]));

    fixture.detectChanges();

    const renderedImage = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(renderedImage.src).toBe(image.url);
  });
});

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

const image: EventImageDto = {
  id: 'image-1',
  url: 'https://cdn.test/image-1.jpg',
};
