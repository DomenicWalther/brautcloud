import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth-service';
import { inject } from '@angular/core';
import { filter, map, take } from 'rxjs';

export const guestGuard: CanActivateFn = (route, state) => {

  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.initialized$.pipe(
    filter((initialized) => initialized === true),
    take(1),
    map(() => {
      return !auth.isAuthenticated()
        ? true
        : router.createUrlTree(['/app/home']);
    })
  )
};
