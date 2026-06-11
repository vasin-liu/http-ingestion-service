import {
  ParamValueType,
  SchemaProperty,
  asObjectMap,
  defaultValueForType,
  normalizeParamType,
} from './schemaForm';

export interface JsonTreeRow {
  key: string;
  name: string;
  type: ParamValueType;
  value?: unknown;
  description?: string;
  example?: unknown;
  children?: JsonTreeRow[];
}

export interface FlatJsonTreeRow extends Omit<JsonTreeRow, 'children'> {
  depth: number;
  hasChildren: boolean;
}

export function inferTypeFromValue(value: unknown): ParamValueType {
  if (Array.isArray(value)) {
    return 'array';
  }
  if (value == null) {
    return 'string';
  }
  if (typeof value === 'boolean') {
    return 'boolean';
  }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? 'integer' : 'number';
  }
  if (typeof value === 'object') {
    return 'object';
  }
  return 'string';
}

export function flattenTreeRows(
  rows: JsonTreeRow[],
  expandedKeys: Set<string>,
  depth = 0,
): FlatJsonTreeRow[] {
  const result: FlatJsonTreeRow[] = [];
  for (const row of rows) {
    const { children, ...rest } = row;
    const hasChildren = Boolean(children?.length);
    result.push({ ...rest, depth, hasChildren });
    if (hasChildren && expandedKeys.has(row.key)) {
      result.push(...flattenTreeRows(children!, expandedKeys, depth + 1));
    }
  }
  return result;
}

export function collectExpandableKeys(rows: JsonTreeRow[]): string[] {
  const keys: string[] = [];
  const walk = (items: JsonTreeRow[]) => {
    for (const row of items) {
      if (row.children && row.children.length > 0) {
        keys.push(row.key);
        walk(row.children);
      }
    }
  };
  walk(rows);
  return keys;
}

export function schemaPropertyToRow(name: string, schema: SchemaProperty, path: string): JsonTreeRow {
  const type = normalizeParamType(schema.type);
  if (type === 'object') {
    const children = schemaPropertiesToTreeRows(schema.properties, path);
    return {
      key: path,
      name,
      type,
      description: schema.description,
      example: schema.example,
      children: children.length > 0 ? children : undefined,
    };
  }
  if (type === 'array') {
    const itemSchema = schema.items ?? { type: 'string' };
    const itemRow = schemaPropertyToRow('[items]', itemSchema, `${path}.[items]`);
    return {
      key: path,
      name,
      type,
      description: schema.description,
      example: schema.example,
      children: [itemRow],
    };
  }
  return {
    key: path,
    name,
    type,
    description: schema.description,
    example: schema.example,
    value: schema.example ?? defaultValueForType(type),
  };
}

export function schemaPropertiesToTreeRows(
  properties?: Record<string, SchemaProperty>,
  parentPath = 'schema',
): JsonTreeRow[] {
  if (!properties) {
    return [];
  }
  return Object.entries(properties).map(([name, schema]) =>
    schemaPropertyToRow(name, schema, `${parentPath}.${name}`),
  );
}

function buildMergedPropertyRow(
  name: string,
  value: unknown,
  schema: SchemaProperty | undefined,
  path: string,
): JsonTreeRow {
  const type = schema?.type ? normalizeParamType(schema.type) : inferTypeFromValue(value);
  if (type === 'object') {
    const objectValue = value == null ? {} : asObjectMap(value);
    const children = mergeSchemaPropertiesWithValue(schema?.properties, objectValue, path);
    return {
      key: path,
      name,
      type,
      description: schema?.description,
      example: schema?.example,
      children: children.length > 0 ? children : undefined,
    };
  }
  if (type === 'array') {
    const arrayValue = Array.isArray(value) ? value : [];
    const itemSchema = schema?.items;
    const children =
      arrayValue.length > 0
        ? arrayValue.map((item, index) =>
            buildMergedPropertyRow(`[${index}]`, item, itemSchema, `${path}.[${index}]`),
          )
        : itemSchema
          ? [buildMergedPropertyRow('[items]', defaultValueForType(normalizeParamType(itemSchema.type)), itemSchema, `${path}.[items]`)]
          : [buildMergedPropertyRow('[items]', defaultValueForType('string'), { type: 'string' }, `${path}.[items]`)]
    return {
      key: path,
      name,
      type,
      description: schema?.description,
      example: schema?.example,
      children,
    };
  }
  const resolvedValue =
    value !== undefined ? value : schema?.example !== undefined ? schema.example : defaultValueForType(type);
  return {
    key: path,
    name,
    type,
    value: resolvedValue,
    description: schema?.description,
    example: schema?.example,
  };
}

