import { Component, computed, inject, input, OnInit, output } from '@angular/core';
import { StepStateService } from './step-state-service';
import { FormButton } from './onboarding-components/form-button/form-button';

@Component({
  selector: 'app-step',
  templateUrl: './step.html',
  imports: [FormButton],
  host: { class: 'onboarding-step' },
})
export class StepComponent implements OnInit {
  stepRegisterLabel = input.required<string>();
  isStepInputValid = input<boolean>(true);
  isSubmitting = input<boolean>(false);
  complete = output<void>();

  stepStateService = inject(StepStateService);
  isActiveStep = computed(() => this.stepStateService.currentStep() === this.stepRegisterLabel());

  ngOnInit() {
    this.stepStateService.register(this.stepRegisterLabel());
  }

  submit(): void {
    if (this.isStepInputValid() && !this.isSubmitting()) {
      this.complete.emit();
    }
  }
}
