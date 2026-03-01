import { Component } from '@angular/core';
import {LandingCtaSection} from './landing-cta-section/landing-cta-section';
import {LandingExperienceSection} from './landing-experience-section/landing-experience-section';
import {LandingHero} from './landing-hero/landing-hero';
@Component({
  selector: 'app-landing',
  imports: [LandingCtaSection, LandingExperienceSection, LandingHero],
  templateUrl: './landing.html',
  standalone: true,
})
export class Landing {}
