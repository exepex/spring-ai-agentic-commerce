import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuditEvent, JAEGER_URL } from './api';

/** The audit trail: who or what did each step, how it turned out, why, and a link to its distributed trace. */
@Component({
  selector: 'app-timeline',
  imports: [DatePipe, RouterLink],
  template: `
    <ol class="timeline">
      @for (event of events(); track event.id) {
        <li>
          <time [attr.datetime]="event.occurredAt">{{ event.occurredAt | date: 'HH:mm:ss' }}<br />{{ event.occurredAt | date: 'MMM d' }}</time>
          <div>
            <div class="who">
              <span class="chip {{ event.actorType }}">{{ event.actorType }}</span>
              <strong>{{ event.actor }}</strong>
              <span class="mono muted">{{ event.action }}</span>
              <span class="chip {{ event.outcome }}">{{ event.outcome }}</span>
              @if (showOrder() && event.orderId) {
                <a class="small" [routerLink]="['/orders', event.orderId]">order {{ event.orderId.slice(0, 8) }}</a>
              }
              @if (event.traceId) {
                <a class="small" [href]="jaegerUrl + '/trace/' + event.traceId" target="_blank" rel="noopener">trace</a>
              }
            </div>
            <div>{{ event.summary }}</div>
            @if (event.details) {
              <details>
                <summary class="muted small">Details</summary>
                <pre>{{ event.details }}</pre>
              </details>
            }
          </div>
        </li>
      } @empty {
        <li class="empty">Nothing yet.</li>
      }
    </ol>
  `,
})
export class Timeline {
  readonly events = input.required<AuditEvent[]>();
  readonly showOrder = input(false);
  protected readonly jaegerUrl = JAEGER_URL;
}
