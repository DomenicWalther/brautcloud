import { Component } from '@angular/core';
import {LandingCtaSection} from './landing-cta-section/landing-cta-section';
import {LandingExperienceSection} from './landing-experience-section/landing-experience-section';
import {LandingHero} from './landing-hero/landing-hero';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-landing',
  imports: [LandingCtaSection, LandingExperienceSection, LandingHero, RouterLink],
  templateUrl: './landing.html',
  standalone: true,
})
export class Landing {}
