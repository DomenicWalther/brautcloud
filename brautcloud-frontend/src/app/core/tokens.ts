import { InjectionToken } from '@angular/core';
import type { AuthService } from '../services/auth-service';

export const API_URL = new InjectionToken<string>('API_URL');
export const APP_URL = new InjectionToken<string>('APP_URL');
export const AUTH_SERVICE = new InjectionToken<AuthService>('AUTH_SERVICE');
