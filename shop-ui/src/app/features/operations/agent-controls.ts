import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { actionLabel, humanize, toolServer } from '../../core/labels';
import { AgentStatus } from '../../core/models';
import { Icon } from '../../shared/icon';
import { Panel } from '../../shared/panel';
import { OperationsStore } from './operations-store';

interface ToolGroup {
  server: string;
  tools: string[];
}

interface AgentCard {
  agent: AgentStatus;
  name: string;
  toolGroups: ToolGroup[];
}

/** The agents, the outside systems they reach, and each agent's kill switch. */
@Component({
  selector: 'app-agent-controls',
  imports: [Panel, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './agent-controls.html',
  styleUrl: './agent-controls.scss',
  host: { class: 'operations-columns' },
})
export class AgentControls {
  protected readonly store = inject(OperationsStore);

  protected readonly connections = computed(() => {
    const overview = this.store.agents();
    return overview
      ? [
          { name: 'Claude', connected: overview.modelConfigured, off: 'No API key' },
          { name: 'Slack', connected: overview.slackConfigured, off: 'Not configured' },
          { name: 'ServiceNow', connected: overview.servicenowConfigured, off: 'Not configured' },
        ]
      : [];
  });

  protected readonly agentCards = computed<AgentCard[]>(
    () =>
      this.store.agents()?.agents.map((agent) => ({
        agent,
        name: humanize(agent.id),
        toolGroups: groupByServer(agent.tools),
      })) ?? [],
  );

  protected setEnabled(agentId: string, event: Event): void {
    this.store.setAgentEnabled(agentId, (event.target as HTMLInputElement).checked);
  }
}

/** Tools by the MCP server that serves them, each named in words. */
function groupByServer(tools: string[]): ToolGroup[] {
  const groups = new Map<string, string[]>();
  for (const tool of tools) {
    const server = toolServer(tool);
    groups.set(server, [...(groups.get(server) ?? []), actionLabel(tool)]);
  }
  return [...groups].map(([server, names]) => ({ server, tools: names }));
}
