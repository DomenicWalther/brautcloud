import { Component, input } from '@angular/core';

@Component({
  selector: 'app-experience-element',
  imports: [],
  templateUrl: './experience-element.html',
  styleUrl: './experience-element.css',
  standalone: true,
})
export class ExperienceElement {
  step = input.required<string>();
  badge = input.required<string>();
  title = input.required<string>();
  description = input.required<string>();
}
