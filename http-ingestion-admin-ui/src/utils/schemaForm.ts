export type ParamValueType = 'string' | 'number' | 'integer' | 'boolean' | 'array' | 'object';

export interface SchemaProperty {
  type?: string;
  example?: unknown;
  description?: string;
  in?: string;
  format?: string;
  properties?: Record<string, SchemaProperty>;
  items?: SchemaProperty;
}

export interface RequestSchema {
  headers?: { properties?: Record<string, SchemaProperty> };
  query?: { properties?: Record<string, SchemaProperty> };
  body?: { properties?: Record<string, SchemaProperty> };
}

export interface ParamFieldRow {
  key: string;
  type: ParamValueType;
  value: string;
  description?: string;
}

export interface BodyFieldRow {
  name: string;
  type: ParamValueType;
  value: unknown;
  description?: string;
}

export const PARAM_TYPE_OPTIONS: ParamValueType[] = [
  'string',
  'number',
  'integer',
  'boolean',
  'array',
  'object',
];

export function normalizeParamType(type?: string): ParamValueType {
  switch (type) {
    case 'number':
    case 'double':
    case 'float':
      return 'number';
    case 'integer':
    case 'int32':
    case 'int64':
    case 'long':
      return 'integer';
    case 'boolean':
      return 'boolean';
    case 'array':
      return 'array';
    case 'object':
      return 'object';
    default:
      return 'string';
  }
}

export function stringifyParamValue(value: unknown, type: ParamValueType): string {
  if (value == null) {
    return '';
  }
  if (type === 'object' || type === 'array') {
    return typeof value === 'string' ? value : JSON.stringify(value);
  }
  return String(value);
}

export function parseParamValue(raw: string, type: ParamValueType): unknown {
  const trimmed = raw.trim();
  if (!trimmed) {
    return type === 'boolean' ? false : type === 'number' || type === 'integer' ? 0 : '';
  }
  switch (type) {
    case 'boolean':
      return trimmed === 'true' || trimmed === '1';
    case 'integer':
      return Number.parseInt(trimmed, 10);
    case 'number':
      return Number.parseFloat(trimmed);
    case 'array':
    case 'object':
      try {
        return JSON.parse(trimmed);
      } catch {
        return trimmed;
      }
    default:
      return raw;
  }
}

export function buildParamRows(
  schemaProps?: Record<string, SchemaProperty>,
  values?: Record<string, string>,
): ParamFieldRow[] {
  const keys = new Set<string>([
    ...Object.keys(schemaProps ?? {}),
    ...Object.keys(values ?? {}),
  ]);
  if (keys.size === 0) {
    return [{ key: '', type: 'string', value: '' }];
  }
  return Array.from(keys).map((key) => {
    const schema = schemaProps?.[key];
    const type = normalizeParamType(schema?.type);
    const rawValue = values?.[key];
    const value =
      rawValue != null
        ? rawValue
        : schema?.example != null
          ? stringifyParamValue(schema.example, type)
          : '';
    return {
      key,
      type,
      value,
      description: schema?.description,
    };
  });
}

export function paramRowsToMap(rows: ParamFieldRow[]): Record<string, string> {
  const result: Record<string, string> = {};
  for (const row of rows) {
    if (!row.key.trim()) {
      continue;
    }
    result[row.key.trim()] = row.value;
  }
  return result;
}

export function buildBodyFieldRows(
  schemaProps?: Record<string, SchemaProperty>,
  values?: Record<string, unknown> | null,
): BodyFieldRow[] {
  const keys = new Set<string>([
    ...Object.keys(schemaProps ?? {}),
    ...Object.keys(values ?? {}),
  ]);
  if (keys.size === 0) {
    return [];
  }
  return Array.from(keys).map((name) => {
    const schema = schemaProps?.[name];
    const type = normalizeParamType(schema?.type);
    const current = values?.[name];
    const value =
      current !== undefined
        ? current
        : schema?.example !== undefined
          ? schema.example
          : defaultValueForType(type);
    return {
      name,
      type,
      value,
      description: schema?.description,
    };
  });
}

export function bodyFieldRowsToObject(rows: BodyFieldRow[]): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const row of rows) {
    if (!row.name.trim()) {
      continue;
    }
    if (row.type === 'object' || row.type === 'array') {
      result[row.name.trim()] =
        typeof row.value === 'string' ? parseParamValue(row.value, row.type) : row.value;
    } else {
      result[row.name.trim()] = parseParamValue(stringifyParamValue(row.value, row.type), row.type);
    }
  }
  return result;
}

export function defaultValueForType(type: ParamValueType): unknown {
  switch (type) {
    case 'boolean':
      return false;
    case 'integer':
      return 0;
    case 'number':
      return 0;
    case 'array':
      return [];
    case 'object':
      return {};
    default:
      return '';
  }
}

export function asObjectMap(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

export function parseJsonObject(text: string): Record<string, unknown> | null {
  if (!text.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(text) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
    return null;
  } catch {
    return null;
  }
}

export interface ResponseSchemaShape {
  input_root?: string;
  record?: { type?: string; properties?: Record<string, SchemaProperty> };
  envelope?: { type?: string; properties?: Record<string, SchemaProperty> };
}

export function parseResponseSchema(text: string): ResponseSchemaShape | null {
  return parseJsonObject(text) as ResponseSchemaShape | null;
}

export function flattenSchemaProperties(
  properties?: Record<string, SchemaProperty>,
  prefix = '',
): Array<{ key: string; type: string; example: string; description: string }> {
  if (!properties) {
    return [];
  }
  const rows: Array<{ key: string; type: string; example: string; description: string }> = [];
  for (const [name, property] of Object.entries(properties)) {
    const fullName = prefix ? `${prefix}.${name}` : name;
    if (property.properties && Object.keys(property.properties).length > 0) {
      rows.push(...flattenSchemaProperties(property.properties, fullName));
      continue;
    }
    rows.push({
      key: fullName,
      type: property.type ?? 'string',
      example: property.example == null ? '' : String(property.example),
      description: property.description ?? '',
    });
  }
  return rows;
}
