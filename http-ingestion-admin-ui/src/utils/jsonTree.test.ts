import { describe, expect, it } from 'vitest';
import {
  collectExpandableKeys,
  flattenTreeRows,
  mergeSchemaPropertiesWithValue,
  schemaPropertiesToTreeRows,
} from './jsonTree';

describe('flattenTreeRows', () => {
  it('returns flat rows without children property', () => {
    const rows = schemaPropertiesToTreeRows(
      {
        profile: {
          type: 'object',
          properties: {
            address: {
              type: 'object',
              properties: {
                city: { type: 'string', example: 'SZ' },
              },
            },
          },
        },
        tags: {
          type: 'array',
          items: { type: 'string', example: 'alpha' },
        },
      },
      'body',
    );
    const expanded = new Set(collectExpandableKeys(rows));
    const flat = flattenTreeRows(rows, expanded);

    expect(flat.length).toBeGreaterThan(2);
    for (const row of flat) {
      expect(row).not.toHaveProperty('children');
      expect(row).toHaveProperty('depth');
      expect(row).toHaveProperty('hasChildren');
    }
  });

  it('does not duplicate rows when toggling expand state', () => {
    const rows = mergeSchemaPropertiesWithValue(
      {
        profile: {
          type: 'object',
          properties: {
            level1: { type: 'string', example: 'L1' },
          },
        },
      },
      { profile: { level1: 'L1' } },
      'body',
    );
    const profileKey = 'body.profile';
    const collapsed = flattenTreeRows(rows, new Set<string>());
    const expanded = flattenTreeRows(rows, new Set([profileKey]));

    expect(collapsed).toHaveLength(1);
    expect(expanded).toHaveLength(2);
    expect(expanded.every((row) => !('children' in row))).toBe(true);
  });
});
