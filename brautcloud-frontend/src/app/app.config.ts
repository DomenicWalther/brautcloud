import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './services/auth-interceptor';
import { AuthService } from './services/auth-service';
import { environment } from '../environments/environment';
import { API_URL } from './core/tokens';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      return auth.initializeAuth();
    }),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    {
      provide: API_URL, useValue: environment.apiUrl
    }
  ],
};
