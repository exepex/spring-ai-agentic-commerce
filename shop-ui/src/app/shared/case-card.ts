import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SupportCase } from '../core/models';
import { Icon } from './icon';
import { BylinePipe, CaseTypePipe } from './label-pipes';
import { OrderLink } from './order-link';
import { StatusBadge } from './status-badge';

/** One case: what went wrong, its ServiceNow incident, and who has it now. */
@Component({
  selector: 'app-case-card',
  imports: [DatePipe, Icon, OrderLink, StatusBadge, BylinePipe, CaseTypePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="item">
      <div class="split">
        <strong>{{ supportCase().type | caseType }}</strong>
        <app-status-badge [status]="supportCase().status" />
      </div>
      <p>{{ supportCase().description }}</p>
      <div class="split small">
        <span class="cluster muted">
          @if (supportCase().incidentUrl) {
            <a class="mono" [href]="supportCase().incidentUrl" target="_blank" rel="noopener"
              >{{ supportCase().incidentNumber }} <app-icon name="external" [size]="12"
            /></a>
          } @else if (supportCase().incidentNumber) {
            <span class="mono">{{ supportCase().incidentNumber }}</span>
          }
          <span>
            @switch (supportCase().status) {
              @case ('PENDING') {
                Waiting to reach ServiceNow
              }
              @case ('WITH_AGENT') {
                With the incident agent
              }
              @case ('WITH_TEAM') {
                With <strong>{{ supportCase().assignmentGroup ?? 'a team' }}</strong>
              }
              @case ('RESOLVED') {
                Resolved
              }
            }
          </span>
        </span>
        @if (showOrder() && supportCase().orderId; as orderId) {
          <app-order-link [orderId]="orderId" />
        }
      </div>
      <p class="muted small">
        {{ supportCase().raisedBy | byline: 'Opened' }} ·
        {{ supportCase().createdAt | date: 'MMM d, HH:mm' }}
      </p>
    </article>
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: var(--space-3);
    }
    :host(:last-child) {
      margin-bottom: 0;
    }
    a {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  `,
})
export class CaseCard {
  readonly supportCase = input.required<SupportCase>();
  /** Link to the case's order, for lists that span orders. */
  readonly showOrder = input(true);
}
