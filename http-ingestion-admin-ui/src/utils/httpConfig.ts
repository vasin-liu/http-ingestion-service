export type HttpBodyType = 'none' | 'json' | 'form-urlencoded';

export interface HttpConfig {
  method: string;
  url: string;
  headers: Record<string, string>;
  query: Record<string, string>;
  body_type: HttpBodyType;
  body?: string | Record<string, unknown> | null;
  form?: Record<string, string>;
  timeout_ms: number;
}

const defaultHttpConfig = (): HttpConfig => ({
  method: 'GET',
  url: '',
  headers: {},
  query: {},
  body_type: 'none',
  body: null,
  form: {},
  timeout_ms: 30000,
});

export function normalizeHttpConfig(raw?: Record<string, unknown>): HttpConfig {
  const source = raw ?? {};
  const headers = asStringMap(source.headers);
  const query = asStringMap(source.query);
  const form = asStringMap(source.form);

  let body = source.body ?? source.bodyJson ?? null;
  let bodyType = source.body_type as HttpBodyType | undefined;
  if (!bodyType && typeof source.bodyType === 'string') {
    bodyType = source.bodyType as HttpBodyType;
  }

  const timeoutMs =
    typeof source.timeout_ms === 'number'
      ? source.timeout_ms
      : typeof source.timeoutMs === 'number'
        ? source.timeoutMs
        : 30000;

  if (!bodyType) {
    if (Object.keys(form).length > 0) {
      bodyType = 'form-urlencoded';
    } else if (body != null && body !== '') {
      bodyType = 'json';
    } else {
      bodyType = 'none';
    }
  }

  if (bodyType === 'json' && body != null && typeof body === 'object') {
    // Keep object shape for structured body editor.
  } else if (bodyType === 'json' && typeof body === 'string' && body.trim()) {
    try {
      body = JSON.parse(body) as Record<string, unknown>;
    } catch {
      // keep raw string for invalid JSON until user fixes it
    }
  }

  return {
    ...defaultHttpConfig(),
    method: typeof source.method === 'string' ? source.method : 'GET',
    url: typeof source.url === 'string' ? source.url : '',
    headers,
    query,
    body_type: bodyType,
    body: bodyType === 'json' ? (body as string | Record<string, unknown> | null) : null,
    form: bodyType === 'form-urlencoded' ? form : {},
    timeout_ms: timeoutMs,
  };
}

export function prepareHttpForSave(http: HttpConfig): Record<string, unknown> {
  const result: Record<string, unknown> = {
    method: http.method,
    url: http.url,
    headers: http.headers ?? {},
    query: http.query ?? {},
    body_type: http.body_type,
    timeout_ms: http.timeout_ms,
  };

  if (http.body_type === 'json') {
    const rawBody = http.body;
    if (typeof rawBody === 'string' && rawBody.trim()) {
      try {
        result.body = JSON.parse(rawBody);
      } catch {
        result.body = rawBody;
      }
    } else if (rawBody && typeof rawBody === 'object') {
      result.body = rawBody;
    } else {
      result.body = null;
    }
    result.form = {};
  } else if (http.body_type === 'form-urlencoded') {
    result.form = http.form ?? {};
    result.body = null;
  } else {
    result.body = null;
    result.form = {};
  }

  return result;
}

export function serializeJsonBody(body: HttpConfig['body']): string | undefined {
  if (body == null || body === '') {
    return undefined;
  }
  if (typeof body === 'string') {
    return body;
  }
  return JSON.stringify(body);
}

export function toTrialRequest(http: HttpConfig) {
  const bodyType = http.body_type ?? 'none';
  if (bodyType === 'json') {
    return {
      method: http.method,
      url: http.url,
      headers: http.headers,
      query: http.query,
      bodyType: 'json' as const,
      body: serializeJsonBody(http.body),
      timeoutMs: http.timeout_ms,
    };
  }
  if (bodyType === 'form-urlencoded') {
    return {
      method: http.method,
      url: http.url,
      headers: http.headers,
      query: http.query,
      bodyType: 'form-urlencoded' as const,
      form: http.form ?? {},
      timeoutMs: http.timeout_ms,
    };
  }
  return {
    method: http.method,
    url: http.url,
    headers: http.headers,
    query: http.query,
    bodyType: 'none' as const,
    timeoutMs: http.timeout_ms,
  };
}

function asStringMap(value: unknown): Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  const result: Record<string, string> = {};
  for (const [key, entryValue] of Object.entries(value as Record<string, unknown>)) {
    if (entryValue != null) {
      result[key] = String(entryValue);
    }
  }
  return result;
}
