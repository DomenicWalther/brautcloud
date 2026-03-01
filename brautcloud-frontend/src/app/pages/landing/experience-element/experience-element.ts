import { Component, input } from '@angular/core';

@Component({
  selector: 'app-experience-element',
  imports: [],
  templateUrl: './experience-element.html',
  standalone: true,
})
export class ExperienceElement {
  badge = input.required<string>();
  title = input.required<string>();
  description = input.required<string>();
}
