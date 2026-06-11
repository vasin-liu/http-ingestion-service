export interface ConnectorSummary {
  id: string;
  name: string;
  mode: string;
  hasDraft: boolean;
  latestPublishedVersion: number | null;
  updatedAt: string;
}

export interface ConnectorDetail {
  id: string;
  name: string;
  mode: string;
  draftConfig: Record<string, unknown>;
  latestPublishedVersion: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectorRequest {
  id: string;
  name: string;
  mode: string;
  config: Record<string, unknown>;
}

export interface TrialRequest {
  method: string;
  url: string;
  headers?: Record<string, string>;
  query?: Record<string, string>;
  bodyType?: 'none' | 'json' | 'form-urlencoded';
  body?: string;
  form?: Record<string, string>;
  timeoutMs?: number;
}

export interface TrialResponse {
  statusCode: number;
  durationMs: number;
  body: string | null;
  truncated: boolean;
  bodyLength: number | null;
  error: string | null;
}

export interface JobRun {
  id: number;
  connectorId: string;
  runType: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  recordsOk: number;
  recordsFailed: number;
  errorMessage: string | null;
}

export interface JobRunDetail {
  id: number;
  stage: string;
  pageNumber: number | null;
  recordIndex: number | null;
  message: string | null;
  sampleJson: string | null;
}

export interface ConnectorTemplate {
  id: string;
  name: string;
  description: string;
  mode: string;
  category: 'integration' | 'example';
  responseSchema?: Record<string, unknown> | null;
  sampleResponse?: Record<string, unknown> | null;
  requestSchema?: Record<string, unknown> | null;
  config: Record<string, unknown>;
}

export interface OpenApiParseRequest {
  spec?: string;
  specUrl?: string;
}

export interface OpenApiOperation {
  operationId: string;
  method: string;
  path: string;
  summary: string;
  serverUrl: string;
  suggestedInputRoot: string;
  requestSchema: Record<string, unknown>;
  responseSchema: Record<string, unknown>;
  httpConfig: Record<string, unknown>;
}

export interface OpenApiParseResult {
  serverUrls: string[];
  operations: OpenApiOperation[];
}

export interface FieldMapping {
  target: string;
  source: string;
  type: string;
  source_format?: string;
  target_format?: string;
}

export interface ConnectorState {
  connectorId: string;
  watermarkJson: string | null;
  updatedAt: string | null;
}

export interface ConnectorSchedule {
  connectorId: string;
  scheduleType: string | null;
  expression: string | null;
  enabled: boolean;
  registered: boolean;
  paused: boolean;
}

export interface ConnectorExportBundle {
  exportVersion: string;
  id: string;
  name: string;
  mode: string;
  config: Record<string, unknown>;
  latestPublishedVersion: number | null;
  exportedAt: string;
}

export interface ConnectorImportRequest {
  id: string;
  name: string;
  mode: string;
  config: Record<string, unknown>;
  overwrite: boolean;
  publishAfterImport: boolean;
}

export interface ConnectorJobStats {
  connectorId: string;
  totalJobs: number;
  successJobs: number;
  failedJobs: number;
  recordsOk: number;
  recordsFailed: number;
}

export interface IngestionStats {
  totalJobs: number;
  successJobs: number;
  failedJobs: number;
  recordsOk: number;
  recordsFailed: number;
  byConnector: ConnectorJobStats[];
  recentJobs: JobRun[];
}

export interface JsonPathSuggestion {
  path: string;
  sample?: string;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  listConnectors: () => request<ConnectorSummary[]>('/api/connectors'),
  getConnector: (id: string) => request<ConnectorDetail>(`/api/connectors/${id}`),
  createConnector: (body: ConnectorRequest) =>
    request<ConnectorDetail>('/api/connectors', { method: 'POST', body: JSON.stringify(body) }),
  updateConnector: (id: string, body: ConnectorRequest) =>
    request<ConnectorDetail>(`/api/connectors/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteConnector: (id: string) =>
    request<void>(`/api/connectors/${id}`, { method: 'DELETE' }),
  publishConnector: (id: string) =>
    request<ConnectorDetail>(`/api/connectors/${id}/publish`, { method: 'POST' }),
  trialRequest: (body: TrialRequest) =>
    request<TrialResponse>('/api/trial-requests', { method: 'POST', body: JSON.stringify(body) }),
  suggestJsonPath: (responseBody: string, limit = 30) =>
    request<JsonPathSuggestion[]>('/api/preview/jsonpath', {
      method: 'POST',
      body: JSON.stringify({ responseBody, limit }),
    }),
  inferMappings: (payload: {
    responseBody?: string;
    inputRoot?: string;
    recordSchema?: Record<string, unknown>;
  }) =>
    request<FieldMapping[]>('/api/preview/infer-mappings', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  generateSampleResponse: (payload: {
    recordSchema: Record<string, unknown>;
    mode: 'random' | 'real';
    realSample?: Record<string, unknown>;
  }) =>
    request<{ body: string }>('/api/preview/generate-sample', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  inferSchema: (payload: { responseBody?: string; sampleJson?: string; inputRoot?: string }) =>
    request<Record<string, unknown>>('/api/preview/infer-schema', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  previewTransform: (responseBody: string, transform: Record<string, unknown>, limit = 10) =>
    request<Record<string, unknown>[]>('/api/preview/transform', {
      method: 'POST',
      body: JSON.stringify({ responseBody, transform, limit }),
    }),
  triggerSync: (id: string, type: 'full' | 'incremental') =>
    request<{ jobRunId: number; status: string }>(`/api/connectors/${id}/sync?type=${type}`, {
      method: 'POST',
    }),
  triggerSampleSync: (id: string, limit: number, writeSink: boolean) =>
    request<{ jobRunId: number; status: string }>(
      `/api/connectors/${id}/sync?type=sample&limit=${limit}&writeSink=${writeSink}`,
      { method: 'POST' }
    ),
  getJobDetails: (connectorId: string, jobId: number) =>
    request<JobRunDetail[]>(`/api/connectors/${connectorId}/jobs/${jobId}/details`),
  listTemplates: () => request<ConnectorTemplate[]>('/api/templates'),
  parseOpenApi: (body: OpenApiParseRequest) =>
    request<OpenApiParseResult>('/api/openapi/parse', { method: 'POST', body: JSON.stringify(body) }),
  getState: (id: string) => request<ConnectorState>(`/api/connectors/${id}/state`),
  resetState: (id: string) =>
    request<void>(`/api/connectors/${id}/state/reset`, { method: 'POST' }),
  getSchedule: (id: string) => request<ConnectorSchedule>(`/api/connectors/${id}/schedule`),
  pauseSchedule: (id: string) =>
    request<ConnectorSchedule>(`/api/connectors/${id}/schedule/pause`, { method: 'POST' }),
  resumeSchedule: (id: string) =>
    request<ConnectorSchedule>(`/api/connectors/${id}/schedule/resume`, { method: 'POST' }),
  listJobs: (id: string) => request<JobRun[]>(`/api/connectors/${id}/jobs`),
  exportConnector: (id: string) => request<ConnectorExportBundle>(`/api/connectors/${id}/export`),
  importConnector: (body: ConnectorImportRequest) =>
    request<ConnectorDetail>('/api/connectors/import', { method: 'POST', body: JSON.stringify(body) }),
  getStats: (recentLimit = 50) => request<IngestionStats>(`/api/stats?recentLimit=${recentLimit}`),
};

export const defaultConfig = {
  http: {
    method: 'GET',
    url: '',
    headers: {},
    query: {},
    body_type: 'none',
    body: null,
    form: {},
    timeout_ms: 30000,
  },
  pagination: {
    strategy: 'page_page_size',
    location: 'query',
    page_param: 'page',
    page_size_param: 'page_size',
    page_start: 1,
    page_size: 100,
    max_pages: 1000,
    cursor_param: 'cursor',
    cursor_response_path: '$.meta.nextCursor',
    has_more_path: '$.meta.hasMore',
    first_page_omit_cursor: true,
    stop_when: ['empty_cursor', 'empty_page'],
    total_count: { source: 'none', json_path: '', http: { method: 'POST', url: '', reuse_body: true } },
  },
  incremental: {
    enabled: false,
    timestamp: { response_path: '$.updated_at', request_param: 'updated_after', overlap: '5m' },
  },
  sync: { on_first_run: 'full' },
  transform: {
    input_root: '$',
    steps: [{ type: 'map_fields', mappings: [] as { target: string; source: string; type: string }[] }],
  },
  sink: {
    type: 'postgresql',
    target: { schema: 'public', table: '' },
    keys: ['id'],
    write_mode: 'upsert',
    batch_size: 500,
  },
  schedule: { enabled: true, type: 'cron', expression: '0 0/5 * * * ?' },
};
