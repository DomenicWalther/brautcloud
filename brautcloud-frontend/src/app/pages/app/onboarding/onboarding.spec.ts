import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router, UrlTree } from '@angular/router';
import { Subject } from 'rxjs';
import { OnboardingResponse } from '../../../core/models/onboarding.dto';
import { AuthRoutingService } from '../../../services/auth-routing-service';
import { AuthService } from '../../../services/auth-service';
import { OnboardingService } from '../../../services/onboarding-service';
import { Onboarding } from './onboarding';

describe('Onboarding submission', () => {
  let responses: Subject<OnboardingResponse>;
  let onboardingService: { submitOnboarding: ReturnType<typeof vi.fn> };
  let authService: { markOnboardingComplete: ReturnType<typeof vi.fn> };
  let router: Router;
  let component: Onboarding;

  beforeEach(() => {
    responses = new Subject<OnboardingResponse>();
    onboardingService = {
      submitOnboarding: vi.fn(() => responses.asObservable()),
    };
    authService = {
      markOnboardingComplete: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OnboardingService, useValue: onboardingService },
        { provide: AuthService, useValue: authService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({ returnUrl: '/app/upload' }) },
          },
        },
        {
          provide: AuthRoutingService,
          useFactory: (providedRouter: Router) => ({
            destinationAfterOnboarding: vi.fn(() => providedRouter.parseUrl('/app/upload')),
          }),
          deps: [Router],
        },
      ],
    });

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    component = TestBed.runInInjectionContext(() => new Onboarding());
  });

  it('validates before sending and prevents duplicate submissions while pending', () => {
    component.submit();
    expect(onboardingService.submitOnboarding).not.toHaveBeenCalled();

    fillValidModel(component);
    component.submit();
    component.submit();

    expect(onboardingService.submitOnboarding).toHaveBeenCalledTimes(1);
    expect(onboardingService.submitOnboarding).toHaveBeenCalledWith(component.model());
    expect(component.submitting()).toBe(true);
  });

  it('preserves values and exposes a visible error after a failed submission', () => {
    fillValidModel(component);
    component.submit();
    responses.error(
      new HttpErrorResponse({
        status: 400,
        error: { message: 'Venue is required' },
      }),
    );

    expect(component.submitting()).toBe(false);
    expect(component.submissionError()).toBe('Venue is required');
    expect(component.model().firstName).toBe('Sophie');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('marks the session complete and navigates only after backend success', () => {
    fillValidModel(component);
    component.submit();
    expect(router.navigateByUrl).not.toHaveBeenCalled();

    responses.next({ onboardingComplete: true, event: null });

    expect(authService.markOnboardingComplete).toHaveBeenCalledOnce();
    expect(router.navigateByUrl).toHaveBeenCalledOnce();
    const destination = vi.mocked(router.navigateByUrl).mock.calls[0][0] as UrlTree;
    expect(router.serializeUrl(destination)).toBe('/app/upload');
  });
});

function fillValidModel(component: Onboarding): void {
  component.model.set({
    firstName: 'Sophie',
    partnerFirstName: 'Marcus',
    familyName: 'Müller-Weber',
    venue: 'Eichenfürst',
  });
}
