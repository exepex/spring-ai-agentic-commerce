import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { JAEGER_URL } from '../core/endpoints';
import { actorTypeLabel } from '../core/labels';
import { ActorType, AuditEvent } from '../core/models';
import { EmptyState } from './empty-state';
import { Icon, IconName } from './icon';
import { ActionLabelPipe, ActorNamePipe } from './label-pipes';
import { OrderLink } from './order-link';
import { StatusBadge } from './status-badge';

const ACTOR_ICONS: Record<ActorType, IconName> = { AGENT: 'bot', HUMAN: 'user', SYSTEM: 'server' };

/** The audit trail: who or what did each step, how it turned out, why, and a link to its distributed trace. */
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
  protected readonly actorTypeLabel = actorTypeLabel;
}
