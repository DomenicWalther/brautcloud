import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_URL } from '../core/tokens';
import { authInterceptor } from './auth-interceptor';
import { AuthService } from './auth-service';

describe('authInterceptor public gallery requests', () => {
  const apiUrl = 'https://api.example.test/api';
  let auth: AuthService;
  let http: HttpTestingController;
  let httpClient: HttpClient;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_URL, useValue: apiUrl },
      ],
    });

    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  it('keeps an authenticated visitor on public gallery after a password 401', () => {
    authenticate('expired-or-owner-token');

    let requestError: unknown;
    httpClient
      .get(`${apiUrl}/events/event-1/public/images`, {
        headers: { 'X-Gallery-Password': 'wrong-password' },
      })
      .subscribe({ error: (error) => (requestError = error) });

    const request = http.expectOne(`${apiUrl}/events/event-1/public/images`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush('Gallery password required', { status: 401, statusText: 'Unauthorized' });

    expect(requestError).toBeTruthy();
    http.expectNone(`${apiUrl}/auth/refresh`);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('does not turn a signed-out public gallery password 401 into auth navigation', () => {
    let requestError: unknown;
    httpClient
      .get(`${apiUrl}/events/event-1/public/images`, {
        headers: { 'X-Gallery-Password': 'wrong-password' },
      })
      .subscribe({ error: (error) => (requestError = error) });

    const request = http.expectOne(`${apiUrl}/events/event-1/public/images`);
    request.flush('Gallery password required', { status: 401, statusText: 'Unauthorized' });

    expect(requestError).toBeTruthy();
    http.expectNone(`${apiUrl}/auth/refresh`);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  function authenticate(accessToken: string): void {
    auth.login({ email: 'couple@example.test', password: 'secret' }).subscribe();
    const request = http.expectOne(`${apiUrl}/auth/login`);
    request.flush({ accessToken, onboardingComplete: true });
  }
});
