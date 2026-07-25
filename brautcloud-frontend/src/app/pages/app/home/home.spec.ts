import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { APP_URL } from '../../../core/tokens';
import { UserDto } from '../../../core/models/user.dto';
import { UserService } from '../../../services/user-service';
import { Home } from './home';

describe('Home defensive empty state', () => {
  const user = signal<UserDto | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    user.set(null);
    loading.set(false);
    error.set(null);

    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        { provide: APP_URL, useValue: 'http://app.test' },
        {
          provide: UserService,
          useValue: {
            user: user.asReadonly(),
            loading: loading.asReadonly(),
            error: error.asReadonly(),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Home);
  });

  it('does not render broken names, countdowns, stats, or QR data without an event', () => {
    fixture.detectChanges();
    const content = fixture.nativeElement.textContent as string;

    expect(content).toContain('No gallery is available yet');
    expect(content).not.toContain('Welcome,');
    expect(content).not.toContain('Days until your Wedding');
    expect(content).not.toContain('Live Gallery');
    expect(fixture.nativeElement.querySelector('qr-code')).toBeNull();
  });

  it('renders explicit loading and error states', () => {
    loading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading your gallery');

    loading.set(false);
    error.set('Unable to load');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Your gallery is temporarily unavailable');
    expect(fixture.nativeElement.textContent).toContain('Unable to load');
  });
});
