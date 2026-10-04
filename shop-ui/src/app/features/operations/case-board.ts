import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CaseCard } from '../../shared/case-card';
import { EmptyState } from '../../shared/empty-state';
import { Panel } from '../../shared/panel';
import { OperationsStore } from './operations-store';

/** Every problem the shop opened a case for, worked as a ServiceNow incident: the open ones, then the resolved. */
@Component({
  selector: 'app-case-board',
  imports: [Panel, EmptyState, CaseCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'operations-columns' },
  template: `
    <app-panel
      heading="Open cases"
      icon="lifebuoy"
      description="Each one is a ServiceNow incident. The incident agent works it first and hands it to a team when a person is needed."
    >
      @for (supportCase of store.openCases(); track supportCase.id) {
        <app-case-card [supportCase]="supportCase" />
      } @empty {
        <app-empty-state title="No open cases" />
      }
    </app-panel>

    <app-panel heading="Recently resolved" icon="check">
      @for (supportCase of store.resolvedCases(); track supportCase.id) {
        <app-case-card [supportCase]="supportCase" />
      } @empty {
        <app-empty-state title="Nothing resolved yet" icon="clock" />
      }
    </app-panel>
  `,
})
export class CaseBoard {
  protected readonly store = inject(OperationsStore);
}
