import { Group, Operation } from '../../core/catalog.service';
import { editActive, removeActive, replacePending, stageNew, undo } from './staging';

const group: Group = {
  id: 'g1',
  sourceFeatureId: 'gpu',
  kind: 'REQUIRES',
  targetFeatureIds: ['charger'],
};

describe('staging', () => {
  it('stages a new relationship', () => {
    expect(
      stageNew([], { sourceFeatureId: 'touch', kind: 'REQUIRES', targetFeatureIds: ['s'] }),
    ).toEqual([
      { type: 'CREATE', sourceFeatureId: 'touch', kind: 'REQUIRES', targetFeatureIds: ['s'] },
    ]);
  });

  it('edits only the targets when source and type are unchanged', () => {
    const result = editActive([], group, {
      sourceFeatureId: 'gpu',
      kind: 'REQUIRES',
      targetFeatureIds: ['charger', 'battery'],
    });

    expect(result).toEqual([
      { type: 'UPDATE', groupId: 'g1', targetFeatureIds: ['charger', 'battery'] },
    ]);
  });

  it('replaces a relationship whose source or type changed with a delete and a create', () => {
    const result = editActive([], group, {
      sourceFeatureId: 'gpu',
      kind: 'NOT_ALLOWED_WITH',
      targetFeatureIds: ['fanless'],
    });

    expect(result).toEqual([
      { type: 'DELETE', groupId: 'g1' },
      {
        type: 'CREATE',
        sourceFeatureId: 'gpu',
        kind: 'NOT_ALLOWED_WITH',
        targetFeatureIds: ['fanless'],
      },
    ]);
  });

  it('removes a relationship when its last target is removed', () => {
    const result = editActive([], group, {
      sourceFeatureId: 'gpu',
      kind: 'REQUIRES',
      targetFeatureIds: [],
    });

    expect(result).toEqual([{ type: 'DELETE', groupId: 'g1' }]);
  });

  it('keeps one change per active relationship', () => {
    const staged: Operation[] = [{ type: 'UPDATE', groupId: 'g1', targetFeatureIds: ['x'] }];

    expect(removeActive(staged, group)).toEqual([{ type: 'DELETE', groupId: 'g1' }]);
    expect(
      editActive(staged, group, {
        sourceFeatureId: 'gpu',
        kind: 'REQUIRES',
        targetFeatureIds: ['charger'],
      }),
    ).toEqual([]);
  });

  it('edits or drops a pending new relationship', () => {
    const staged: Operation[] = [
      { type: 'CREATE', sourceFeatureId: 'touch', kind: 'REQUIRES', targetFeatureIds: ['s', 'h'] },
    ];

    expect(
      replacePending(staged, 0, {
        sourceFeatureId: 'touch',
        kind: 'REQUIRES',
        targetFeatureIds: ['s'],
      }),
    ).toEqual([
      { type: 'CREATE', sourceFeatureId: 'touch', kind: 'REQUIRES', targetFeatureIds: ['s'] },
    ]);
    expect(
      replacePending(staged, 0, {
        sourceFeatureId: 'touch',
        kind: 'REQUIRES',
        targetFeatureIds: [],
      }),
    ).toEqual([]);
    expect(undo(staged, 0)).toEqual([]);
  });
});
