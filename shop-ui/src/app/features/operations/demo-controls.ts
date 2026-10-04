import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Icon } from '../../shared/icon';
import { Panel } from '../../shared/panel';
import { OperationsStore } from './operations-store';

/** Ways to make things go wrong on purpose, to watch the agents and the governance deal with it. */
@Component({
  selector: 'app-demo-controls',
  imports: [Panel, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './demo-controls.html',
  host: { class: 'operations-columns' },
  styles: `
    .write-off {
      gap: var(--space-4);
    }
    .write-off-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 3fr);
      gap: var(--space-3);
    }
  `,
})
export class DemoControls {
  protected readonly store = inject(OperationsStore);

  private readonly chosenProductId = signal<string | null>(null);
  /** The product to write off: the one chosen, or the first until one is. */
  protected readonly productId = computed(
    () => this.chosenProductId() ?? this.store.products()[0]?.id ?? '',
  );
  protected readonly selectedProduct = computed(() =>
    this.store.products().find((product) => product.id === this.productId()),
  );
  protected readonly quantity = signal(1);
  protected readonly reason = signal('damaged in warehouse');

  protected chooseProduct(productId: string): void {
    this.chosenProductId.set(productId);
  }

  protected setQuantity(value: string): void {
    this.quantity.set(Math.max(1, Math.floor(Number(value)) || 1));
  }

  protected writeOff(): void {
    if (this.productId()) {
      this.store.writeOff(this.productId(), this.quantity(), this.reason().trim());
    }
  }

  protected setPaymentOutage(event: Event): void {
    this.store.setPaymentOutage((event.target as HTMLInputElement).checked);
  }
}
