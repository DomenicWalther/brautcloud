import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { EventImageDto } from '../../../../core/models/event-image.dto';
import { EventDto } from '../../../../core/models/event.dto';
import { ImageService } from '../../../../services/image-service';
import { ToastService } from '../../../../services/toast-service';
import { UserService } from '../../../../services/user-service';
import { Gallery } from './gallery';

describe('Gallery states', () => {
  const user = signal<{ events: EventDto[] } | null>(null);
  const imageService = {
    getEventImages: vi.fn<(eventId: string) => Observable<EventImageDto[]>>(),
    deleteImage: vi.fn<(imageId: string) => Observable<void>>(),
  };
  let fixture: ComponentFixture<Gallery>;

  beforeEach(async () => {
    user.set(null);
    imageService.getEventImages.mockReset();
    document.body.style.overflow = '';

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

  afterEach(() => {
    fixture.destroy();
    document.body.style.overflow = '';
  });

  it('announces loading while moments are being gathered', () => {
    imageService.getEventImages.mockReturnValue(new Subject<EventImageDto[]>());
    user.set({ events: [event] });

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')?.textContent).toContain(
      'Preparing your gallery',
    );
  });

  it('stops loading and offers home navigation when no event exists', () => {
    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No gallery is available yet');
    expect(fixture.nativeElement.querySelector('a[routerlink="/app/home"]')).toBeTruthy();
    expect(imageService.getEventImages).not.toHaveBeenCalled();
  });

  it('shows a useful empty state and upload navigation when event has no images', () => {
    user.set({ events: [event] });
    imageService.getEventImages.mockReturnValue(of([]));

    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Your first moments will appear here');
    expect(fixture.nativeElement.querySelector('a[routerlink="/app/upload"]')).toBeTruthy();
  });

  it('stops loading and shows recovery navigation when image loading fails', () => {
    user.set({ events: [event] });
    imageService.getEventImages.mockReturnValue(throwError(() => new Error('request failed')));

    fixture.detectChanges();

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Gallery unavailable',
    );
    expect(fixture.nativeElement.textContent).toContain('Back to home');
  });

  it('renders accessible populated gallery imagery', () => {
    user.set({ events: [event] });
    imageService.getEventImages.mockReturnValue(of([image]));

    fixture.detectChanges();

    const renderedImage = fixture.nativeElement.querySelector(
      '.gallery-photo img',
    ) as HTMLImageElement;
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(renderedImage.getAttribute('src')).toBe(image.url);
    expect(renderedImage.getAttribute('alt')).toBe('Wedding gallery moment 1');
  });
});

