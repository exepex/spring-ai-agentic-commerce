import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { CommerceApi } from '../../core/commerce-api';
import { Product } from '../../core/models';
import { pollWhileActive } from '../../core/polling';
import { AssistantChat } from './assistant-chat';
import { ProductCard } from './product-card';

/** The shop: the catalog, and the assistant that turns a conversation into an order for the customer to confirm. */
@Component({
  selector: 'app-shop-page',
  imports: [ProductCard, AssistantChat],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="page">
      <header class="page-header">
        <div>
          <h1>Outdoor gear</h1>
          <p class="lead">
            Pick something you like, or tell the assistant what you need. It puts the order
            together; you confirm and pay.
          </p>
        </div>
      </header>

      <div class="shop-layout">
        <section class="catalog" aria-label="Products">
          @for (product of products(); track product.id) {
            <app-product-card [product]="product" (ask)="askAbout($event)" />
          }
        </section>
        <aside class="assistant">
          <app-assistant-chat />
        </aside>
      </div>
    </main>
  `,
  styles: `
    .shop-layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(340px, 400px);
      align-items: start;
      gap: var(--space-5);
    }
    .catalog {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: var(--space-4);
    }
    .assistant {
      position: sticky;
      top: calc(var(--header-height) + var(--space-5));
    }
    @media (max-width: 1024px) {
      .shop-layout {
        grid-template-columns: minmax(0, 1fr);
      }
      .assistant {
        position: static;
      }
    }
  `,
})
export class ShopPage {
  private readonly api = inject(CommerceApi);
  protected readonly products = signal<Product[]>([]);
  private readonly assistant = viewChild.required(AssistantChat);

  constructor() {
    pollWhileActive(
      () => this.api.products().subscribe((products) => this.products.set(products)),
      {
        intervalMs: 5000,
      },
    );
  }

  protected askAbout(product: Product): void {
    this.assistant().prefill(`I'd like to buy the ${product.name}.`);
  }
}
