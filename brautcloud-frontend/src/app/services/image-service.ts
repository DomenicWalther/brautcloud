import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap, throwError } from 'rxjs';
import { API_URL } from '../core/tokens';
import { EventImageDto } from '../core/models/event-image.dto';

interface PresignedUrlRequest {
  eventId: string;
  fileNames: string[];
  contentTypes: string[];
  fileSizes: number[];
}

interface PublicPresignedUrlRequest {
  fileNames: string[];
  contentTypes: string[];
  fileSizes: number[];
}

export interface SelectedFile {
  file: File;
  preview: string;
}

interface PresignedUrlResponse {
  imageId: string;
  uploadUrl: string;
}

export interface UploadResult {
  imageId: string;
  success: boolean;
  error?: string;
}

export const MAX_FILES_PER_REQUEST = 100;
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

@Injectable({
  providedIn: 'root',
})
export class ImageService {
  private http: HttpClient = inject(HttpClient);
  private readonly API_URL = inject(API_URL);

  getEventImages(eventId: string): Observable<EventImageDto[]> {
    return this.http.get<EventImageDto[]>(`${this.API_URL}/events/${eventId}/images`, {
      withCredentials: true,
    });
  }

  getPublicEventImages(eventId: string, password?: string): Observable<EventImageDto[]> {
    const headers: { [name: string]: string } = {};
    if (password) {
      headers['X-Gallery-Password'] = password;
    }
    return this.http.get<EventImageDto[]>(`${this.API_URL}/events/${eventId}/public/images`, {
      headers,
      withCredentials: true,
    });
  }

  uploadImages(eventId: string, files: SelectedFile[]): Observable<UploadResult[]> {
    const validationError = this.validateFiles(files);
    if (validationError) {
      return throwError(() => new Error(validationError));
    }
    const request = this.createUploadRequest(eventId, files);

    return this.uploadImagesWithEndpoints(
      `${this.API_URL}/image/presigned-url`,
      `${this.API_URL}/image/uploaded`,
      request,
      files,
      true,
    );
  }

  uploadPublicImages(
    eventId: string,
    files: SelectedFile[],
    galleryPassword?: string,
  ): Observable<UploadResult[]> {
    const validationError = this.validateFiles(files);
    if (validationError) {
      return throwError(() => new Error(validationError));
    }
    const headers: { [name: string]: string } = {};
    if (galleryPassword) {
      headers['X-Gallery-Password'] = galleryPassword;
    }

    return this.uploadImagesWithEndpoints(
      `${this.API_URL}/events/${eventId}/public/images/presigned-url`,
      `${this.API_URL}/events/${eventId}/public/images/uploaded`,
      this.createUploadRequestWithoutEvent(files),
      files,
      true,
      headers,
    );
  }

  private uploadImagesWithEndpoints(
    presignedUrlEndpoint: string,
    uploadedEndpoint: string,
    request: PresignedUrlRequest | PublicPresignedUrlRequest,
    files: SelectedFile[],
    withCredentials: boolean,
    headers: { [name: string]: string } = {},
  ): Observable<UploadResult[]> {
    return this.http
      .post<PresignedUrlResponse[]>(presignedUrlEndpoint, request, {
        headers,
        withCredentials,
      })
      .pipe(
        switchMap((presignedUrls) => {
          if (
            presignedUrls.length > MAX_FILES_PER_REQUEST ||
            presignedUrls.length !== files.length
          ) {
            return throwError(() => new Error('Upload batch exceeds the allowed file count'));
          }
          const uploads = presignedUrls.map((presigned, index) => {
            const selectedFile = files[index];
            if (!selectedFile) {
              return of({ imageId: presigned.imageId, success: false, error: 'File not found' });
            }
            return this.uploadToS3(presigned.uploadUrl, selectedFile.file).pipe(
              map(() => ({ imageId: presigned.imageId, success: true })),
              catchError((error: unknown) => {
                if (error instanceof HttpErrorResponse && error.status === 401) {
                  return throwError(() => error);
                }
                const message = error instanceof Error ? error.message : 'Upload failed';
                return of({ imageId: presigned.imageId, success: false, error: message });
              }),
            );
          });

          return forkJoin(uploads).pipe(
            switchMap((results) => {
              const uploadedIds = results
                .filter((result) => result.success)
                .map((result) => result.imageId);
              if (uploadedIds.length === 0) {
                return of(results);
              }
              return this.notifyBackend(
                uploadedEndpoint,
                uploadedIds,
                headers,
                withCredentials,
              ).pipe(
                map(() => results),
                catchError((error: unknown) => {
                  if (error instanceof HttpErrorResponse && error.status === 401) {
                    return throwError(() => error);
                  }
                  const message =
                    error instanceof Error ? error.message : 'Upload confirmation failed';
                  return of(
                    results.map((result) =>
                      uploadedIds.includes(result.imageId)
                        ? { ...result, success: false, error: message }
                        : result,
                    ),
                  );
                }),
              );
            }),
          );
        }),
      );
  }

  private uploadToS3(uploadUrl: string, file: File): Observable<void> {
    return new Observable((observer) => {
      fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: {
          'Content-Type': file.type,
        },
      })
        .then((response) => {
          if (!response.ok) {
            throw new Error(`S3 upload failed: ${response.status}`);
          }
          observer.next();
          observer.complete();
        })
        .catch((error) => {
          observer.error(error);
        });
    });
  }

  private notifyBackend(
    uploadedEndpoint: string,
    imageIds: string[],
    headers: { [name: string]: string },
    withCredentials: boolean,
  ): Observable<void> {
    return this.http.post<void>(uploadedEndpoint, imageIds, { headers, withCredentials });
  }

  private createUploadRequest(eventId: string, files: SelectedFile[]): PresignedUrlRequest {
    return { eventId, ...this.createUploadMetadata(files) };
  }

  private createUploadRequestWithoutEvent(files: SelectedFile[]): PublicPresignedUrlRequest {
    return this.createUploadMetadata(files);
  }

  private createUploadMetadata(files: SelectedFile[]): Omit<PresignedUrlRequest, 'eventId'> {
    return {
      fileNames: files.map(({ file }) => file.name),
      contentTypes: files.map(({ file }) => file.type),
      fileSizes: files.map(({ file }) => file.size),
    };
  }

  private validateFiles(files: SelectedFile[]): string | null {
    if (files.length === 0) {
      return 'Select at least one image.';
    }
    if (files.length > MAX_FILES_PER_REQUEST) {
      return `You can upload at most ${MAX_FILES_PER_REQUEST} images at once.`;
    }
    if (files.some(({ file }) => file.size <= 0 || file.size > MAX_IMAGE_BYTES)) {
      return 'Each image must be 10 MB or smaller.';
    }
    if (files.some(({ file }) => file.name.length > 255)) {
      return 'File names must be 255 characters or shorter.';
    }
    return null;
  }

  deleteImage(imageId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/image/${imageId}`, { withCredentials: true });
  }

  deletePublicImage(eventId: string, imageId: string, galleryPassword?: string): Observable<void> {
    const headers: { [name: string]: string } = {};
    if (galleryPassword) {
      headers['X-Gallery-Password'] = galleryPassword;
    }
    return this.http.delete<void>(`${this.API_URL}/events/${eventId}/public/images/${imageId}`, {
      headers,
      withCredentials: true,
    });
  }
}
