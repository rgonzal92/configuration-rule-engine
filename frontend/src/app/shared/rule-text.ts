import { Catalog, Operation, RelationshipKind } from '../core/catalog.service';

/** Labels for the relationship type select. */
export const KIND_LABELS: Record<RelationshipKind, string> = {
  REQUIRES: 'requires',
  REQUIRED_WITH: 'is required with',
  NOT_ALLOWED_WITH: "can't be chosen with",
};

/** What each type means, shown beside the form. */
export const KIND_HELP: Record<RelationshipKind, string> = {
  REQUIRES: 'Choosing the source also requires every target.',
  REQUIRED_WITH: 'Choosing any target also requires the source.',
  NOT_ALLOWED_WITH: "The source can't be chosen together with any target.",
};

/** Joins names as "A", "A and B", or "A, B, and C". */
export function joinNames(names: string[], conjunction = 'and'): string {
  if (names.length <= 2) {
    return names.join(` ${conjunction} `);
  }

  return `${names.slice(0, -1).join(', ')}, ${conjunction} ${names.at(-1)}`;
}

export function featureName(catalog: Catalog, id: string): string {
  return catalog.features.find((feature) => feature.id === id)?.name ?? 'Unknown feature';
}

/** One sentence that states the rule from the direction it acts in. */
export function describeRule(
  catalog: Catalog,
  sourceId: string,
  kind: RelationshipKind,
  targetIds: string[],
): string {
  const source = featureName(catalog, sourceId);
  const targets = targetIds.map((id) => featureName(catalog, id));

  switch (kind) {
    case 'REQUIRES':
      return `Choosing ${source} also requires ${joinNames(targets)}.`;

    case 'REQUIRED_WITH':
      return `Choosing ${joinNames(targets, 'or')} also requires ${source}.`;

    case 'NOT_ALLOWED_WITH':
      return targets.length === 1
        ? `${source} and ${targets[0]} can't be chosen together.`
        : `${source} can't be chosen together with ${joinNames(targets, 'or')}.`;
  }
}

export function describeOperation(catalog: Catalog, operation: Operation): string {
  if (operation.type === 'CREATE') {
    const rule = describeRule(
      catalog,
      operation.sourceFeatureId,
      operation.kind,
      operation.targetFeatureIds,
    );

    return `Add: ${rule}`;
  }

  const group = catalog.groups.find((candidate) => candidate.id === operation.groupId);
  if (!group) {
    return 'A relationship that is no longer active';
  }

  if (operation.type === 'UPDATE') {
    const rule = describeRule(
      catalog,
      group.sourceFeatureId,
      group.kind,
      operation.targetFeatureIds,
    );

    return `Change targets: ${rule}`;
  }

  return `Remove: ${describeRule(catalog, group.sourceFeatureId, group.kind, group.targetFeatureIds)}`;
}

export function formatCount(count: number): string {
  return count.toLocaleString('en-US');
}