describe('Gallery lightbox', () => {
  const user = signal<{ events: EventDto[] } | null>(null);
  const imageService = {
    getEventImages: vi.fn<(eventId: string) => Observable<EventImageDto[]>>(),
    deleteImage: vi.fn<(imageId: string) => Observable<void>>(),
  };
  let fixture: ComponentFixture<Gallery>;

  beforeEach(async () => {
    user.set({ events: [event] });
    imageService.getEventImages.mockReset().mockReturnValue(of(images));
    imageService.deleteImage.mockReset();
    document.body.style.overflow = '';

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
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.inject(ToastService).clear();
    document.body.style.overflow = '';
  });

  it('opens selected image and closes without leaving page scrolling locked', async () => {
    const galleryButton = fixture.nativeElement.querySelector(
      '.gallery-photo__button',
    ) as HTMLButtonElement;
    galleryButton.focus();
    galleryButton.click();
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('.gallery-lightbox__close'),
    );
    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__image')?.getAttribute('src'),
    ).toBe(images[0].url);
    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__position')?.textContent,
    ).toContain('Photo 1 of 3');
    expect(document.body.style.overflow).toBe('hidden');

    (fixture.nativeElement.querySelector('.gallery-lightbox__close') as HTMLButtonElement).click();
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(galleryButton);
    expect(document.body.style.overflow).toBe('');
  });

  it('keeps selected image decoded across close and immediate reopen', () => {
    const galleryButton = fixture.nativeElement.querySelector(
      '.gallery-photo__button',
    ) as HTMLButtonElement;
    galleryButton.click();
    fixture.detectChanges();
    const loadedImage = fixture.nativeElement.querySelector('.gallery-lightbox__image');

    (fixture.nativeElement.querySelector('.gallery-lightbox__close') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.gallery-lightbox__image')).toBe(loadedImage);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();

    galleryButton.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.gallery-lightbox__image')).toBe(loadedImage);
  });

  it('opens delete confirmation without deleting before explicit confirmation', () => {
    const deleteButton = fixture.nativeElement.querySelector(
      '.gallery-photo__delete',
    ) as HTMLButtonElement;
    deleteButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.gallery-delete-dialog')?.textContent).toContain(
      'cannot be undone',
    );
    expect(imageService.deleteImage).not.toHaveBeenCalled();
  });

  it('cancels delete with Escape and restores focus', async () => {
    const deleteButton = fixture.nativeElement.querySelector(
      '.gallery-photo__delete',
    ) as HTMLButtonElement;
    deleteButton.focus();
    deleteButton.click();
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(fixture.nativeElement.querySelector('.gallery-delete-dialog')).toBeNull();
    expect(imageService.deleteImage).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(deleteButton);
  });

  it('keeps image pending and shows success toast only after server response', () => {
    const deletion = new Subject<void>();
    imageService.deleteImage.mockReturnValue(deletion);
    const toastService = TestBed.inject(ToastService);
    const deleteButton = fixture.nativeElement.querySelector(
      '.gallery-photo__delete',
    ) as HTMLButtonElement;
    deleteButton.click();
    fixture.detectChanges();
    (
      fixture.nativeElement.querySelector(
        '.gallery-delete-dialog .bc-button--danger',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(imageService.deleteImage).toHaveBeenCalledWith(images[0].id);
    expect(fixture.componentInstance.images()).toEqual(images);
    expect(fixture.componentInstance.isImagePending(images[0].id)).toBe(true);
    expect(toastService.toasts()).toEqual([]);
    deletion.next();
    deletion.complete();
    expect(fixture.componentInstance.images()).toEqual(images.slice(1));
    expect(toastService.toasts()[0]?.message).toContain('deleted successfully');
  });

  it('keeps failed image visible and shows error toast', () => {
    imageService.deleteImage.mockReturnValue(throwError(() => new Error('request failed')));
    const toastService = TestBed.inject(ToastService);
    const deleteButton = fixture.nativeElement.querySelector(
      '.gallery-photo__delete',
    ) as HTMLButtonElement;
    deleteButton.click();
    fixture.detectChanges();
    (
      fixture.nativeElement.querySelector(
        '.gallery-delete-dialog .bc-button--danger',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(fixture.componentInstance.images()).toEqual(images);
    expect(toastService.toasts()[0]?.kind).toBe('error');
  });

  it('confirms selected batch and removes images only after server confirmation', () => {
    const batchDeletion = new Subject<void>();
    imageService.deleteImage.mockReturnValue(batchDeletion);
    const selectAll = fixture.nativeElement.querySelector(
      '.gallery-selection-bar button',
    ) as HTMLButtonElement;
    selectAll.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedImageCount()).toBe(3);
    (
      fixture.nativeElement.querySelector(
        '.gallery-selection-bar .bc-button--danger',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.gallery-delete-dialog')).toBeTruthy();
    expect(imageService.deleteImage).not.toHaveBeenCalled();

    (
      fixture.nativeElement.querySelector(
        '.gallery-delete-dialog .bc-button--danger',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    expect(imageService.deleteImage).toHaveBeenCalledTimes(3);
    expect(fixture.componentInstance.images()).toEqual(images);
    expect(fixture.componentInstance.pendingDeleteIds().size).toBe(3);
    batchDeletion.next();
    batchDeletion.complete();
    expect(fixture.componentInstance.images()).toEqual([]);
  });

  it('keeps failed images visible after partial batch deletion', () => {
    imageService.deleteImage.mockImplementation((imageId) =>
      imageId === images[1].id ? throwError(() => new Error('request failed')) : of(undefined),
    );
    (
      fixture.nativeElement.querySelector('.gallery-selection-bar button') as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    (
      fixture.nativeElement.querySelector(
        '.gallery-selection-bar .bc-button--danger',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    (
      fixture.nativeElement.querySelector(
        '.gallery-delete-dialog .bc-button--danger',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(fixture.componentInstance.images()).toEqual([images[1]]);
    expect(TestBed.inject(ToastService).toasts()[0]?.message).toContain('1 of 3');
  });

  it('navigates with arrows, wraps at boundaries, and selects filmstrip thumbnails', () => {
    (
      fixture.nativeElement.querySelectorAll('.gallery-photo__button')[2] as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      '.gallery-lightbox__nav--next',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__image')?.getAttribute('src'),
    ).toBe(images[0].url);
    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__thumbnail[aria-current="true"]'),
    ).toBe(fixture.nativeElement.querySelectorAll('.gallery-lightbox__thumbnail')[0]);

    (
      fixture.nativeElement.querySelectorAll('.gallery-lightbox__thumbnail')[1] as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__image')?.getAttribute('src'),
    ).toBe(images[1].url);
    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__position')?.textContent,
    ).toContain('Photo 2 of 3');
  });

  it('uses Escape and arrow keys while lightbox is open', () => {
    (
      fixture.nativeElement.querySelectorAll('.gallery-photo__button')[0] as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__image')?.getAttribute('src'),
    ).toBe(images[1].url);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('.gallery-lightbox__image')?.getAttribute('src'),
    ).toBe(images[0].url);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('renders selected image failure state without closing lightbox', () => {
    (fixture.nativeElement.querySelector('.gallery-photo__button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const image = fixture.nativeElement.querySelector(
      '.gallery-lightbox__image',
    ) as HTMLImageElement;
    image.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.gallery-lightbox__error')?.textContent).toContain(
      'could not be displayed',
    );
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
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
  hasPassword: false,
};

const image: EventImageDto = {
  id: 'image-1',
  url: 'https://cdn.test/image-1.jpg',
};

const images: EventImageDto[] = [
  image,
  { id: 'image-2', url: 'https://cdn.test/image-2.jpg' },
  { id: 'image-3', url: 'https://cdn.test/image-3.jpg' },
];
