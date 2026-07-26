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
import { UserService } from './services/user-service';
import { environment } from '../environments/environment';
import { API_URL, APP_URL, AUTH_SERVICE } from './core/tokens';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      const userService = inject(UserService);
      return auth.initializeAuth().then(() => {
        if (auth.isAuthenticated()) {
          userService.reload();
        }
      });
    }),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    {
      provide: API_URL,
      useValue: environment.apiUrl,
    },
    {
      provide: APP_URL,
      useValue: environment.appUrl,
    },
    {
      provide: AUTH_SERVICE,
      useExisting: AuthService,
    },
  ],
};
