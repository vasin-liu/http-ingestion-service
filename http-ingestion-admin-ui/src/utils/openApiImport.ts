import { ConnectorRequest, defaultConfig, OpenApiOperation } from '../api/client';
import { normalizeHttpConfig, prepareHttpForSave } from './httpConfig';
import { buildOpenApiMeta } from './openApiMeta';

export function operationRowKey(operation: OpenApiOperation): string {
  return `${operation.method}-${operation.path}`;
}

export function connectorIdFromOperation(operation: OpenApiOperation, suffix = ''): string {
  const raw = operation.operationId
    || `${operation.method.toLowerCase()}-${operation.path.replace(/^\//, '')}`;
  const slug = raw
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48);
  const base = `openapi-${slug || 'operation'}`;
  return suffix ? `${base}-${suffix}` : base;
}

export function buildConnectorRequestFromOperation(
  operation: OpenApiOperation,
  connectorId: string,
): ConnectorRequest {
  const merged = {
    ...defaultConfig,
    http: normalizeHttpConfig(operation.httpConfig),
    transform: {
      ...defaultConfig.transform,
      input_root: operation.suggestedInputRoot || '$',
    },
    openapi_meta: buildOpenApiMeta({
      requestSchema: operation.requestSchema,
      responseSchema: operation.responseSchema,
      operationId: operation.operationId,
      path: operation.path,
      method: operation.method,
    }),
  };
  return {
    id: connectorId,
    name: operation.summary || `${operation.method} ${operation.path}`,
    mode: 'pull',
    config: {
      ...merged,
      http: prepareHttpForSave(merged.http),
    },
  };
}

export function toWizardImportPayload(operation: OpenApiOperation) {
  return {
    operation: {
      summary: operation.summary,
      method: operation.method,
      path: operation.path,
      operationId: operation.operationId,
    },
    httpConfig: operation.httpConfig,
    requestSchema: operation.requestSchema,
    responseSchema: operation.responseSchema,
    suggestedInputRoot: operation.suggestedInputRoot,
  };
}

export type OpenApiWizardImportPayload = ReturnType<typeof toWizardImportPayload>;

export type OpenApiOperationFilters = {
  method?: string;
  keyword?: string;
};

export function operationSearchText(operation: OpenApiOperation): string {
  return `${operation.method} ${operation.path} ${operation.summary} ${operation.operationId}`.toLowerCase();
}

export function filterOperations(operations: OpenApiOperation[], filters: OpenApiOperationFilters) {
  const method = filters.method?.trim().toUpperCase();
  const keyword = filters.keyword?.trim().toLowerCase();
  return operations.filter((operation) => {
    if (method && operation.method.toUpperCase() !== method) {
      return false;
    }
    if (keyword && !operationSearchText(operation).includes(keyword)) {
      return false;
    }
    return true;
  });
}
