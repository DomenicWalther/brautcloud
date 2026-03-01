import { Component, input } from '@angular/core';

@Component({
  selector: 'app-button',
  imports: [],
  templateUrl: './button.html',
  styles: ``,
})
export class Button {
  label = input.required<string>();
}
