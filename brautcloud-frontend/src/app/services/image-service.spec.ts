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
    expect(presign.request.body).toEqual({ fileNames: ['guest.jpg'] });
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
