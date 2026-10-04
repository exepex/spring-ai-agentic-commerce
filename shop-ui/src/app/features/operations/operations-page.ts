import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { pollWhileActive } from '../../core/polling';
import { DEMO_OPERATORS, Session } from '../../core/session';
import { AuditTimeline } from '../../shared/audit-timeline';
import { Icon, IconName } from '../../shared/icon';
import { Panel } from '../../shared/panel';
import { AgentControls } from './agent-controls';
import { CaseBoard } from './case-board';
import { DemoControls } from './demo-controls';
import { OperationsStore } from './operations-store';
import { RefundQueue } from './refund-queue';
import { ShippingDesk } from './shipping-desk';

type OperationsTab = 'refunds' | 'cases' | 'shipping' | 'agents' | 'activity' | 'demo';

interface TabLink {
  id: OperationsTab;
  label: string;
  icon: IconName;
  /** How many items wait on this tab, when it has a queue. */
  count?: () => number;
}

interface KeyFigure {
  label: string;
  value: number;
  tab: OperationsTab;
  /** Highlight the figure when it is not zero: someone needs to act. */
  urgent: boolean;
}

/** The operations team's console: approvals, failed refunds, cases, shipping, the agents' kill switches, and demo controls. */
@Component({
  selector: 'app-operations-page',
  imports: [
    RouterLink,
    Icon,
    Panel,
    AuditTimeline,
    RefundQueue,
    CaseBoard,
    ShippingDesk,
    AgentControls,
    DemoControls,
  ],
  providers: [OperationsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './operations-page.html',
  styleUrl: './operations-page.scss',
})
export class OperationsPage {
  /** The open tab, from the {@code tab} query parameter so it survives a reload and can be linked to. */
  readonly tab = input<string>();

  protected readonly store = inject(OperationsStore);
  protected readonly session = inject(Session);
  protected readonly operators = DEMO_OPERATORS;

  protected readonly tabs: TabLink[] = [
    {
      id: 'refunds',
      label: 'Refunds',
      icon: 'refund',
      count: () => this.store.pendingRefunds().length + this.store.failedRefunds().length,
    },
    { id: 'cases', label: 'Cases', icon: 'lifebuoy', count: () => this.store.openCases().length },
    {
      id: 'shipping',
      label: 'Shipping',
      icon: 'truck',
      count: () => this.store.shipments().length,
    },
    { id: 'agents', label: 'Agents', icon: 'bot' },
    { id: 'activity', label: 'Activity', icon: 'activity' },
    { id: 'demo', label: 'Demo controls', icon: 'sliders' },
  ];

  protected readonly activeTab = computed<OperationsTab>(
    () => this.tabs.find((tab) => tab.id === this.tab())?.id ?? 'refunds',
  );

  protected readonly keyFigures = computed<KeyFigure[]>(() => [
    {
      label: 'Refunds to approve',
      value: this.store.pendingRefunds().length,
      tab: 'refunds',
      urgent: true,
    },
    {
      label: 'Failed refunds',
      value: this.store.failedRefunds().length,
      tab: 'refunds',
      urgent: true,
    },
    { label: 'Open cases', value: this.store.openCases().length, tab: 'cases', urgent: false },
    {
      label: 'Parcels to handle',
      value: this.store.shipments().length,
      tab: 'shipping',
      urgent: false,
    },
  ]);

  constructor() {
    pollWhileActive(() => this.store.refresh());
  }
}
