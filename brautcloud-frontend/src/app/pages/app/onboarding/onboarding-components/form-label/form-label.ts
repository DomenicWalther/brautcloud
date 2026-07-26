import { Component, computed, input } from '@angular/core';
import { FieldTree, FormField } from '@angular/forms/signals';

@Component({
  selector: 'app-form-label',
  imports: [FormField],
  templateUrl: './form-label.html',
  styles: ``,
  host: { class: 'onboarding-field' },
})
export class FormLabel {
  label = input.required<string>();
  placeholder = input.required<string>();
  field = input.required<FieldTree<string, string>>();
  type = input<'text' | 'date' | 'password'>('text');

  protected fieldState = computed(() => this.field()());
}
