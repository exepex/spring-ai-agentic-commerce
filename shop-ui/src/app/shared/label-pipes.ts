import { Pipe, PipeTransform } from '@angular/core';
import { actionLabel, actorName, byline, caseTypeLabel } from '../core/labels';
import { ActorType, AuditEvent, CaseType } from '../core/models';

/** "PARCEL_LOST" as "Lost parcel". */
@Pipe({ name: 'caseType' })
export class CaseTypePipe implements PipeTransform {
  transform(type: CaseType): string {
    return caseTypeLabel(type);
  }
}

/** "servicenow:assign_to_team" as "Assign to team". */
@Pipe({ name: 'actionLabel' })
export class ActionLabelPipe implements PipeTransform {
  transform(action: string, outcome?: AuditEvent['outcome']): string {
    return actionLabel(action, outcome);
  }
}

/** "incident-agent" as "Incident agent", a service as "Automatic"; people keep their email. */
@Pipe({ name: 'actorName' })
export class ActorNamePipe implements PipeTransform {
  transform(actor: string, type?: ActorType): string {
    return actorName(actor, type);
  }
}

/** "Sent automatically" or "Sent by Incident agent". */
@Pipe({ name: 'byline' })
export class BylinePipe implements PipeTransform {
  transform(actor: string, verb: string): string {
    return byline(verb, actor);
  }
}
