import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-home-stats',
  imports: [],
  templateUrl: './home-stats.html',
  styles: ``,
})
export class HomeStats {
  statInput = input.required<{ photos: string; guests: string; views: string }>();
  stats = computed(() => [
    {
      value: this.statInput().photos,
      label: 'Photos',
    },
    {
      value: this.statInput().guests,
      label: 'Guests',
    },
    {
      value: this.statInput().views,
      label: 'Views',
    },
  ]);
}
