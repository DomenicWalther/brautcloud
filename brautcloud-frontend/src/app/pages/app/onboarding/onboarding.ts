import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { form, required } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OnboardingDto } from '../../../core/models/onboarding.dto';
import { AuthRoutingService } from '../../../services/auth-routing-service';
import { AuthService } from '../../../services/auth-service';
import { OnboardingService } from '../../../services/onboarding-service';
import { MultiStepForm } from './multi-step-form';
import { FormLabel } from './onboarding-components/form-label/form-label';
import { StepHeader } from './onboarding-components/step-header/step-header';
import { StepComponent } from './step';

@Component({
  selector: 'app-onboarding',
  imports: [MultiStepForm, StepComponent, FormLabel, StepHeader, RouterLink],
  templateUrl: './onboarding.html',
  styles: ``,
})
export class Onboarding {
  private readonly onboardingService = inject(OnboardingService);
  private readonly authService = inject(AuthService);
  private readonly authRouting = inject(AuthRoutingService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly submissionError = signal<string | null>(null);

  readonly model = signal<OnboardingDto>({
    firstName: '',
    partnerFirstName: '',
    familyName: '',
    venue: '',
  });

  readonly onboardingForm = form(this.model, (schema) => {
    required(schema.firstName, { message: 'Please enter your first name' });
    required(schema.partnerFirstName, { message: 'Please enter your partner first name' });
    required(schema.familyName, { message: 'Please enter your family name' });
    required(schema.venue, { message: 'Please enter your Venues name' });
  });

  readonly stepOneValid = computed(
    () =>
      this.onboardingForm.firstName().valid() &&
      this.onboardingForm.partnerFirstName().valid() &&
      this.onboardingForm.familyName().valid(),
  );

  readonly stepTwoValid = computed(() => this.onboardingForm.venue().valid());
  readonly stepThreeValid = computed(() => this.stepOneValid() && this.stepTwoValid());

  submit(): void {
    if (this.onboardingForm().invalid() || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.submissionError.set(null);
    this.onboardingService.submitOnboarding(this.model()).subscribe({
      next: () => {
        this.authService.markOnboardingComplete();
        const destination = this.authRouting.destinationAfterOnboarding(
          this.route.snapshot.queryParamMap.get('returnUrl'),
        );
        void this.router.navigateByUrl(destination);
      },
      error: (error: HttpErrorResponse) => {
        this.submissionError.set(
          error.error?.message ?? 'We could not create your gallery. Please try again.',
        );
        this.submitting.set(false);
      },
    });
  }
}
