import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_URL } from '../core/tokens';
import { authInterceptor } from './auth-interceptor';
import { AuthService } from './auth-service';

describe('AuthService logout', () => {
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

  afterEach(() => {
    http.verify();
  });

  it('revokes the refresh session with credentials, clears local auth, and navigates', () => {
    authenticate('access-token');

    auth.logout();

    expect(auth.getAccessToken()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.isLoggingOut()).toBe(true);

    const request = http.expectOne(`${apiUrl}/auth/logout`);
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    request.flush('Logged out');

    expect(auth.isLoggingOut()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/sign-in', { replaceUrl: true });
  });

  it('stays locally signed out and navigates when the logout endpoint fails', () => {
    authenticate('access-token');

    auth.logout();

    const request = http.expectOne(`${apiUrl}/auth/logout`);
    expect(request.request.withCredentials).toBe(true);
    request.flush('Unavailable', { status: 401, statusText: 'Unauthorized' });

    http.expectNone(`${apiUrl}/auth/refresh`);
    expect(auth.getAccessToken()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.isLoggingOut()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/sign-in', { replaceUrl: true });
  });

  it('blocks later interceptor refresh attempts after a failed logout', () => {
    authenticate('access-token');

    auth.logout();

    const logout = http.expectOne(`${apiUrl}/auth/logout`);
    logout.flush('Unavailable', { status: 401, statusText: 'Unauthorized' });

    let protectedRequestError: unknown;
    httpClient.get(`${apiUrl}/protected`).subscribe({ error: (error) => (protectedRequestError = error) });

    const protectedRequest = http.expectOne(`${apiUrl}/protected`);
    expect(protectedRequest.request.headers.has('Authorization')).toBe(false);
    protectedRequest.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    http.expectNone(`${apiUrl}/auth/refresh`);
    expect(protectedRequestError).toBeTruthy();
    expect(auth.getAccessToken()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
  });

  it('waits for an in-flight refresh before revoking it and ignores its stale access token', () => {
    authenticate('original-token');

    let refreshError: unknown;
    auth.refreshToken().subscribe({ error: (error) => (refreshError = error) });
    const refresh = http.expectOne(`${apiUrl}/auth/refresh`);

    auth.logout();

    expect(auth.getAccessToken()).toBeNull();
    http.expectNone(`${apiUrl}/auth/logout`);

    refresh.flush({ accessToken: 'stale-refreshed-token' });

    expect(refreshError).toBeInstanceOf(Error);
    expect(auth.getAccessToken()).toBeNull();

    const logout = http.expectOne(`${apiUrl}/auth/logout`);
    expect(logout.request.withCredentials).toBe(true);
    expect(logout.request.headers.has('Authorization')).toBe(false);
    logout.flush('Logged out');

    expect(auth.getAccessToken()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/sign-in', { replaceUrl: true });
  });

  it('bounds the wait for an in-flight refresh before navigating away', async () => {
    vi.useFakeTimers();

    try {
      authenticate('original-token');

      let refreshError: unknown;
      auth.refreshToken().subscribe({ error: (error) => (refreshError = error) });
      const refresh = http.expectOne(`${apiUrl}/auth/refresh`);

      auth.logout();

      http.expectNone(`${apiUrl}/auth/logout`);

      await vi.advanceTimersByTimeAsync(1000);

      const logout = http.expectOne(`${apiUrl}/auth/logout`);
      expect(logout.request.withCredentials).toBe(true);
      logout.flush('Logged out');

      refresh.flush({ accessToken: 'late-token' });

      expect(refreshError).toBeInstanceOf(Error);
      expect(auth.getAccessToken()).toBeNull();
      expect(auth.isLoggingOut()).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/sign-in', { replaceUrl: true });
    } finally {
      vi.useRealTimers();
    }
  });

  function authenticate(accessToken: string): void {
    auth.login({ email: 'couple@example.test', password: 'secret' }).subscribe();
    const request = http.expectOne(`${apiUrl}/auth/login`);
    expect(request.request.withCredentials).toBe(true);
    request.flush({ accessToken });
    expect(auth.getAccessToken()).toBe(accessToken);
  }
});
