import { DestroyRef, inject } from '@angular/core';

export interface PollingOptions {
  /** How often to refresh. */
  intervalMs?: number;
  /** Refresh right away too; turn off when something else does the first load, such as an effect on a route input. */
  immediate?: boolean;
}

/**
 * Calls {@code refresh} every few seconds while the component that calls this is alive, so what agents and other
 * people do shows up without a reload. Call it from a constructor or field initialiser.
 */
export function pollWhileActive(
  refresh: () => void,
  { intervalMs = 3000, immediate = true }: PollingOptions = {},
): void {
  if (immediate) {
    refresh();
  }
  const timer = setInterval(refresh, intervalMs);
  inject(DestroyRef).onDestroy(() => clearInterval(timer));
}
