import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core/primitives/di';
import { AuthService } from './auth-service';
import { Observable } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const authReq = addToken(req, authService.getAccessToken());
  return new Observable((observer) => {
    next(authReq).subscribe({
      next: (event) => observer.next(event),
      error: (err) => {
        if (err instanceof HttpErrorResponse && err.status === 401) {
          authService.refresh(
            (newToken) => {
              next(addToken(req, newToken)).subscribe({
                next: (event) => observer.next(event),
                error: (retryErr) => observer.error(retryErr),
                complete: () => observer.complete(),
              });
            },
            () => observer.error(err),
          );
        } else {
          observer.error(err);
        }
      },
      complete: () => observer.complete(),
    });
  });
};

const addToken = (req: HttpRequest<any>, token: string | null): HttpRequest<any> => {
  if (!token) return req;
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
};
