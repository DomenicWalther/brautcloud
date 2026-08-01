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
    const fileNames = files.map((f) => f.file.name);
    const contentTypes = files.map((f) => f.file.type);
    const fileSizes = files.map((f) => f.file.size);

    return this.uploadImagesWithEndpoints(
      `${this.API_URL}/image/presigned-url`,
      `${this.API_URL}/image/uploaded`,
      { eventId, fileNames, contentTypes, fileSizes },
      files,
      true,
    );
  }

  uploadPublicImages(
    eventId: string,
    files: SelectedFile[],
    galleryPassword?: string,
  ): Observable<UploadResult[]> {
    const headers: { [name: string]: string } = {};
    if (galleryPassword) {
      headers['X-Gallery-Password'] = galleryPassword;
    }

    return this.uploadImagesWithEndpoints(
      `${this.API_URL}/events/${eventId}/public/images/presigned-url`,
      `${this.API_URL}/events/${eventId}/public/images/uploaded`,
      {
        fileNames: files.map((f) => f.file.name),
        contentTypes: files.map((f) => f.file.type),
        fileSizes: files.map((f) => f.file.size),
      },
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
          const uploads = presignedUrls.map((presigned, index) => {
            const selectedFile = files[index];
            if (!selectedFile) {
              return of({ imageId: presigned.imageId, success: false, error: 'File not found' });
            }
            return this.uploadToS3(presigned.uploadUrl, selectedFile.file).pipe(
              switchMap(() =>
                this.notifyBackend(uploadedEndpoint, presigned.imageId, headers, withCredentials),
              ),
              catchError((error: unknown) => {
                if (error instanceof HttpErrorResponse && error.status === 401) {
                  return throwError(() => error);
                }
                const message = error instanceof Error ? error.message : 'Upload failed';
                return of({ imageId: presigned.imageId, success: false, error: message });
              }),
            );
          });

          return forkJoin(uploads);
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
    imageId: string,
    headers: { [name: string]: string },
    withCredentials: boolean,
  ): Observable<UploadResult> {
    return this.http
      .post<void>(uploadedEndpoint, [imageId], { headers, withCredentials })
      .pipe(map(() => ({ imageId, success: true })));
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
