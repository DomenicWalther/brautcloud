import { TestBed } from '@angular/core/testing';
import { StepStateService } from './step-state-service';

describe('StepStateService', () => {
  it('preserves validation-gated forward navigation and back navigation', () => {
    const service = TestBed.runInInjectionContext(() => new StepStateService());
    service.register('couple');
    service.register('venue');

    expect(service.currentStep()).toBe('couple');
    expect(service.isFirst()).toBe(true);

    service.next(false);
    expect(service.currentStep()).toBe('couple');

    service.next(true);
    expect(service.currentStep()).toBe('venue');
    expect(service.isLast()).toBe(true);

    service.back();
    expect(service.currentStep()).toBe('couple');
  });
});
