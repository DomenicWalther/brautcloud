import {
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
  Signal,
  WritableSignal,
} from '@angular/core';
import { StepStateService } from './step-state-service';

@Component({
  selector: 'app-step',
  templateUrl: './step.html',
})
export class StepComponent implements OnInit {
  label = input.required<string>();
  validate = input<boolean>(true);

  stepState = inject(StepStateService);
  active = computed(() => this.stepState.currentStep() === this.label());

  ngOnInit() {
    this.stepState.register(this.label());
  }
}
