import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Proposal } from '../../core/models';
import { Icon } from '../../shared/icon';
import { StatusBadge } from '../../shared/status-badge';

/** The payment service's test cards: one always succeeds, one is always declined. */
export const TEST_CARDS = [
  { paymentMethod: 'pm_card_visa', label: 'Test Visa (succeeds)' },
  { paymentMethod: 'pm_card_chargeDeclined', label: 'Test card (declined)' },
] as const;

/** An order the assistant put together. Only the customer can place it, by choosing a card and paying. */
@Component({
  selector: 'app-proposal-card',
  imports: [CurrencyPipe, RouterLink, Icon, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './proposal-card.html',
  styleUrl: './proposal-card.scss',
})
export class ProposalCard {
  readonly proposal = input.required<Proposal>();
  /** The confirmation is on its way to the server. */
  readonly paying = input(false);
  /** The customer confirmed, paying with this payment method. */
  readonly pay = output<string>();

  protected readonly testCards = TEST_CARDS;
  protected readonly paymentMethod = signal<string>(TEST_CARDS[0].paymentMethod);
}
