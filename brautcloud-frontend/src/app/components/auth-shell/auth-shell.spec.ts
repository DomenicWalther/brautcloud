import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthShell } from './auth-shell';

describe('AuthShell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthShell],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('connects its skip link and accessible title to the form panel', () => {
    const fixture = TestBed.createComponent(AuthShell);
    fixture.componentRef.setInput('eyebrow', 'Welcome back');
    fixture.componentRef.setInput('title', 'Sign in');
    fixture.componentRef.setInput('description', 'Return to your gallery.');
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const panel = root.querySelector('#auth-content');

    expect(root.querySelector('.bc-skip-link')?.getAttribute('href')).toBe('#auth-content');
    expect(panel?.getAttribute('aria-labelledby')).toBe('auth-title');
    expect(root.querySelector('#auth-title')?.textContent).toContain('Sign in');
    expect(root.querySelectorAll('a[aria-label="BrautCloud home"]').length).toBe(2);
  });
});
