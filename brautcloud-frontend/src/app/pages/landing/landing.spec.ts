import { By } from '@angular/platform-browser';
import { provideRouter, RouterLink } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { Landing } from './landing';

describe('Landing', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Landing],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('renders the luxury landing page structure with keyboard-usable navigation', () => {
    const fixture = TestBed.createComponent(Landing);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const skipLink = root.querySelector('.skip-link');
    const primaryNav = root.querySelector('nav[aria-label="Primary navigation"]');
    const footerNav = root.querySelector('nav[aria-label="Footer navigation"]');
    const main = root.querySelector('main#main-content');

    expect(skipLink?.textContent).toContain('Skip to content');
    expect(skipLink?.getAttribute('href')).toBe('#main-content');
    expect(primaryNav).toBeTruthy();
    expect(footerNav).toBeTruthy();
    expect(main).toBeTruthy();
    expect(primaryNav?.querySelectorAll('a').length).toBe(3);
  });

  it('preserves sign-in and sign-up routes across the landing page calls to action', () => {
    const fixture = TestBed.createComponent(Landing);
    fixture.detectChanges();

    const routerLinks = fixture.debugElement
      .queryAll(By.directive(RouterLink))
      .map((debugElement) => debugElement.injector.get(RouterLink).href);

    expect(routerLinks).toContain('/auth/sign-in');
    expect(routerLinks).toContain('/auth/sign-up');
  });

  it('renders accessible wedding imagery and key English marketing copy', () => {
    const fixture = TestBed.createComponent(Landing);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const heroImage = root.querySelector('img');

    expect(root.textContent).toContain('Preserve Every');
    expect(root.textContent).toContain('Treasured Moment');
    expect(root.textContent).toContain('The Experience');
    expect(root.textContent).toContain('Begin Your');
    expect(heroImage?.getAttribute('alt')).toBe('A newlywed couple embracing beside a lake');
  });
});
