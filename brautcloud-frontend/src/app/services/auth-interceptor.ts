import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthRoutingService } from './auth-routing-service';
import { AuthService } from './auth-service';

const PUBLIC_GALLERY_REQUEST = /\/events\/[^/]+\/public(?:\/|$)/;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const authRouting = inject(AuthRoutingService);
  const router = inject(Router);
  const isPublicGalleryRequest = PUBLIC_GALLERY_REQUEST.test(req.url);

  const token = auth.getAccessToken();

  const authReq =
    token && !isPublicGalleryRequest
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isPublicGalleryRequest && auth.canAttemptRefresh(req.url)) {
        return auth.refreshToken().pipe(
          switchMap((newToken) => {
            return next(req.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } }));
          }),
          catchError((refreshErr) => {
            void router.navigateByUrl(authRouting.signInDestination(router.url));
            return throwError(() => refreshErr);
          }),
        );
      }
      return throwError(() => err);
    }),
  );
};
