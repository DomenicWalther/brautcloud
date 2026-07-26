import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router, UrlTree } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthResponse } from '../../../core/models/auth.dto';
import { AuthRoutingService } from '../../../services/auth-routing-service';
import { AuthService } from '../../../services/auth-service';
import { SignUp } from './sign-up';

describe('SignUp registration lifecycle', () => {
  const authService = { register: vi.fn() };
  let router: Router;

  beforeEach(() => {
    authService.register.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        {
          provide: AuthRoutingService,
          useFactory: (providedRouter: Router) => ({
            safeReturnUrl: vi.fn(() => null),
            destinationAfterAuth: vi.fn(() => providedRouter.parseUrl('/app/onboarding')),
          }),
          deps: [Router],
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  it('navigates a newly authenticated incomplete account to onboarding', () => {
    authService.register.mockReturnValue(
      of({ accessToken: 'token', onboardingComplete: false } satisfies AuthResponse),
    );
    const component = createComponent();

    component.register({ email: 'new@example.com', password: 'Password123!' });

    expect(authService.register).toHaveBeenCalledOnce();
    const destination = vi.mocked(router.navigateByUrl).mock.calls[0][0] as UrlTree;
    expect(router.serializeUrl(destination)).toBe('/app/onboarding');
  });

  it('rejects passwords shorter than the backend minimum with one clear message', () => {
    const component = createComponent();

    component.registerForm.password().reset('short');

    expect(
      component.registerForm
        .password()
        .errors()
        .map((error) => error.message),
    ).toEqual(['Password must be at least 8 characters long']);
  });

  it('accepts a password with exactly eight characters', () => {
    const component = createComponent();

    component.registerForm.email().reset('new@example.com');
    component.registerForm.password().reset('12345678');
    component.registerForm.confirmPassword().reset('12345678');

    expect(component.registerForm.password().errors()).toEqual([]);
    expect(component.registerForm().invalid()).toBe(false);
  });

  it('does not add a mismatch message when confirmation is empty', () => {
    const component = createComponent();

    component.registerForm.password().reset('12345678');

    expect(
      component.registerForm
        .confirmPassword()
        .errors()
        .map((error) => error.message),
    ).toEqual(['Please confirm your Password']);
  });

  it('renders a server failure once without adding it to field validation', () => {
    authService.register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'Email already used!' },
          }),
      ),
    );
    const fixture = createFixture();
    fixture.componentInstance.registerForm.password().reset('Password123!');

    fixture.componentInstance.register({
      email: 'duplicate@example.com',
      password: 'Password123!',
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.registerForm.password().errors()).toEqual([]);
    expect(fixture.nativeElement.querySelectorAll('.auth-server-error')).toHaveLength(1);
    expect(fixture.nativeElement.textContent.match(/Email already used!/g)).toHaveLength(1);
  });

  it('shows a structured duplicate-registration failure without navigating', () => {
    authService.register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'Email already used!' },
          }),
      ),
    );
    const component = createComponent();

    component.register({ email: 'duplicate@example.com', password: 'Password123!' });

    expect(component.serverError()).toBe('Email already used!');
    expect(component.submitting()).toBe(false);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('suppresses duplicate registration submits while the request is pending', () => {
    const pending = new Subject<AuthResponse>();
    authService.register.mockReturnValue(pending.asObservable());
    const component = createComponent();

    component.register({ email: 'new@example.com', password: 'Password123!' });
    component.register({ email: 'new@example.com', password: 'Password123!' });

    expect(authService.register).toHaveBeenCalledOnce();
    expect(component.submitting()).toBe(true);
  });

  function createComponent(): SignUp {
    return TestBed.runInInjectionContext(() => new SignUp());
  }

  function createFixture(): ComponentFixture<SignUp> {
    return TestBed.createComponent(SignUp);
  }
});
