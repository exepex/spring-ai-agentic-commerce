import { DestroyRef, inject } from '@angular/core';

/**
 * Calls {@code refresh} every few seconds while the page is open, so agent actions show up live. Pass
 * {@code startNow = false} when something else does the first load, for example an effect on a route input.
 */
export function refreshWhileOpen(refresh: () => void, everyMillis = 3000, startNow = true): void {
  if (startNow) {
    refresh();
  }
  const timer = setInterval(refresh, everyMillis);
  inject(DestroyRef).onDestroy(() => clearInterval(timer));
}
