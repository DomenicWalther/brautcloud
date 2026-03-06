import { Routes } from '@angular/router';
import { authGuard } from './core/auth-guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => {
      return import('./pages/landing/landing').then((m) => m.Landing);
    },
  },
  {
    path: 'auth/sign-in',
    loadComponent: () => {
      return import('./pages/auth/sign-in/sign-in').then((m) => m.SignIn);
    },
  },

  {
    path: 'auth/sign-up',
    loadComponent: () => {
      return import('./pages/auth/sign-up/sign-up').then((m) => m.SignUp);
    },
  },
  {
    path: 'auth/reset-password',
    loadComponent: () => {
      return import('./pages/auth/reset-password/reset-password').then((m) => m.ResetPassword);
    },
  },
  {
    path: 'app',
    canActivate: [authGuard],
    children: [
      { path: 'home', loadComponent: () => import('./pages/app/home/home').then((m) => m.Home) },
      {
        path: 'upload',
        loadComponent: () =>
          import('./pages/app/image-upload/image-upload').then((m) => m.ImageUpload),
      },
      {
        path: 'gallery',
        loadComponent: () =>
          import('./pages/app/image-gallery/image-gallery').then((m) => m.ImageGallery),
      },
      {
        path: 'onboarding',
        loadComponent: () => import('./pages/app/onboarding/onboarding').then((m) => m.Onboarding),
      },
      {
        path: '**', redirectTo: 'home'
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
