import { NgOptimizedImage } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-landing-hero',
  imports: [NgOptimizedImage, RouterLink],
  templateUrl: './landing-hero.html',
  styleUrl: './landing-hero.css',
  standalone: true,
})
export class LandingHero {}
