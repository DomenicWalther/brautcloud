import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-form-button',
  imports: [],
  templateUrl: './form-button.html',
  styles: ``,
})
export class FormButton {
  buttonLabel = input.required<string>();
  isDisabled = input.required<boolean>();
  buttonEvent = output<void>();

  buttonClick() {
    this.buttonEvent.emit();
  }
}
