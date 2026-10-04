import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CarrierOutcome, Shipment } from '../../core/models';
import { EmptyState } from '../../shared/empty-state';
import { Icon } from '../../shared/icon';
import { OrderLink } from '../../shared/order-link';
import { Panel } from '../../shared/panel';
import { StatusBadge } from '../../shared/status-badge';
import { OperationsStore } from './operations-store';

/** The warehouse and the carrier, played by hand: ship paid orders, then say what happened to each parcel. */
@Component({
  selector: 'app-shipping-desk',
  imports: [Panel, EmptyState, Icon, OrderLink, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shipping-desk.html',
  host: { class: 'operations-columns' },
})
export class ShippingDesk {
  protected readonly store = inject(OperationsStore);
  protected readonly toShip = computed(() =>
    this.store.shipments().filter((shipment) => shipment.status === 'PREPARING'),
  );
  protected readonly inTransit = computed(() =>
    this.store.shipments().filter((shipment) => shipment.status === 'SHIPPED'),
  );
  /** What went wrong with each parcel, as the operator typed it, by shipment. */
  private readonly problems = signal<Record<string, string>>({});

  protected problemFor(shipmentId: string): string {
    return this.problems()[shipmentId] ?? '';
  }

  protected setProblem(shipmentId: string, problem: string): void {
    this.problems.update((problems) => ({ ...problems, [shipmentId]: problem }));
  }

  protected report(shipment: Shipment, outcome: CarrierOutcome): void {
    this.store.reportCarrierOutcome(shipment, outcome, this.problemFor(shipment.id));
  }
}
