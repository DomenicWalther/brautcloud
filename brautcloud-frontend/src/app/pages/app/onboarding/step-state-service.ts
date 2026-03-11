import { computed, Injectable, signal } from '@angular/core';

@Injectable()
export class StepStateService {
  private steps = signal<string[]>([]);
  currentStep = signal<string>('');

  register(label: string) {
    this.steps.update((s) => [...s, label]);
    if (this.steps().length === 1) this.currentStep.set(label);
  }

  next(canProceed: boolean) {
    if (!canProceed) return;
    const all = this.steps();
    const idx = all.indexOf(this.currentStep());
    if (idx < all.length - 1) this.currentStep.set(all[idx + 1]);
  }

  back() {
    const all = this.steps();
    const idx = all.indexOf(this.currentStep());
    if (idx > 0) this.currentStep.set(all[idx - 1]);
  }

  isFirst = computed(() => this.currentStep() === this.steps()[0]);
  isLast = computed(() => {
    const all = this.steps();
    return this.currentStep() === all[all.length - 1];
  });
}
