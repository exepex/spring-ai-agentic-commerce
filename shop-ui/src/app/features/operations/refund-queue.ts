import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RefundDecision, RefundRequest } from '../../core/models';
import { EmptyState } from '../../shared/empty-state';
import { Icon } from '../../shared/icon';
import { ActorNamePipe } from '../../shared/label-pipes';
import { OrderLink } from '../../shared/order-link';
import { Panel } from '../../shared/panel';
import { OperationsStore } from './operations-store';

/** Refunds above the approval limit, waiting for a person, and refunds that failed and can be retried. */
@Component({
  selector: 'app-refund-queue',
  imports: [CurrencyPipe, DatePipe, Panel, EmptyState, Icon, OrderLink, ActorNamePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './refund-queue.html',
  host: { class: 'operations-columns' },
})
export class RefundQueue {
  protected readonly store = inject(OperationsStore);
  /** What the operator typed as the reason for each decision, by refund request. */
  private readonly notes = signal<Record<string, string>>({});

  protected noteFor(refundId: string): string {
    return this.notes()[refundId] ?? '';
  }

  protected setNote(refundId: string, note: string): void {
    this.notes.update((notes) => ({ ...notes, [refundId]: note }));
  }

  protected decide(refund: RefundRequest, decision: RefundDecision): void {
    this.store.decideRefund(refund, decision, this.noteFor(refund.id));
  }
}
