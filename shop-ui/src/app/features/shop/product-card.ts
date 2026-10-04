import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Product } from '../../core/models';
import { Icon, IconName } from '../../shared/icon';

/** Product pictures by SKU prefix; anything else is a parcel. */
const PRODUCT_ICONS: [prefix: string, icon: IconName][] = [
  ['BOTTLE', 'bottle'],
  ['HEADLAMP', 'headlamp'],
  ['RAIN-JACKET', 'jacket'],
  ['RUN-SHOE', 'shoe'],
];

/** Fewer than this many left reads as low stock. */
const LOW_STOCK = 3;

type StockLevel = 'in-stock' | 'low' | 'out';

/** One product in the catalog, with a way to ask the assistant about it. */
@Component({
  selector: 'app-product-card',
  imports: [CurrencyPipe, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
  readonly product = input.required<Product>();
  /** The customer wants the assistant's help with this product. */
  readonly ask = output<Product>();

  protected readonly icon = computed<IconName>(
    () => PRODUCT_ICONS.find(([prefix]) => this.product().sku.startsWith(prefix))?.[1] ?? 'package',
  );

  protected readonly stockLevel = computed<StockLevel>(() => {
    const available = this.product().available;
    return available <= 0 ? 'out' : available < LOW_STOCK ? 'low' : 'in-stock';
  });

  protected readonly stockText = computed(() => {
    const available = this.product().available;
    switch (this.stockLevel()) {
      case 'out':
        return 'Out of stock';
      case 'low':
        return `Only ${available} left`;
      default:
        return `${available} in stock`;
    }
  });
}
