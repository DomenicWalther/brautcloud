import {Component} from '@angular/core';
import {NgOptimizedImage} from '@angular/common';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-landing-hero',
  imports: [
    NgOptimizedImage,
    RouterLink
  ],
  templateUrl: './landing-hero.html',
  standalone: true,
})
export class LandingHero {

}
