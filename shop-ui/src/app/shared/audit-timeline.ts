import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { JAEGER_URL } from '../core/endpoints';
import { ActorType, AuditEvent } from '../core/models';
import { EmptyState } from './empty-state';
import { Icon, IconName } from './icon';
import { ActionLabelPipe, ActorNamePipe } from './label-pipes';
import { OrderLink } from './order-link';
import { StatusBadge } from './status-badge';

const ACTOR_ICONS: Record<ActorType, IconName> = { AGENT: 'bot', HUMAN: 'user', SYSTEM: 'sliders' };

type ActorFilter = 'ALL' | ActorType;

const FILTERS: { id: ActorFilter; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'AGENT', label: 'Agents' },
  { id: 'HUMAN', label: 'People' },
  { id: 'SYSTEM', label: 'Automatic' },
];

/**
 * The audit trail, for the back office: who did each step (an agent, a person, or the shop on its own), what it was,
 * how it turned out and why. Which service took an automatic step, and its distributed trace, are in its technical
 * details.
 */
@Component({
  selector: 'app-audit-timeline',
  imports: [DatePipe, Icon, StatusBadge, OrderLink, EmptyState, ActorNamePipe, ActionLabelPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-timeline.html',
  styleUrl: './audit-timeline.scss',
})
export class AuditTimeline {
  readonly events = input.required<AuditEvent[]>();
  /** Link each step to its order, for a feed that spans orders. */
  readonly showOrder = input(false);

  protected readonly jaegerUrl = JAEGER_URL;
  protected readonly actorIcons = ACTOR_ICONS;
  protected readonly filter = signal<ActorFilter>('ALL');

  protected readonly filters = computed(() =>
    FILTERS.map((filter) => ({
      ...filter,
      count:
        filter.id === 'ALL'
          ? this.events().length
          : this.events().filter((event) => event.actorType === filter.id).length,
    })),
  );

  protected readonly shownEvents = computed(() => {
    const filter = this.filter();
    return filter === 'ALL'
      ? this.events()
      : this.events().filter((event) => event.actorType === filter);
  });
}
