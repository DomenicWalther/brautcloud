import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ImageService } from '../../../services/image-service';
import { UserService } from '../../../services/user-service';
import { ToastService } from '../../../services/toast-service';
import { ImageUpload } from './image-upload';

const imageService = {
  uploadImages: vi.fn(),
  deleteImage: vi.fn(),
};

const user = signal({
  id: 'user-1',
  email: 'couple@example.com',
  emailVerified: true,
  createdAt: '2026-01-01',
  lastName: 'Example',
  events: [
    {
      id: 'event-1',
      date: '2026-09-21',
      eventName: 'Our wedding',
      firstNameCoupleOne: 'Amelia',
      firstNameCoupleTwo: 'James',
      location: 'Lakeside',
      userID: 'user-1',
    },
  ],
});

describe('ImageUpload', () => {
  beforeEach(async () => {
    imageService.uploadImages.mockReset();
    imageService.deleteImage.mockReset();
    await TestBed.configureTestingModule({
      imports: [ImageUpload],
      providers: [
        provideRouter([]),
        { provide: UserService, useValue: { user } },
        { provide: ImageService, useValue: imageService },
      ],
    }).compileComponents();
  });

  it('starts with an accessible picker and a clear empty uploads state', () => {
    const fixture = TestBed.createComponent(ImageUpload);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const input = root.querySelector<HTMLInputElement>('#file-input');
    const label = root.querySelector<HTMLLabelElement>('label[for="file-input"]');

    expect(input?.multiple).toBe(true);
    expect(input?.getAttribute('accept')).toBe('image/*');
    expect(label?.textContent).toContain('Choose photographs');
    expect(root.textContent).toContain('No photographs shared yet');
    expect(root.querySelector('a[href="/app/gallery"]')).toBeTruthy();
  });

  it('announces upload failures without removing the upload controls', () => {
    const fixture = TestBed.createComponent(ImageUpload);
    fixture.componentInstance.uploadError.set('The upload could not be completed.');
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      'The upload could not be completed.',
    );
    expect(root.querySelector('label[for="file-input"]')).toBeTruthy();
  });

  it('shows completion feedback after uploading photographs', () => {
    imageService.uploadImages.mockReturnValue(of([{ success: true, imageId: 'image-1' }]));
    const fixture = TestBed.createComponent(ImageUpload);
    const toastService = TestBed.inject(ToastService);
    toastService.clear();
    fixture.componentInstance.selectedFiles.set([
      {
        file: new File(['photo'], 'moment.jpg', { type: 'image/jpeg' }),
        preview: 'blob:preview',
      },
    ]);
    (URL as unknown as { createObjectURL: unknown }).createObjectURL = vi
      .fn()
      .mockReturnValue('blob:uploaded');

    fixture.componentInstance.uploadSelected();

    expect(toastService.toasts()[0]).toMatchObject({
      kind: 'success',
      message: '1 photo uploaded successfully.',
    });
  });

  it('cancels in-page deletion without calling the server or changing the image list', () => {
    const fixture = TestBed.createComponent(ImageUpload);
    const image = { id: 'image-1', url: 'blob:image' };
    fixture.componentInstance.uploadedImages.set([image]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.uploaded-photo__delete') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    expect(fixture.componentInstance.pendingDeletionId()).toBe(image.id);
    expect(imageService.deleteImage).not.toHaveBeenCalled();

    (
      fixture.nativeElement.querySelector(
        '.upload-delete-dialog .bc-button--secondary',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.componentInstance.pendingDeletionId()).toBeNull();
    expect(fixture.componentInstance.uploadedImages()).toEqual([image]);
  });

  it('deletes an image and shows success only after the server confirms', () => {
    imageService.deleteImage.mockReturnValue(of(undefined));
    const fixture = TestBed.createComponent(ImageUpload);
    const toastService = TestBed.inject(ToastService);
    toastService.clear();
    fixture.componentInstance.uploadedImages.set([{ id: 'image-1', url: 'blob:image' }]);

    fixture.componentInstance.deleteUploadedImage(0);
    expect(fixture.componentInstance.uploadedImages()).toHaveLength(1);

    fixture.componentInstance.confirmDeleteUploadedImage();

    expect(imageService.deleteImage).toHaveBeenCalledTimes(1);
    expect(imageService.deleteImage).toHaveBeenCalledWith('image-1');
    expect(fixture.componentInstance.uploadedImages()).toEqual([]);
    expect(fixture.componentInstance.deleteSuccess()).toBe('Photo deleted successfully.');
    expect(toastService.toasts()[0]).toMatchObject({
      kind: 'success',
      message: 'Photo deleted successfully.',
    });
  });

  it('prevents duplicate deletion requests while one deletion is pending', () => {
    const deletion = new Subject<void>();
    imageService.deleteImage.mockReturnValue(deletion.asObservable());
    const fixture = TestBed.createComponent(ImageUpload);
    fixture.componentInstance.uploadedImages.set([
      { id: 'image-1', url: 'blob:image-1' },
      { id: 'image-2', url: 'blob:image-2' },
    ]);

    fixture.componentInstance.deleteUploadedImage(0);
    fixture.componentInstance.confirmDeleteUploadedImage();
    fixture.componentInstance.confirmDeleteUploadedImage();
    fixture.componentInstance.deleteUploadedImage(1);

    expect(imageService.deleteImage).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.deletingImageId()).toBe('image-1');
    expect(fixture.componentInstance.uploadedImages()).toHaveLength(2);
  });

  it('keeps the image and pending confirmation when the server rejects deletion', () => {
    imageService.deleteImage.mockReturnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(ImageUpload);
    const toastService = TestBed.inject(ToastService);
    toastService.clear();
    const image = { id: 'image-1', url: 'blob:image' };
    fixture.componentInstance.uploadedImages.set([image]);

    fixture.componentInstance.deleteUploadedImage(0);
    fixture.componentInstance.confirmDeleteUploadedImage();

    expect(fixture.componentInstance.uploadedImages()).toEqual([image]);
    expect(fixture.componentInstance.pendingDeletionId()).toBeNull();
    expect(fixture.componentInstance.deletingImageId()).toBeNull();
    expect(fixture.componentInstance.deleteError()).toBe(
      'That photo could not be removed. Please try again.',
    );
    expect(toastService.toasts()[0]).toMatchObject({
      kind: 'error',
      message: 'That photo could not be removed. Please try again.',
    });
  });
});
