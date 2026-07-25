import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { filter, map, take } from 'rxjs';
import { AuthRoutingService } from '../services/auth-routing-service';
import { AuthService } from '../services/auth-service';

export const guestGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const authRouting = inject(AuthRoutingService);

  return auth.initialized$.pipe(
    filter((initialized) => initialized),
    take(1),
    map(() => {
      return auth.isAuthenticated()
        ? authRouting.destinationAfterAuth(route.queryParamMap.get('returnUrl'))
        : true;
    }),
  );
};
