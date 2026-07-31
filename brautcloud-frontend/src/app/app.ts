import { afterNextRender, Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHost } from './components/toast/toast-host';

type LenisInstance = Pick<import('lenis').default, 'raf' | 'destroy'>;
type LenisConstructor = new () => LenisInstance;
type RequestFrame = (callback: FrameRequestCallback) => number;
type CancelFrame = (handle: number) => void;

export function startLenisLoop(
  Lenis: LenisConstructor,
  requestFrame: RequestFrame = requestAnimationFrame,
  cancelFrame: CancelFrame = cancelAnimationFrame,
): () => void {
  const lenis = new Lenis();
  let frameId: number | undefined;
  let destroyed = false;

  const raf: FrameRequestCallback = (time) => {
    if (destroyed) {
      return;
    }

    lenis.raf(time);
    frameId = requestFrame(raf);
  };

  frameId = requestFrame(raf);

  return () => {
    if (destroyed) {
      return;
    }

    destroyed = true;
    if (frameId !== undefined) {
      cancelFrame(frameId);
    }
    lenis.destroy();
  };
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHost],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('brautcloud-frontend');

  constructor() {
    const destroyRef = inject(DestroyRef);
    let stopLenis: (() => void) | undefined;
    let destroyed = false;

    destroyRef.onDestroy(() => {
      destroyed = true;
      stopLenis?.();
    });

    afterNextRender(() => {
      if (
        typeof ResizeObserver === 'undefined' ||
        window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
      ) {
        return;
      }

      import('lenis').then(({ default: Lenis }) => {
        if (!destroyed) {
          stopLenis = startLenisLoop(Lenis);
        }
      });
    });
  }
}
