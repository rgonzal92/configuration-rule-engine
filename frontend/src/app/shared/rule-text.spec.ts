import { describeOperation, describeRule, formatChange, formatCount, joinNames } from './rule-text';
import { Catalog } from '../core/catalog.service';

const catalog: Catalog = {
  id: 'c',
  name: 'Laptop',
  readOnly: false,
  revision: 1,
  features: [
    { id: 'gpu', code: 'GPU', name: 'Dedicated GPU' },
    { id: 'charger', code: 'CHARGER', name: 'High-wattage charger' },
    { id: 'touch', code: 'TOUCH', name: 'Touchscreen' },
    { id: 'stylus', code: 'STYLUS', name: 'Stylus support' },
  ],
  groups: [{ id: 'g1', sourceFeatureId: 'gpu', kind: 'REQUIRES', targetFeatureIds: ['charger'] }],
};

describe('rule text', () => {
  it('joins names in plain English', () => {
    expect(joinNames(['A'])).toBe('A');
    expect(joinNames(['A', 'B'])).toBe('A and B');
    expect(joinNames(['A', 'B', 'C'])).toBe('A, B, and C');
    expect(joinNames(['A', 'B'], 'or')).toBe('A or B');
  });

  it('explains each relationship type from the direction it acts in', () => {
    expect(describeRule(catalog, 'touch', 'REQUIRES', ['stylus', 'charger'])).toBe(
      'Choosing Touchscreen also requires Stylus support and High-wattage charger.',
    );
    expect(describeRule(catalog, 'stylus', 'REQUIRED_WITH', ['touch'])).toBe(
      'Choosing Touchscreen also requires Stylus support.',
    );
    expect(describeRule(catalog, 'stylus', 'REQUIRED_WITH', ['touch', 'gpu'])).toBe(
      'Choosing Touchscreen or Dedicated GPU also requires Stylus support.',
    );
    expect(describeRule(catalog, 'gpu', 'NOT_ALLOWED_WITH', ['touch'])).toBe(
      "Dedicated GPU and Touchscreen can't be chosen together.",
    );
    expect(describeRule(catalog, 'gpu', 'NOT_ALLOWED_WITH', ['touch', 'stylus'])).toBe(
      "Dedicated GPU can't be chosen together with Touchscreen or Stylus support.",
    );
  });

  it('describes pending changes', () => {
    expect(
      describeOperation(catalog, {
        type: 'CREATE',
        sourceFeatureId: 'touch',
        kind: 'REQUIRES',
        targetFeatureIds: ['stylus'],
      }),
    ).toBe('Add: Choosing Touchscreen also requires Stylus support.');
    expect(
      describeOperation(catalog, { type: 'UPDATE', groupId: 'g1', targetFeatureIds: ['stylus'] }),
    ).toBe('Change targets: Choosing Dedicated GPU also requires Stylus support.');
    expect(describeOperation(catalog, { type: 'DELETE', groupId: 'g1' })).toBe(
      'Remove: Choosing Dedicated GPU also requires High-wattage charger.',
    );
  });

  it('formats counts with separators', () => {
    expect(formatCount(1536)).toBe('1,536');
  });

  it('formats the change between two counts as a signed percentage', () => {
    expect(formatChange(1536, 960)).toBe('-37.5%');
    expect(formatChange(12, 16)).toBe('+33.3%');
    expect(formatChange(9, 9)).toBe('0%');
    expect(formatChange(0, 4)).toBe('');
  });
});
