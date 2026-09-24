import { Group, Operation, RelationshipKind } from '../../core/catalog.service';

/** The relationship form's values. */
export interface RuleInput {
  sourceFeatureId: string;
  kind: RelationshipKind;
  targetFeatureIds: string[];
}

export function stageNew(operations: Operation[], input: RuleInput): Operation[] {
  return [...operations, { type: 'CREATE', ...input }];
}

/**
 * Stages the edit of an active relationship, replacing any change already staged for it. Changing
 * only targets is an update; changing the source or type deletes the relationship and creates a
 * new one; removing every target deletes it.
 */
export function editActive(operations: Operation[], group: Group, input: RuleInput): Operation[] {
  const others = withoutGroup(operations, group.id);

  if (input.targetFeatureIds.length === 0) {
    return [...others, { type: 'DELETE', groupId: group.id }];
  }

  const sameRule = input.sourceFeatureId === group.sourceFeatureId && input.kind === group.kind;
  if (!sameRule) {
    return [...others, { type: 'DELETE', groupId: group.id }, { type: 'CREATE', ...input }];
  }

  if (sameTargets(input.targetFeatureIds, group.targetFeatureIds)) {
    return others;
  }

  return [
    ...others,
    { type: 'UPDATE', groupId: group.id, targetFeatureIds: input.targetFeatureIds },
  ];
}

export function removeActive(operations: Operation[], group: Group): Operation[] {
  return [...withoutGroup(operations, group.id), { type: 'DELETE', groupId: group.id }];
}

/** Rewrites a pending change; a pending new relationship with no targets is dropped. */
export function replacePending(
  operations: Operation[],
  index: number,
  input: RuleInput,
): Operation[] {
  if (input.targetFeatureIds.length === 0) {
    return undo(operations, index);
  }

  return operations.map((operation, i) => (i === index ? { type: 'CREATE', ...input } : operation));
}

export function undo(operations: Operation[], index: number): Operation[] {
  return operations.filter((_, i) => i !== index);
}

function withoutGroup(operations: Operation[], groupId: string): Operation[] {
  return operations.filter(
    (operation) => operation.type === 'CREATE' || operation.groupId !== groupId,
  );
}

function sameTargets(first: string[], second: string[]): boolean {
  return first.length === second.length && first.every((id) => second.includes(id));
}
