import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { routes } from '../../../app.routes';
import { AuthenticatedLayout } from './authenticated-layout';

describe('AuthenticatedLayout', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthenticatedLayout],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('only projects the active route so child shells own their navigation', () => {
    const fixture = TestBed.createComponent(AuthenticatedLayout);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('router-outlet')).toBeTruthy();
    expect(element.querySelector('header')).toBeNull();
    expect(element.querySelector('nav')).toBeNull();
    expect(element.querySelector('button')).toBeNull();
  });

  it('is the layout used by the authenticated route', async () => {
    const appRoute = routes.find((route) => route.path === 'app');

    expect(appRoute?.loadComponent).toBeDefined();
    expect(await appRoute?.loadComponent?.()).toBe(AuthenticatedLayout);
  });
});
