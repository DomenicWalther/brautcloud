import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_URL } from '../core/tokens';
import { authInterceptor } from './auth-interceptor';
import { AuthService } from './auth-service';

describe('AuthService logout', () => {
  const apiUrl = 'https://api.example.test/api';
  let localStorageState: Record<string, string>;
  let auth: AuthService;
  let http: HttpTestingController;
  let httpClient: HttpClient;
  let router: Router;

  beforeEach(() => {
    localStorageState = {};
    installLocalStorageMock();
    globalThis.localStorage.clear();
    setupTestingModule();
  });

  afterEach(() => {
    http?.verify();
    TestBed.resetTestingModule();
    globalThis.localStorage?.clear();
    vi.useRealTimers();
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
    httpClient
      .get(`${apiUrl}/protected`)
      .subscribe({ error: (error) => (protectedRequestError = error) });

    const protectedRequest = http.expectOne(`${apiUrl}/protected`);
    expect(protectedRequest.request.headers.has('Authorization')).toBe(false);
    protectedRequest.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    http.expectNone(`${apiUrl}/auth/refresh`);
    expect(protectedRequestError).toBeTruthy();
    expect(auth.getAccessToken()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledTimes(1);
  });

  it('cancels an in-flight refresh before sending logout', () => {
    authenticate('original-token');

    let refreshError: unknown;
    auth.refreshToken().subscribe({ error: (error) => (refreshError = error) });
    const refresh = http.expectOne(`${apiUrl}/auth/refresh`);

    auth.logout();

    expect(auth.getAccessToken()).toBeNull();
    expect(refresh.cancelled).toBe(true);
    expect(refreshError).toBeInstanceOf(Error);

    const logout = http.expectOne(`${apiUrl}/auth/logout`);
    expect(logout.request.withCredentials).toBe(true);
    expect(logout.request.headers.has('Authorization')).toBe(false);
    logout.flush('Logged out');

    expect(auth.getAccessToken()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/sign-in', { replaceUrl: true });
  });

  it('navigates away when the logout request hangs', async () => {
    vi.useFakeTimers();

    try {
      authenticate('access-token');

      auth.logout();
      const logout = http.expectOne(`${apiUrl}/auth/logout`);

      await vi.advanceTimersByTimeAsync(1000);

      expect(logout.cancelled).toBe(true);
      expect(auth.getAccessToken()).toBeNull();
      expect(auth.isLoggingOut()).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/sign-in', { replaceUrl: true });
    } finally {
      vi.useRealTimers();
    }
  });

  it('skips refresh on reload after explicit logout until login succeeds again', async () => {
    authenticate('access-token');

    auth.logout();

    const logout = http.expectOne(`${apiUrl}/auth/logout`);
    logout.flush('Unavailable', { status: 401, statusText: 'Unauthorized' });

    recreateService();

    await auth.initializeAuth();

    http.expectNone(`${apiUrl}/auth/refresh`);
    expect(auth.getAccessToken()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);

    authenticate('new-access-token');

    recreateService();

    const initializePromise = auth.initializeAuth();
    const refresh = http.expectOne(`${apiUrl}/auth/refresh`);
    expect(refresh.request.withCredentials).toBe(true);
    refresh.flush({ accessToken: 'restored-token' });
    await initializePromise;

    expect(auth.getAccessToken()).toBe('restored-token');
  });

  function authenticate(accessToken: string): void {
    auth.login({ email: 'couple@example.test', password: 'secret' }).subscribe();
    const request = http.expectOne(`${apiUrl}/auth/login`);
    expect(request.request.withCredentials).toBe(true);
    request.flush({ accessToken });
    expect(auth.getAccessToken()).toBe(accessToken);
  }

  function recreateService(): void {
    http.verify();
    TestBed.resetTestingModule();
    setupTestingModule();
  }

  function setupTestingModule(): void {
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
  }

  function installLocalStorageMock(): void {
    const storage = {
      clear: () => {
        localStorageState = {};
      },
      getItem: (key: string) => localStorageState[key] ?? null,
      key: (index: number) => Object.keys(localStorageState)[index] ?? null,
      removeItem: (key: string) => {
        delete localStorageState[key];
      },
      setItem: (key: string, value: string) => {
        localStorageState[key] = value;
      },
      get length() {
        return Object.keys(localStorageState).length;
      },
    } satisfies Storage;

    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: storage,
    });
  }
});
