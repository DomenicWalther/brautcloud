import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { authGuard } from '../core/auth-guard';
import { guestGuard } from '../core/guest-guard';
import { onboardingPageGuard, onboardingRequiredGuard } from '../core/onboarding-guards';
import { AuthRoutingService } from './auth-routing-service';
import { AuthService } from './auth-service';

describe('onboarding-aware auth routing', () => {
  const authenticated = signal(false);
  const onboardingComplete = signal(false);
  const authStub = {
    initialized$: of(true),
    isAuthenticated: authenticated,
    isOnboardingComplete: onboardingComplete,
  };

  let router: Router;
  let routing: AuthRoutingService;

  beforeEach(() => {
    authenticated.set(false);
    onboardingComplete.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        AuthRoutingService,
        { provide: AuthService, useValue: authStub },
      ],
    });
    router = TestBed.inject(Router);
    routing = TestBed.inject(AuthRoutingService);
  });

  it('accepts only known internal application destinations', () => {
    expect(routing.safeReturnUrl('/app/upload?from=signup')).toBe('/app/upload?from=signup');
    expect(routing.safeReturnUrl('/app/gallery#latest')).toBe('/app/gallery#latest');
    expect(routing.safeReturnUrl('https://evil.example/app/home')).toBeNull();
    expect(routing.safeReturnUrl('//evil.example/app/home')).toBeNull();
    expect(routing.safeReturnUrl('/auth/sign-in')).toBeNull();
    expect(routing.safeReturnUrl('/app/onboarding')).toBeNull();
    expect(routing.safeReturnUrl('/app/home\\evil')).toBeNull();
  });

  it('routes incomplete sessions to onboarding and preserves a safe destination', () => {
    expect(router.serializeUrl(routing.destinationAfterAuth('/app/upload'))).toBe(
      '/app/onboarding?returnUrl=%2Fapp%2Fupload',
    );
    expect(router.serializeUrl(routing.destinationAfterAuth('https://evil.example'))).toBe(
      '/app/onboarding',
    );
  });

  it('routes completed sessions to a safe destination or home', () => {
    authenticated.set(true);
    onboardingComplete.set(true);

    expect(router.serializeUrl(routing.destinationAfterAuth('/app/gallery'))).toBe('/app/gallery');
    expect(router.serializeUrl(routing.destinationAfterAuth('https://evil.example'))).toBe(
      '/app/home',
    );
  });

  it('enforces the unauthenticated, incomplete, and complete route matrix', async () => {
    const homeState = state('/app/home');
    const onboardingState = state('/app/onboarding?returnUrl=%2Fapp%2Fupload');

    const unauthenticatedResult = await runGuard(authGuard, route(), homeState);
    expect(router.serializeUrl(unauthenticatedResult as UrlTree)).toBe(
      '/auth/sign-in?returnUrl=%2Fapp%2Fhome',
    );

    authenticated.set(true);
    expect(await runGuard(authGuard, route(), homeState)).toBe(true);
    const incompleteAppResult = await runGuard(onboardingRequiredGuard, route(), homeState);
    expect(router.serializeUrl(incompleteAppResult as UrlTree)).toBe(
      '/app/onboarding?returnUrl=%2Fapp%2Fhome',
    );
    expect(await runGuard(onboardingPageGuard, route('/app/upload'), onboardingState)).toBe(true);

    const incompleteGuestResult = await runGuard(guestGuard, route('/app/upload'), homeState);
    expect(router.serializeUrl(incompleteGuestResult as UrlTree)).toBe(
      '/app/onboarding?returnUrl=%2Fapp%2Fupload',
    );

    onboardingComplete.set(true);
    expect(await runGuard(onboardingRequiredGuard, route(), homeState)).toBe(true);
    const completedOnboardingResult = await runGuard(
      onboardingPageGuard,
      route('/app/upload'),
      onboardingState,
    );
    expect(router.serializeUrl(completedOnboardingResult as UrlTree)).toBe('/app/upload');

    const completedGuestResult = await runGuard(guestGuard, route(), homeState);
    expect(router.serializeUrl(completedGuestResult as UrlTree)).toBe('/app/home');
  });

  it('preserves the safe destination when an onboarding session is interrupted', () => {
    const result = routing.signInDestination(
      '/app/onboarding?returnUrl=%2Fapp%2Fgallery%3Fview%3Dlatest',
    );
    expect(router.serializeUrl(result)).toBe(
      '/auth/sign-in?returnUrl=%2Fapp%2Fgallery%3Fview%3Dlatest',
    );
  });
});

type Guard = typeof authGuard;

function route(returnUrl?: string): ActivatedRouteSnapshot {
  return {
    queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}),
  } as ActivatedRouteSnapshot;
}

function state(url: string): RouterStateSnapshot {
  return { url } as RouterStateSnapshot;
}

function runGuard(
  guard: Guard,
  activatedRoute: ActivatedRouteSnapshot,
  routerState: RouterStateSnapshot,
): Promise<boolean | UrlTree> {
  const result = TestBed.runInInjectionContext(() => guard(activatedRoute, routerState));
  return firstValueFrom(result as Observable<boolean | UrlTree>);
}
