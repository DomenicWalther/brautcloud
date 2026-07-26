import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_URL } from '../core/tokens';
import { OnboardingDto } from '../core/models/onboarding.dto';
import { OnboardingService } from './onboarding-service';

describe('OnboardingService', () => {
  let service: OnboardingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        OnboardingService,
        { provide: API_URL, useValue: 'http://api.test/api' },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(OnboardingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('serializes date input using backend LocalDateTime convention', () => {
    const data: OnboardingDto = {
      firstName: 'Sophie',
      partnerFirstName: 'Marcus',
      familyName: 'Müller-Weber',
      venue: 'Eichenfürst',
      date: '2030-06-15',
    };

    service.submitOnboarding(data).subscribe();

    const request = http.expectOne('http://api.test/api/onboarding');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({
      ...data,
      date: '2030-06-15T00:00:00',
    });
    request.flush({ onboardingComplete: true, event: null });
  });
});
