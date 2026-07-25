import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { routes } from '../../../app.routes';
import { AuthService } from '../../../services/auth-service';
import { AuthenticatedLayout } from './authenticated-layout';

describe('AuthenticatedLayout', () => {
  let fixture: ComponentFixture<AuthenticatedLayout>;
  const logout = vi.fn();

  beforeEach(async () => {
    logout.mockClear();

    await TestBed.configureTestingModule({
      imports: [AuthenticatedLayout],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isLoggingOut: signal(false).asReadonly(),
            logout,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuthenticatedLayout);
    fixture.detectChanges();
  });

  it('makes the sign-out control accessible from the shared authenticated UI', () => {
    const element = fixture.nativeElement as HTMLElement;
    const button = element.querySelector<HTMLButtonElement>('button');

    expect(element.querySelector('router-outlet')).toBeTruthy();
    expect(button).toBeTruthy();
    expect(button?.type).toBe('button');
    expect(button?.textContent).toContain('Sign out');

    button?.click();
    expect(logout).toHaveBeenCalledOnce();
  });

  it('is the layout used by the authenticated route', async () => {
    const appRoute = routes.find((route) => route.path === 'app');

    expect(appRoute?.loadComponent).toBeDefined();
    expect(await appRoute?.loadComponent?.()).toBe(AuthenticatedLayout);
  });
});