export function mergeSchemaPropertiesWithValue(
  schemaProps?: Record<string, SchemaProperty>,
  values?: Record<string, unknown> | null,
  parentPath = 'body',
): JsonTreeRow[] {
  const keys = new Set<string>([...Object.keys(schemaProps ?? {}), ...Object.keys(values ?? {})]);
  if (keys.size === 0) {
    return [];
  }
  return Array.from(keys).map((name) =>
    buildMergedPropertyRow(name, values?.[name], schemaProps?.[name], `${parentPath}.${name}`),
  );
}

export function rowToJsonValue(row: JsonTreeRow): unknown {
  if (row.type === 'object') {
    if (!row.children || row.children.length === 0) {
      return {};
    }
    return treeRowsToObject(row.children);
  }
  if (row.type === 'array') {
    if (!row.children || row.children.length === 0) {
      return [];
    }
    return row.children.map((child) => rowToJsonValue(child));
  }
  return row.value;
}

export function treeRowsToObject(rows: JsonTreeRow[]): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const row of rows) {
    if (row.name.startsWith('[')) {
      continue;
    }
    result[row.name] = rowToJsonValue(row);
  }
  return result;
}

export function jsonValueToTreeRows(value: unknown, parentPath = 'json', name?: string): JsonTreeRow[] {
  if (Array.isArray(value)) {
    return value.map((item, index) => {
      const path = `${parentPath}.[${index}]`;
      return valueToRow(`[${index}]`, item, path);
    });
  }
  if (value != null && typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).map(([key, item]) =>
      valueToRow(key, item, `${parentPath}.${key}`),
    );
  }
  if (name) {
    return [valueToRow(name, value, parentPath)];
  }
  return [];
}

function valueToRow(name: string, value: unknown, path: string): JsonTreeRow {
  const type = inferTypeFromValue(value);
  if (type === 'object') {
    const children = jsonValueToTreeRows(value, path, name);
    return { key: path, name, type, children: children.length > 0 ? children : undefined };
  }
  if (type === 'array') {
    const children = jsonValueToTreeRows(value, path, name);
    return { key: path, name, type, children: children.length > 0 ? children : undefined };
  }
  return { key: path, name, type, value };
}

export function normalizeBodyObject(value?: Record<string, unknown> | string | null): Record<string, unknown> {
  if (value == null || value === '') {
    return {};
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value) as unknown;
      return asObjectMap(parsed);
    } catch {
      return {};
    }
  }
  return asObjectMap(value);
}

export function updateTreeRowValue(rows: JsonTreeRow[], key: string, value: unknown): JsonTreeRow[] {
  return rows.map((row) => {
    if (row.key === key) {
      return { ...row, value };
    }
    if (row.children) {
      return { ...row, children: updateTreeRowValue(row.children, key, value) };
    }
    return row;
  });
}

export function updateTreeRowType(rows: JsonTreeRow[], key: string, type: ParamValueType): JsonTreeRow[] {
  return rows.map((row) => {
    if (row.key === key) {
      const next: JsonTreeRow = { ...row, type, value: defaultValueForType(type) };
      if (type === 'object') {
        next.children = [];
        delete next.value;
      } else if (type === 'array') {
        next.children = [schemaPropertyToRow('[items]', { type: 'string' }, `${key}.[items]`)];
        delete next.value;
      } else {
        delete next.children;
      }
      return next;
    }
    if (row.children) {
      return { ...row, children: updateTreeRowType(row.children, key, type) };
    }
    return row;
  });
}

export function addTreeRow(rows: JsonTreeRow[], parentKey?: string): JsonTreeRow[] {
  const newRow: JsonTreeRow = {
    key: `body.new.${Date.now()}`,
    name: `field${rows.length + 1}`,
    type: 'string',
    value: '',
  };
  if (!parentKey) {
    return [...rows, newRow];
  }
  return rows.map((row) => {
    if (row.key === parentKey && row.type === 'object') {
      const children = row.children ?? [];
      return { ...row, children: [...children, { ...newRow, key: `${row.key}.new.${Date.now()}` }] };
    }
    if (row.children) {
      return { ...row, children: addTreeRow(row.children, parentKey) };
    }
    return row;
  });
}

export function removeTreeRow(rows: JsonTreeRow[], key: string): JsonTreeRow[] {
  return rows
    .filter((row) => row.key !== key)
    .map((row) => (row.children ? { ...row, children: removeTreeRow(row.children, key) } : row));
}

export function renameTreeRow(rows: JsonTreeRow[], key: string, name: string): JsonTreeRow[] {
  return rows.map((row) => {
    if (row.key === key) {
      return { ...row, name };
    }
    if (row.children) {
      return { ...row, children: renameTreeRow(row.children, key, name) };
    }
    return row;
  });
}

export function formatDisplayValue(value: unknown): string {
  if (value == null) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return JSON.stringify(value);
}
