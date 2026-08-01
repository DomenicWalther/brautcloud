import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_URL } from '../core/tokens';
import { ImageService, SelectedFile } from './image-service';

describe('ImageService public uploads', () => {
  let imageService: ImageService;
  let http: HttpTestingController;
  let fetchMock: ReturnType<typeof vi.fn>;

  const files: SelectedFile[] = [
    {
      file: new File(['photo'], 'guest.jpg', { type: 'image/jpeg' }),
      preview: 'blob:guest',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api.test/api' },
      ],
    });
    imageService = TestBed.inject(ImageService);
    http = TestBed.inject(HttpTestingController);
    fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue({ ok: true, status: 200 } as Response);
  });

  afterEach(() => {
    http.verify();
    fetchMock.mockRestore();
  });

  it('uses public event routes and passes verified gallery password to both requests', async () => {
    let result: unknown;
    imageService
      .uploadPublicImages('event-1', files, 'guest-secret')
      .subscribe((value) => (result = value));

    const presign = http.expectOne(
      'http://api.test/api/events/event-1/public/images/presigned-url',
    );
    expect(presign.request.method).toBe('POST');
    expect(presign.request.body).toEqual({
      fileNames: ['guest.jpg'],
      contentTypes: ['image/jpeg'],
      fileSizes: [5],
    });
    expect(presign.request.headers.get('X-Gallery-Password')).toBe('guest-secret');
    expect(presign.request.withCredentials).toBe(true);
    presign.flush([{ imageId: 'image-1', uploadUrl: 'https://s3.test/guest' }]);

    await Promise.resolve();
    await Promise.resolve();
    const confirmation = http.expectOne(
      'http://api.test/api/events/event-1/public/images/uploaded',
    );
    expect(confirmation.request.headers.get('X-Gallery-Password')).toBe('guest-secret');
    expect(confirmation.request.body).toEqual(['image-1']);
    confirmation.flush(null, { status: 204, statusText: 'No Content' });

    expect(result).toEqual([{ imageId: 'image-1', success: true }]);
  });

  it('confirms successful uploads in one bounded batch', async () => {
    const secondFile: SelectedFile = {
      file: new File(['second'], 'second.jpg', { type: 'image/jpeg' }),
      preview: 'blob:second',
    };
    let result: unknown;
    imageService
      .uploadPublicImages('event-1', [...files, secondFile])
      .subscribe((value) => (result = value));

    http.expectOne('http://api.test/api/events/event-1/public/images/presigned-url').flush([
      { imageId: 'image-1', uploadUrl: 'https://s3.test/one' },
      { imageId: 'image-2', uploadUrl: 'https://s3.test/two' },
    ]);
    await Promise.resolve();
    await Promise.resolve();

    const confirmation = http.expectOne(
      'http://api.test/api/events/event-1/public/images/uploaded',
    );
    expect(confirmation.request.body).toEqual(['image-1', 'image-2']);
    confirmation.flush(null, { status: 204, statusText: 'No Content' });

    expect(result).toEqual([
      { imageId: 'image-1', success: true },
      { imageId: 'image-2', success: true },
    ]);
  });

  it('rejects oversized client batches before requesting presigned URLs', () => {
    const oversized = Array.from({ length: 101 }, (_, index) => ({
      file: new File(['photo'], `photo-${index}.jpg`, { type: 'image/jpeg' }),
      preview: `blob:${index}`,
    }));
    let error: Error | undefined;

    imageService
      .uploadPublicImages('event-1', oversized)
      .subscribe({ error: (value) => (error = value) });

    expect(error?.message).toContain('at most 100');
    http.expectNone('http://api.test/api/events/event-1/public/images/presigned-url');
  });

  it('rejects oversized files before requesting presigned URLs', () => {
    const oversized: SelectedFile = {
      file: new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.jpg', { type: 'image/jpeg' }),
      preview: 'blob:large',
    };
    let error: Error | undefined;

    imageService
      .uploadImages('event-1', [oversized])
      .subscribe({ error: (value) => (error = value) });

    expect(error?.message).toContain('10 MB or smaller');
    http.expectNone('http://api.test/api/image/presigned-url');
  });

  it('deletes public image with gallery scope, password, and guest credentials', () => {
    imageService.deletePublicImage('event-1', 'image-1', 'guest-secret').subscribe();

    const request = http.expectOne('http://api.test/api/events/event-1/public/images/image-1');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('X-Gallery-Password')).toBe('guest-secret');
    expect(request.request.withCredentials).toBe(true);
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('does not confirm failed S3 uploads as visible metadata', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 500 } as Response);
    let result: unknown;
    imageService.uploadPublicImages('event-1', files).subscribe((value) => (result = value));

    http
      .expectOne('http://api.test/api/events/event-1/public/images/presigned-url')
      .flush([{ imageId: 'image-1', uploadUrl: 'https://s3.test/guest' }]);
    await Promise.resolve();
    await Promise.resolve();

    http.expectNone('http://api.test/api/events/event-1/public/images/uploaded');
    expect(result).toEqual([
      { imageId: 'image-1', success: false, error: 'S3 upload failed: 500' },
    ]);
  });
});
