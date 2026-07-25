import { InjectionToken } from '@angular/core';

export const API_URL = new InjectionToken<string>('API_URL');
export const APP_URL = new InjectionToken<string>('APP_URL');

/**
 * Maximum time logout waits for server-side refresh revocation before finishing navigation.
 * Local credentials are cleared immediately; five seconds gives a slow API a reasonable chance
 * to expire the HttpOnly cookie without leaving the user trapped in a signing-out state.
 */
export const LOGOUT_COMPLETION_DEADLINE_MS = new InjectionToken<number>(
  'LOGOUT_COMPLETION_DEADLINE_MS',
  {
    providedIn: 'root',
    factory: () => 5_000,
  },
);
