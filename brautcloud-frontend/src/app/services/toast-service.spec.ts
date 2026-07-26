import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast-service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ToastService] });
    service = TestBed.inject(ToastService);
    service.clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    service.clear();
    vi.useRealTimers();
  });

  it('adds notifications and removes them when dismissed', () => {
    const id = service.show('Gallery ready.', 'success', { duration: 0 });

    expect(service.toasts()).toEqual([{ id, kind: 'success', message: 'Gallery ready.' }]);

    service.dismiss(id);

    expect(service.toasts()).toEqual([]);
  });

  it('auto-expires notifications without duplicating active messages', () => {
    const firstId = service.show('Copied.', 'success', { duration: 1_000 });
    const duplicateId = service.show('Copied.', 'success', { duration: 1_000 });

    expect(duplicateId).toBe(firstId);
    expect(service.toasts()).toHaveLength(1);

    vi.advanceTimersByTime(1_000);

    expect(service.toasts()).toEqual([]);
  });
});
