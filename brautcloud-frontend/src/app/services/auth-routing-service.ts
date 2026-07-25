import { inject, Injectable } from '@angular/core';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from './auth-service';

const HOME_URL = '/app/home';
const ONBOARDING_URL = '/app/onboarding';
const SAFE_APP_DESTINATION = /^\/app\/(?:home|upload|gallery)(?:[?#].*)?$/;

@Injectable({ providedIn: 'root' })
export class AuthRoutingService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  destinationAfterAuth(returnUrl: string | null = null): UrlTree {
    const safeReturnUrl = this.safeReturnUrl(returnUrl);

    if (!this.auth.isOnboardingComplete()) {
      return this.router.createUrlTree([ONBOARDING_URL], {
        queryParams: safeReturnUrl ? { returnUrl: safeReturnUrl } : undefined,
      });
    }

    return this.router.parseUrl(safeReturnUrl ?? HOME_URL);
  }

  destinationAfterOnboarding(returnUrl: string | null = null): UrlTree {
    return this.router.parseUrl(this.safeReturnUrl(returnUrl) ?? HOME_URL);
  }

  signInDestination(requestedUrl: string | null): UrlTree {
    const safeReturnUrl = this.intendedReturnUrl(requestedUrl);
    return this.router.createUrlTree(['/auth/sign-in'], {
      queryParams: safeReturnUrl ? { returnUrl: safeReturnUrl } : undefined,
    });
  }

  onboardingDestination(returnUrl: string | null): UrlTree {
    const safeReturnUrl = this.safeReturnUrl(returnUrl);
    return this.router.createUrlTree([ONBOARDING_URL], {
      queryParams: safeReturnUrl ? { returnUrl: safeReturnUrl } : undefined,
    });
  }

  safeReturnUrl(candidate: string | null): string | null {
    if (!candidate || candidate.includes('\\') || /[\u0000-\u001f]/.test(candidate)) {
      return null;
    }

    return SAFE_APP_DESTINATION.test(candidate) ? candidate : null;
  }

  private intendedReturnUrl(requestedUrl: string | null): string | null {
    const directDestination = this.safeReturnUrl(requestedUrl);
    if (directDestination || !requestedUrl?.startsWith(ONBOARDING_URL)) {
      return directDestination;
    }

    try {
      const onboardingUrl = this.router.parseUrl(requestedUrl);
      const returnUrl = onboardingUrl.queryParams['returnUrl'];
      return typeof returnUrl === 'string' ? this.safeReturnUrl(returnUrl) : null;
    } catch {
      return null;
    }
  }
}
