import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth-service';
import { inject } from '@angular/core';
import { timer, switchMap, takeWhile, lastValueFrom, firstValueFrom } from 'rxjs';

export const authGuard: CanActivateFn = async (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  while (!auth.isInitialized()) {
    await new Promise((resolve) => setTimeout(resolve, 50));
  }

  return auth.isLoggedIn() ? true : router.createUrlTree(['/auth/sign-in']);
};
