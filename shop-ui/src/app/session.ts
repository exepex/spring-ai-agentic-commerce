import { Injectable, signal } from '@angular/core';

/** The demo has no login: you pick which customer you are, and which person you are in operations. */
export const CUSTOMERS = ['ada@example.com', 'grace@example.com', 'alan@example.com'];
export const OPERATORS = ['ana@trailhead.example', 'ben@trailhead.example'];

const STORAGE_KEY = 'trailhead.customer';
const OPERATOR_KEY = 'trailhead.operator';

@Injectable({ providedIn: 'root' })
export class Session {
  readonly customer = signal(Session.restore(STORAGE_KEY, CUSTOMERS));
  readonly operator = signal(Session.restore(OPERATOR_KEY, OPERATORS));

  switchTo(customerEmail: string): void {
    this.customer.set(customerEmail);
    Session.remember(STORAGE_KEY, customerEmail);
  }

  switchOperator(operatorEmail: string): void {
    this.operator.set(operatorEmail);
    Session.remember(OPERATOR_KEY, operatorEmail);
  }

  private static remember(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // storage unavailable: the choice lasts for this page only
    }
  }

  private static restore(key: string, choices: string[]): string {
    try {
      const stored = localStorage.getItem(key);
      return stored && choices.includes(stored) ? stored : choices[0];
    } catch {
      return choices[0];
    }
  }
}
