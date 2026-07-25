import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ImageService } from '../../../services/image-service';
import { UserService } from '../../../services/user-service';
import { ImageUpload } from './image-upload';

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
    await TestBed.configureTestingModule({
      imports: [ImageUpload],
      providers: [
        provideRouter([]),
        { provide: UserService, useValue: { user } },
        { provide: ImageService, useValue: {} },
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
});
