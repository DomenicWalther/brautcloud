import { Component, computed, inject, input, OnInit } from '@angular/core';
import { StepStateService } from './step-state-service';
import { FormButton } from './onboarding-components/form-button/form-button';

@Component({
  selector: 'app-step',
  templateUrl: './step.html',
  imports: [FormButton],
})
export class StepComponent implements OnInit {
  stepRegisterLabel = input.required<string>();
  isStepInputValid = input<boolean>(true);

  stepStateService = inject(StepStateService);
  isActiveStep = computed(() => this.stepStateService.currentStep() === this.stepRegisterLabel());

  ngOnInit() {
    this.stepStateService.register(this.stepRegisterLabel());
  }
}
