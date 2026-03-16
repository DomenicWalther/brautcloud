import { Component, computed, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MultiStepForm } from './multi-step-form';
import { StepComponent } from './step';
import { form, required } from '@angular/forms/signals';
import { FormLabel } from './onboarding-components/form-label/form-label';
import { StepHeader } from './onboarding-components/step-header/step-header';

interface OnboardingModel {
  firstName: string;
  partnerFirstName: string;
  familyName: string;
  venue: string;
}

@Component({
  selector: 'app-onboarding',
  imports: [ReactiveFormsModule, MultiStepForm, StepComponent, FormLabel, StepHeader],
  templateUrl: './onboarding.html',
  styles: ``,
})
export class Onboarding {
  model = signal<OnboardingModel>({
    firstName: '',
    partnerFirstName: '',
    familyName: '',
    venue: '',
  });

  onboardingForm = form(this.model, (s) => {
    required(s.firstName, { message: 'Please enter your first name' });
    required(s.partnerFirstName, { message: 'Please enter your partner first name' });
    required(s.familyName, { message: 'Please enter your family name' });
    required(s.venue, { message: 'Please enter your Venues name' });
  });

  stepOneValid = computed(
    () =>
      this.onboardingForm.firstName().valid() &&
      this.onboardingForm.partnerFirstName().valid() &&
      this.onboardingForm.familyName().valid(),
  );

  stepTwoValid = computed(() => this.onboardingForm.venue().valid());
}
