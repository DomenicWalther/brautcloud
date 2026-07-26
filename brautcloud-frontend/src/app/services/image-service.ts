import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, forkJoin, switchMap, of, map } from 'rxjs';
import { API_URL } from '../core/tokens';
import { EventImageDto } from '../core/models/event-image.dto';

interface PresignedUrlRequest {
  eventId: string;
  fileNames: string[];
}

export interface SelectedFile {
  file: File;
  preview: string;
}

interface PresignedUrlResponse {
  imageId: string;
  uploadUrl: string;
}

interface UploadResult {
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

  getPublicEventImages(eventId: string): Observable<EventImageDto[]> {
    return this.http.get<EventImageDto[]>(`${this.API_URL}/events/${eventId}/public/images`);
  }

  uploadImages(eventId: string, files: SelectedFile[]): Observable<UploadResult[]> {
    const fileNames = files.map((f) => f.file.name);

    return this.http
      .post<PresignedUrlResponse[]>(
        `${this.API_URL}/image/presigned-url`,
        {
          eventId,
          fileNames,
        } as PresignedUrlRequest,
        { withCredentials: true },
      )
      .pipe(
        switchMap((presignedUrls) => {
          const uploads = presignedUrls.map((presigned, index) => {
            const selectedFile = files[index];
            if (!selectedFile) {
              return of({ imageId: presigned.imageId, success: false, error: 'File not found' });
            }
            return this.uploadToS3(presigned.uploadUrl, selectedFile.file).pipe(
              switchMap(() => this.notifyBackend(presigned.imageId)),
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

  private notifyBackend(imageId: string): Observable<UploadResult> {
    return this.http
      .post<void>(`${this.API_URL}/image/uploaded`, [imageId], { withCredentials: true })
      .pipe(map(() => ({ imageId, success: true })));
  }

  deleteImage(imageId: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/image/${imageId}`, { withCredentials: true });
  }
}
