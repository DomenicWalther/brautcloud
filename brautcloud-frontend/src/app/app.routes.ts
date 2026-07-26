import { Routes } from '@angular/router';
import { authGuard } from './core/auth-guard';
import { guestGuard } from './core/guest-guard';
import { onboardingPageGuard, onboardingRequiredGuard } from './core/onboarding-guards';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => {
      return import('./pages/landing/landing').then((m) => m.Landing);
    },
  },
  {
    path: 'auth',
    canActivate: [guestGuard],
    children: [
      {
        path: 'sign-in',
        loadComponent: () => {
          return import('./pages/auth/sign-in/sign-in').then((m) => m.SignIn);
        },
      },

      {
        path: 'sign-up',
        loadComponent: () => {
          return import('./pages/auth/sign-up/sign-up').then((m) => m.SignUp);
        },
      },
      {
        path: 'reset-password',
        loadComponent: () => {
          return import('./pages/auth/reset-password/reset-password').then((m) => m.ResetPassword);
        },
      },
    ],
  },
  {
    path: 'event',
    loadComponent: () => import('./pages/event/event-gallery').then((m) => m.EventGallery),
  },
  {
    path: 'event/:eventId',
    loadComponent: () => import('./pages/event/event-gallery').then((m) => m.EventGallery),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/app/authenticated-layout/authenticated-layout').then(
        (m) => m.AuthenticatedLayout,
      ),
    children: [
      {
        path: 'home',
        canActivate: [onboardingRequiredGuard],
        loadComponent: () => import('./pages/app/home/home').then((m) => m.Home),
      },
      {
        path: 'upload',
        canActivate: [onboardingRequiredGuard],
        loadComponent: () =>
          import('./pages/app/image-upload/image-upload').then((m) => m.ImageUpload),
      },
      {
        path: 'gallery',
        canActivate: [onboardingRequiredGuard],
        loadComponent: () =>
          import('./pages/app/image-gallery/image-gallery').then((m) => m.ImageGallery),
      },
      {
        path: 'onboarding',
        canActivate: [onboardingPageGuard],
        loadComponent: () => import('./pages/app/onboarding/onboarding').then((m) => m.Onboarding),
      },
      {
        path: 'settings',
        canActivate: [onboardingRequiredGuard],
        loadComponent: () => import('./pages/app/settings/settings').then((m) => m.Settings),
      },
      {
        path: '**',
        redirectTo: 'home',
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
