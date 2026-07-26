import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('should create the app shell', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the application router outlet', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('keeps every public, authentication, and workspace page family routed', () => {
    const authPaths = routes
      .find((route) => route.path === 'auth')
      ?.children?.map((route) => route.path);
    const appPaths = routes
      .find((route) => route.path === 'app')
      ?.children?.map((route) => route.path);

    expect(routes.some((route) => route.path === '')).toBe(true);
    expect(authPaths).toEqual(['sign-in', 'sign-up', 'reset-password']);
    expect(appPaths).toEqual(['home', 'upload', 'gallery', 'onboarding', 'settings', '**']);
    const publicEventRoutes = routes.filter(
      (route) => route.path === 'event' || route.path === 'event/:eventId',
    );
    expect(publicEventRoutes).toHaveLength(2);
    expect(publicEventRoutes.every((route) => route.loadComponent)).toBe(true);
    expect(routes.indexOf(publicEventRoutes[0]!)).toBeLessThan(routes.length - 1);
    expect(routes.at(-1)?.path).toBe('**');
  });
});
