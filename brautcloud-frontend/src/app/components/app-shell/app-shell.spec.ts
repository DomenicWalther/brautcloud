import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppShell } from './app-shell';

const expectedWorkspaceRoutes = ['/app/home', '/app/gallery', '/app/upload'];

describe('AppShell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('provides consistent desktop and mobile workspace navigation', () => {
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
  });

  it('offers a skip link to the projected workspace content', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.bc-skip-link')?.getAttribute('href')).toBe('#workspace-content');
    expect(root.querySelector('main#workspace-content')).toBeTruthy();
  });
});
