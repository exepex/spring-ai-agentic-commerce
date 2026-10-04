import { Injectable, signal } from '@angular/core';

/** The demo has no login: you pick which customer you are in the shop, and which person in operations. */
export const DEMO_CUSTOMERS = ['ada@example.com', 'grace@example.com', 'alan@example.com'] as const;
export const DEMO_OPERATORS = ['ana@trailhead.example', 'ben@trailhead.example'] as const;

const CUSTOMER_KEY = 'trailhead.customer';
const OPERATOR_KEY = 'trailhead.operator';

/** Who the visitor is playing, remembered across reloads. */
@Injectable({ providedIn: 'root' })
export class Session {
  readonly customer = signal(restore(CUSTOMER_KEY, DEMO_CUSTOMERS));
  readonly operator = signal(restore(OPERATOR_KEY, DEMO_OPERATORS));

  signInAs(customerEmail: string): void {
    this.customer.set(customerEmail);
    remember(CUSTOMER_KEY, customerEmail);
  }

  actAs(operatorEmail: string): void {
    this.operator.set(operatorEmail);
    remember(OPERATOR_KEY, operatorEmail);
  }
}

function remember(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage is unavailable: the choice lasts for this page only.
  }
}

function restore(key: string, choices: readonly string[]): string {
  try {
    const stored = localStorage.getItem(key);
    return stored && choices.includes(stored) ? stored : choices[0];
  } catch {
    return choices[0];
  }
}
