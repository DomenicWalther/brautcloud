import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

export interface ToastOptions {
  duration?: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toasts = signal<Toast[]>([]);
  private nextId = 0;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  readonly toasts = this._toasts.asReadonly();

  show(message: string, kind: ToastKind = 'info', options: ToastOptions = {}): number {
    const existing = this._toasts().find(
      (toast) => toast.message === message && toast.kind === kind,
    );
    if (existing) {
      return existing.id;
    }

    const id = ++this.nextId;
    this._toasts.update((toasts) => [...toasts, { id, kind, message }]);
    const duration = options.duration ?? 5_000;
    if (duration > 0) {
      this.timers.set(
        id,
        setTimeout(() => this.dismiss(id), duration),
      );
    }
    return id;
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this._toasts.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  clear(): void {
    for (const id of this.timers.keys()) {
      this.dismiss(id);
    }
    this._toasts.set([]);
  }
}
