import { TestBed } from '@angular/core/testing';
import { App, startLenisLoop } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('uses Lenis defaults and cleans up its RAF loop and instance', () => {
    const lenis = {
      raf: vi.fn(),
      destroy: vi.fn(),
    };
    class MockLenis {
      raf = lenis.raf;
      destroy = lenis.destroy;
    }
    const Lenis = vi.fn(MockLenis) as unknown as new () => typeof lenis;
    const callbacks = new Map<number, FrameRequestCallback>();
    const requestFrame = vi.fn((callback: FrameRequestCallback) => {
      const id = callbacks.size + 1;
      callbacks.set(id, callback);
      return id;
    });
    const cancelFrame = vi.fn();

    const cleanup = startLenisLoop(Lenis, requestFrame, cancelFrame);

    expect(Lenis).toHaveBeenCalledWith();
    expect(requestFrame).toHaveBeenCalledTimes(1);

    callbacks.get(1)?.(123.4);
    expect(lenis.raf).toHaveBeenCalledWith(123.4);
    expect(requestFrame).toHaveBeenCalledTimes(2);

    cleanup();
    cleanup();

    expect(cancelFrame).toHaveBeenCalledWith(2);
    expect(cancelFrame).toHaveBeenCalledTimes(1);
    expect(lenis.destroy).toHaveBeenCalledTimes(1);

    callbacks.get(2)?.(456.7);
    expect(lenis.raf).toHaveBeenCalledTimes(1);
    expect(requestFrame).toHaveBeenCalledTimes(2);
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
