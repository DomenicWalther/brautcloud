import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AUTH_SERVICE } from '../../core/tokens';
import { UserService } from '../../services/user-service';
import { AppShell } from './app-shell';

const expectedWorkspaceRoutes = ['/app/home', '/app/gallery', '/app/upload'];
const isLoggingOut = signal(false);
const logout = vi.fn();
const reload = vi.fn();

describe('AppShell', () => {
  beforeEach(async () => {
    isLoggingOut.set(false);
    logout.mockClear();
    reload.mockClear();

    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        {
          provide: AUTH_SERVICE,
          useValue: { isLoggingOut: isLoggingOut.asReadonly(), logout },
        },
        { provide: UserService, useValue: { reload } },
      ],
    }).compileComponents();
  });

  it('owns one workspace navigation and the authenticated sign-out control', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const desktopLinks = Array.from(root.querySelectorAll('.workspace-nav a')).map((link) =>
      link.getAttribute('href'),
    );
    const mobileLinks = Array.from(root.querySelectorAll('.workspace-mobile-nav a')).map((link) =>
      link.getAttribute('href'),
    );

    expect(desktopLinks).toEqual(expectedWorkspaceRoutes);
    expect(mobileLinks).toEqual(expectedWorkspaceRoutes);
    expect(root.querySelector<HTMLAnchorElement>('.bc-brand-lockup')?.getAttribute('href')).toBe(
      '/app/home',
    );
    expect(root.querySelectorAll('nav').length).toBe(2);
    expect(root.querySelector<HTMLButtonElement>('.workspace-signout')?.textContent).toContain(
      'Sign out',
    );

    root.querySelector<HTMLButtonElement>('.workspace-signout')?.click();
    expect(logout).toHaveBeenCalledOnce();
  });

  it('refreshes workspace user state when authenticated shell mounts after auth', () => {
    TestBed.createComponent(AppShell).detectChanges();

    expect(reload).toHaveBeenCalledOnce();
  });

  it('offers a skip link to the projected workspace content', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.bc-skip-link')?.getAttribute('href')).toBe('#workspace-content');
    expect(root.querySelector('main#workspace-content')).toBeTruthy();
  });
});
