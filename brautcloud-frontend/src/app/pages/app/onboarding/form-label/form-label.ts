import { Component, input, Input } from '@angular/core';
import { form, required, FormField, FieldTree } from '@angular/forms/signals';

@Component({
  selector: 'app-form-label',
  imports: [FormField],
  templateUrl: './form-label.html',
  styles: ``,
  host: { class: 'flex-1' },
})
export class FormLabel {
  label = input.required<string>();
  placeholder = input.required<string>();
  field = input.required<any>();
}
