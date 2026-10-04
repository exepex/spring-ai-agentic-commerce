import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { CommerceApi, refusalReason } from '../../core/commerce-api';
import { Proposal } from '../../core/models';
import { Session } from '../../core/session';
import { Icon } from '../../shared/icon';
import { ProposalCard } from './proposal-card';

interface ChatMessage {
  id: number;
  author: 'customer' | 'assistant';
  text: string;
  proposals: Proposal[];
}

/** How often to look again at an order that is still being placed, perhaps from another tab. */
const CONFIRMING_RECHECK_MS = 2000;

const GREETING =
  "Hi! I'm the Trailhead assistant. I can find gear, put together an order for you to confirm, " +
  'track a delivery or cancel an order.';

/** Things a customer might ask first, offered until they start the conversation. */
const SUGGESTIONS = [
  'I need a headlamp for night hikes',
  'Where is my latest order?',
  'Cancel my latest order',
];

/**
 * The shopping assistant's chat. Each signed-in customer gets a fresh conversation; a reply that arrives after they
 * switched belongs to the old one and is dropped.
 */
@Component({
  selector: 'app-assistant-chat',
  imports: [Icon, ProposalCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './assistant-chat.html',
  styleUrl: './assistant-chat.scss',
})
export class AssistantChat {
  private readonly api = inject(CommerceApi);
  private readonly session = inject(Session);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly suggestions = SUGGESTIONS;
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly awaitingReply = signal(false);
  protected readonly payingProposalId = signal<string | null>(null);

  private readonly transcript = viewChild.required<ElementRef<HTMLElement>>('transcript');
  private readonly composer = viewChild.required<ElementRef<HTMLInputElement>>('composer');
  private conversationId = crypto.randomUUID();
  private nextMessageId = 0;
  private destroyed = false;

  constructor() {
    effect(() => {
      this.session.customer();
      untracked(() => this.startConversation());
    });
    this.destroyRef.onDestroy(() => (this.destroyed = true));
  }

  /** Puts a question in the composer for the customer to send or edit, and brings the chat into view. */
  prefill(text: string): void {
    this.draft.set(text);
    const composer = this.composer().nativeElement;
    composer.scrollIntoView({ behavior: 'smooth', block: 'center' });
    composer.focus({ preventScroll: true });
  }

  protected send(text = this.draft()): void {
    const message = text.trim();
    if (!message || this.awaitingReply()) {
      return;
    }
    this.addMessage('customer', message);
    this.draft.set('');
    this.awaitingReply.set(true);
    const conversation = this.conversationId;
    this.api.chat(conversation, this.session.customer(), message).subscribe({
      next: (reply) => this.receiveReply(conversation, reply.text, reply.proposals),
      error: () =>
        this.receiveReply(
          conversation,
          'The assistant could not be reached. Is the agent service running?',
        ),
    });
  }

  protected pay(proposal: Proposal, paymentMethod: string): void {
    this.payingProposalId.set(proposal.id);
    // As with replies, the outcome belongs to the conversation it started in; after a switch it is dropped.
    const conversation = this.conversationId;
    this.api.confirmProposal(proposal.id, paymentMethod).subscribe({
      next: (latest) => {
        if (conversation !== this.conversationId) {
          return;
        }
        this.payingProposalId.set(null);
        this.replaceProposal(latest);
        this.followWhileConfirming(latest);
      },
      error: (failure) => {
        if (conversation !== this.conversationId) {
          return;
        }
        this.payingProposalId.set(null);
        this.addMessage(
          'assistant',
          refusalReason(failure, 'The order could not be placed. Please try again.'),
        );
      },
    });
  }

  /** Another tab may be placing this order; check again until it is confirmed or failed. */
  private followWhileConfirming(proposal: Proposal): void {
    if (proposal.status !== 'CONFIRMING') {
      return;
    }
    const conversation = this.conversationId;
    setTimeout(() => {
      if (this.destroyed || conversation !== this.conversationId) {
        return;
      }
      this.api.proposal(proposal.id).subscribe((latest) => {
        this.replaceProposal(latest);
        this.followWhileConfirming(latest);
      });
    }, CONFIRMING_RECHECK_MS);
  }

  private receiveReply(conversation: string, text: string, proposals: Proposal[] = []): void {
    if (conversation !== this.conversationId) {
      return;
    }
    this.awaitingReply.set(false);
    this.addMessage('assistant', text, proposals);
  }

  private addMessage(
    author: ChatMessage['author'],
    text: string,
    proposals: Proposal[] = [],
  ): void {
    const message = { id: this.nextMessageId++, author, text, proposals };
    this.messages.update((messages) => [...messages, message]);
    this.scrollToLatest();
  }

  private replaceProposal(updated: Proposal): void {
    this.messages.update((messages) =>
      messages.map((message) => ({
        ...message,
        proposals: message.proposals.map((proposal) =>
          proposal.id === updated.id ? updated : proposal,
        ),
      })),
    );
  }

  private startConversation(): void {
    this.conversationId = crypto.randomUUID();
    this.awaitingReply.set(false);
    this.payingProposalId.set(null);
    this.messages.set([]);
    this.addMessage('assistant', GREETING);
  }

  private scrollToLatest(): void {
    setTimeout(() => {
      const transcript = this.transcript().nativeElement;
      transcript.scrollTo({ top: transcript.scrollHeight, behavior: 'smooth' });
    });
  }
}
