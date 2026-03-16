import { Component, contentChildren } from '@angular/core';
import { StepStateService } from './step-state-service';
import { StepComponent } from './step';

@Component({
  selector: 'app-multi-step-form',
  template: `<ng-content />`,
  providers: [StepStateService],
})
export class MultiStepForm {
  steps = contentChildren(StepComponent);
}
