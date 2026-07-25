import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router, UrlTree } from '@angular/router';
import { Subject } from 'rxjs';
import { OnboardingResponse } from '../../../core/models/onboarding.dto';
import { AuthRoutingService } from '../../../services/auth-routing-service';
import { AuthService } from '../../../services/auth-service';
import { OnboardingService } from '../../../services/onboarding-service';
import { Onboarding } from './onboarding';

describe('Onboarding accessibility', () => {
  it('starts on an accessible, validation-gated first step', async () => {
    await TestBed.configureTestingModule({
      imports: [Onboarding],
      providers: [
        provideRouter([]),
        { provide: OnboardingService, useValue: { submitOnboarding: vi.fn() } },
        { provide: AuthService, useValue: { markOnboardingComplete: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
        {
          provide: AuthRoutingService,
          useValue: { destinationAfterOnboarding: vi.fn() },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(Onboarding);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const skipLink = root.querySelector('.bc-skip-link');
    const panel = root.querySelector('#onboarding-form');
    const continueButton = root.querySelector<HTMLButtonElement>('app-form-button button');

    expect(skipLink?.getAttribute('href')).toBe('#onboarding-form');
    expect(panel?.getAttribute('tabindex')).toBe('-1');
    expect(root.textContent).toContain('Step 1 of 3');
    expect(root.querySelectorAll('input[required]').length).toBe(3);
    expect(continueButton?.disabled).toBe(true);
  });
});

describe('Onboarding submission', () => {
  let responses: Subject<OnboardingResponse>;
  let onboardingService: { submitOnboarding: ReturnType<typeof vi.fn> };
  let authService: { markOnboardingComplete: ReturnType<typeof vi.fn> };
  let router: Router;
  let component: Onboarding;
  let fixture: ComponentFixture<Onboarding>;

  beforeEach(() => {
    responses = new Subject<OnboardingResponse>();
    onboardingService = {
      submitOnboarding: vi.fn(() => responses.asObservable()),
    };
    authService = {
      markOnboardingComplete: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [Onboarding],
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
    fixture = TestBed.createComponent(Onboarding);
    component = fixture.componentInstance;
  });

  it('renders and reaches Step 3 before submitting onboarding', () => {
    fillValidModel(component);
    fixture.detectChanges();

    const buttons = () =>
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    expect(fixture.nativeElement.textContent).toContain('Tell us about');
    expect(buttons()[0].textContent).toContain('Continue');

    buttons()[buttons().length - 1].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Where will you');
    expect(onboardingService.submitOnboarding).not.toHaveBeenCalled();

    buttons()[buttons().length - 1].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Create your');
    expect(buttons()[buttons().length - 1].textContent).toContain('Create gallery');

    buttons()[buttons().length - 1].click();
    expect(onboardingService.submitOnboarding).toHaveBeenCalledOnce();
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
