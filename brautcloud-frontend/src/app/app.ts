import { afterNextRender, Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('brautcloud-frontend');

  constructor() {
    afterNextRender(() => {
      if (
        typeof ResizeObserver === 'undefined' ||
        window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
      ) {
        return;
      }

      import('lenis').then(({ default: Lenis }) => {
        const lenis = new Lenis({ duration: 1.2 });

        const raf = (time: number) => {
          lenis.raf(time);
          requestAnimationFrame(raf);
        };
        requestAnimationFrame(raf);
      });
    });
  }
}
