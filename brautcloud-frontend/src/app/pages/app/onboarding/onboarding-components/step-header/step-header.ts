import { Component, input } from '@angular/core';

@Component({
  selector: 'app-step-header',
  imports: [],
  templateUrl: './step-header.html',
  styles: ``,
})
export class StepHeader {
  stepCount = input.required<string>();
  description = input.required<string>();
}
