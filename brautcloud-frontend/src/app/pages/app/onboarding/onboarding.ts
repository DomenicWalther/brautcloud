import { Component, computed, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MultiStepForm } from './multi-step-form';
import { StepComponent } from './step.components';
import { form, required, FormField } from '@angular/forms/signals';
import { FormLabel } from './form-label/form-label';

interface OnboardingModel {
  firstName: string;
  partnerFirstName: string;
  familyName: string;
  venue: string;
}

@Component({
  selector: 'app-onboarding',
  imports: [ReactiveFormsModule, MultiStepForm, StepComponent, FormField, FormLabel],
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
    required(s.firstName);
    required(s.partnerFirstName);
    required(s.familyName);
    required(s.venue);
  });

  stepOneValid = computed(
    () =>
      this.onboardingForm.firstName().valid &&
      this.onboardingForm.partnerFirstName().valid &&
      this.onboardingForm.familyName().valid,
  );

  stepTwoValid = computed(() => this.onboardingForm.venue().valid);
}
