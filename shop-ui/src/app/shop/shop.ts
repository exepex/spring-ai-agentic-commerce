import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, ElementRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, CustomerNotification, Order, Product, Proposal } from '../api';
import { refreshWhileOpen } from '../polling';
import { Session } from '../session';

interface ChatMessage {
  from: 'customer' | 'assistant';
  text: string;
  proposals: Proposal[];
}

const TEST_CARDS = [
  { id: 'pm_card_visa', label: 'Test Visa (succeeds)' },
  { id: 'pm_card_chargeDeclined', label: 'Test card (declined)' },
];

@Component({
  selector: 'app-shop',
  imports: [FormsModule, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './shop.html',
  styleUrl: './shop.scss',
})
export class Shop {
  private readonly api = inject(Api);
  protected readonly session = inject(Session);
  protected readonly testCards = TEST_CARDS;

  protected readonly products = signal<Product[]>([]);
  protected readonly orders = signal<Order[]>([]);
  private readonly allNotifications = signal<CustomerNotification[]>([]);
  protected readonly inbox = computed(() =>
    this.allNotifications().filter((notification) => notification.customerEmail === this.session.customer()),
  );

  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly paymentMethod = signal(TEST_CARDS[0].id);
  protected readonly confirming = signal<string | null>(null);
  private conversationId = crypto.randomUUID();

  private readonly transcript = viewChild<ElementRef<HTMLElement>>('transcript');

  constructor() {
    effect(() => {
      this.session.customer();
      untracked(() => this.startNewConversation());
    });
    refreshWhileOpen(() => this.refresh(), 4000);
  }

  protected send(): void {
    const text = this.draft().trim();
    if (!text || this.sending()) {
      return;
    }
    this.messages.update((messages) => [...messages, { from: 'customer', text, proposals: [] }]);
    this.draft.set('');
    this.sending.set(true);
    this.scrollToEnd();
    // A reply that arrives after the customer switched belongs to the old conversation, so it is dropped.
    const conversation = this.conversationId;
    this.api.chat(conversation, this.session.customer(), text).subscribe({
      next: (reply) => {
        if (conversation === this.conversationId) {
          this.addAssistantMessage(reply.text, reply.proposals);
        }
      },
      error: () => {
        if (conversation === this.conversationId) {
          this.addAssistantMessage('The assistant could not be reached. Is the agent service running?', []);
        }
      },
    });
  }

  protected askAbout(product: Product): void {
    this.draft.set(`I'd like to buy the ${product.name}.`);
  }

  protected confirm(proposal: Proposal): void {
    this.confirming.set(proposal.id);
    this.api.confirmProposal(proposal.id, this.paymentMethod()).subscribe({
      next: (confirmed) => {
        this.replaceProposal(confirmed);
        this.confirming.set(null);
        this.refresh();
      },
      error: () => this.confirming.set(null),
    });
  }

  private addAssistantMessage(text: string, proposals: Proposal[]): void {
    this.messages.update((messages) => [...messages, { from: 'assistant', text, proposals }]);
    this.sending.set(false);
    this.scrollToEnd();
  }

  private replaceProposal(updated: Proposal): void {
    this.messages.update((messages) =>
      messages.map((message) => ({
        ...message,
        proposals: message.proposals.map((proposal) => (proposal.id === updated.id ? updated : proposal)),
      })),
    );
  }

  private startNewConversation(): void {
    this.conversationId = crypto.randomUUID();
    this.sending.set(false);
    this.messages.set([
      {
        from: 'assistant',
        text: `Hi! I'm the Trailhead assistant. I can find gear, put together an order for you to confirm, track a delivery or cancel an order.`,
        proposals: [],
      },
    ]);
    this.refresh();
  }

  private refresh(): void {
    this.api.products().subscribe((products) => this.products.set(products));
    this.api.orders(this.session.customer()).subscribe((orders) => this.orders.set(orders));
    this.api.notifications().subscribe((notifications) => this.allNotifications.set(notifications));
  }

  private scrollToEnd(): void {
    setTimeout(() => {
      const element = this.transcript()?.nativeElement;
      element?.scrollTo({ top: element.scrollHeight, behavior: 'smooth' });
    });
  }
}
