import { Pipe, PipeTransform } from '@angular/core';
import { actionLabel, actorName, caseTypeLabel } from '../core/labels';
import { CaseType } from '../core/models';

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
  transform(action: string): string {
    return actionLabel(action);
  }
}

/** "incident-agent" as "Incident agent"; people keep their email. */
@Pipe({ name: 'actorName' })
export class ActorNamePipe implements PipeTransform {
  transform(actor: string): string {
    return actorName(actor);
  }
}
