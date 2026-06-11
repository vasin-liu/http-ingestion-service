import type { RequestSchema } from './schemaForm';

export interface OpenApiMeta {
  request_schema?: Record<string, unknown>;
  response_schema?: Record<string, unknown>;
  operation_id?: string;
  path?: string;
  method?: string;
}

export function extractOpenApiMeta(config?: Record<string, unknown> | null): OpenApiMeta | null {
  if (!config || typeof config !== 'object') {
    return null;
  }
  const raw = config.openapi_meta;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return null;
  }
  return raw as OpenApiMeta;
}

export function parseRequestSchema(value: unknown): RequestSchema | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as RequestSchema;
}

export function buildOpenApiMeta(params: {
  requestSchema?: Record<string, unknown> | null;
  responseSchema?: Record<string, unknown> | null;
  operationId?: string;
  path?: string;
  method?: string;
  existing?: OpenApiMeta | null;
}): OpenApiMeta | null {
  const { requestSchema, responseSchema, operationId, path, method, existing } = params;
  const next: OpenApiMeta = { ...(existing ?? {}) };

  if (requestSchema && Object.keys(requestSchema).length > 0) {
    next.request_schema = requestSchema;
  }
  if (responseSchema && Object.keys(responseSchema).length > 0) {
    next.response_schema = responseSchema;
  }
  if (operationId) {
    next.operation_id = operationId;
  }
  if (path) {
    next.path = path;
  }
  if (method) {
    next.method = method;
  }

  if (!next.request_schema && !next.response_schema) {
    return existing ?? null;
  }
  return next;
}
