import { Injectable, signal } from '@angular/core';

/** The demo has no login: you pick which customer you are, and which person approves things in operations. */
export const CUSTOMERS = ['ada@example.com', 'grace@example.com', 'alan@example.com'];
export const OPERATOR = 'ops@trailhead.example';

const STORAGE_KEY = 'trailhead.customer';

@Injectable({ providedIn: 'root' })
export class Session {
  readonly customer = signal(Session.restore());

  switchTo(customerEmail: string): void {
    this.customer.set(customerEmail);
    try {
      localStorage.setItem(STORAGE_KEY, customerEmail);
    } catch {
      // storage unavailable: the choice lasts for this page only
    }
  }

  private static restore(): string {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored && CUSTOMERS.includes(stored) ? stored : CUSTOMERS[0];
    } catch {
      return CUSTOMERS[0];
    }
  }
}
