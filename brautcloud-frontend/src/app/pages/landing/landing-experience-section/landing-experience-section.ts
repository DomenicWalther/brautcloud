import {Component} from '@angular/core';
import {ExperienceElement} from '../experience-element/experience-element';

@Component({
  selector: 'app-landing-experience-section',
  imports: [ExperienceElement],
  templateUrl: './landing-experience-section.html',
  standalone: true,
})
export class LandingExperienceSection {

}
