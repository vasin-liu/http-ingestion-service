import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Steps,
  Tag,
  Switch,
  Typography,
  message,
} from 'antd';
import TextArea from 'antd/es/input/TextArea';
import { api, ConnectorRequest, ConnectorTemplate, defaultConfig, FieldMapping, TrialResponse } from '../api/client';
import FieldMappingEditor from '../components/FieldMappingEditor';
import HttpRequestConfigPanel from '../components/HttpRequestConfigPanel';
import ResponseSchemaPanel from '../components/ResponseSchemaPanel';
import { OPENAPI_IMPORT_STORAGE_KEY } from '../components/OpenApiImportModal';
import { connectorIdFromOperation, type OpenApiWizardImportPayload } from '../utils/openApiImport';
import { buildOpenApiMeta, extractOpenApiMeta, parseRequestSchema, type OpenApiMeta } from '../utils/openApiMeta';
import type { RequestSchema } from '../utils/schemaForm';
import JsonPathTree from '../components/JsonPathTree';
import TrialResponsePanel from '../components/TrialResponsePanel';
import { httpMethodLabel, modeLabel } from '../i18n/labels';
import { useI18n } from '../i18n/useI18n';
import { createHttpUrlValidator } from '../utils/urlValidator';
import { normalizeHttpConfig, prepareHttpForSave, toTrialRequest } from '../utils/httpConfig';

function deepMerge<T extends Record<string, unknown>>(base: T, patch?: Record<string, unknown>): T {
  if (!patch) return base;
  const result = { ...base } as Record<string, unknown>;
  for (const [key, value] of Object.entries(patch)) {
    if (value && typeof value === 'object' && !Array.isArray(value) && typeof result[key] === 'object') {
      result[key] = deepMerge(result[key] as Record<string, unknown>, value as Record<string, unknown>);
    } else {
      result[key] = value;
    }
  }
  return result as T;
}

function mergeConfig(base: typeof defaultConfig, patch?: Record<string, unknown>) {
  const merged = deepMerge(base, patch);
  merged.http = normalizeHttpConfig(merged.http as Record<string, unknown>);
  return merged;
}

const MODE_VALUES = ['pull', 'push', 'both'] as const;
const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const;

type OpenApiImportPayload = OpenApiWizardImportPayload;

export default function ConnectorWizardPage() {
  const { id } = useParams();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const { t, messages } = useI18n();
  const [form] = Form.useForm();
  const openapiAppliedRef = useRef(false);
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const [trialing, setTrialing] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [trialResult, setTrialResult] = useState<TrialResponse | null>(null);
  const [jsonPaths, setJsonPaths] = useState<{ path: string; sample?: string }[]>([]);
  const [previewRows, setPreviewRows] = useState<Record<string, unknown>[]>([]);
  const [responseSchemaText, setResponseSchemaText] = useState('');
  const [samplePasteText, setSamplePasteText] = useState('');
  const [requestSchema, setRequestSchema] = useState<Record<string, unknown> | null>(null);
  const [schemaSource, setSchemaSource] = useState<'none' | 'live' | 'manual'>('none');
  const [dryRunning, setDryRunning] = useState(false);
  const urlValidator = useMemo(() => createHttpUrlValidator(t('connectorWizard.urlInvalid')), [t]);
  const sinkSettings = Form.useWatch('sink', form) as { type?: string } | undefined;
  const paginationStrategy = Form.useWatch(['pagination', 'strategy'], form) ?? 'page_page_size';
  const isCursorPagination = paginationStrategy === 'cursor';
  const isLinkHeaderPagination = paginationStrategy === 'link_header';
  const isKafkaSink = sinkSettings?.type === 'kafka';
  const openapiMetaFromForm = Form.useWatch('openapi_meta', form) as OpenApiMeta | undefined;
  const requestSchemaFromForm = parseRequestSchema(openapiMetaFromForm?.request_schema);
  const effectiveRequestSchema = (requestSchemaFromForm ?? requestSchema) as RequestSchema | null;

  useEffect(() => {
    if (!id) {
      if (searchParams.get('from') === 'openapi' && !openapiAppliedRef.current) {
        openapiAppliedRef.current = true;
        const statePayload = location.state as OpenApiImportPayload | null;
        const raw = statePayload ? null : sessionStorage.getItem(OPENAPI_IMPORT_STORAGE_KEY);
        const payload = statePayload ?? (raw ? (JSON.parse(raw) as OpenApiImportPayload) : null);
        if (payload) {
          void applyOpenApiImport(payload).finally(() => {
            sessionStorage.removeItem(OPENAPI_IMPORT_STORAGE_KEY);
          });
          return;
        }
        form.setFieldsValue({ id: '', name: '', mode: 'pull', ...mergeConfig(defaultConfig) });
        return;
      }
      const templateId = searchParams.get('template');
      if (templateId) {
        void (async () => {
          try {
            const templates = await api.listTemplates();
            const template = templates.find((item) => item.id === templateId);
            if (template) {
              applyTemplate(template);
              return;
            }
          } catch (e) {
            message.error(e instanceof Error ? e.message : t('connectorWizard.loadTemplateFailed'));
          }
          form.setFieldsValue({ id: '', name: '', mode: 'pull', ...mergeConfig(defaultConfig) });
        })();
        return;
      }
      form.setFieldsValue({
        id: '',
        name: '',
        mode: 'pull',
        ...mergeConfig(defaultConfig),
      });
      return;
    }
    void (async () => {
      try {
        const detail = await api.getConnector(id);
        const merged = mergeConfig(defaultConfig, detail.draftConfig);
        form.setFieldsValue({
          id: detail.id,
          name: detail.name,
          mode: detail.mode,
          ...merged,
        });
        applyOpenApiMetaFromConfig(detail.draftConfig);
      } catch (e) {
        message.error(e instanceof Error ? e.message : t('connectorWizard.loadFailed'));
      }
    })();
  }, [form, id, searchParams, t]);

  const applyOpenApiMetaFromConfig = (config?: Record<string, unknown>) => {
    const meta = extractOpenApiMeta(config);
    if (!meta) {
      return;
    }
    if (meta.response_schema && Object.keys(meta.response_schema).length > 0) {
      setResponseSchemaText(JSON.stringify(meta.response_schema, null, 2));
      setSchemaSource('manual');
    }
    const request = parseRequestSchema(meta.request_schema);
    if (request) {
      setRequestSchema(request);
    }
    if (meta.response_schema?.input_root && typeof meta.response_schema.input_root === 'string') {
      form.setFieldValue(['transform', 'input_root'], meta.response_schema.input_root);
    }
  };

  const applyOpenApiImport = async (payload: OpenApiImportPayload) => {
    const merged = mergeConfig(defaultConfig);
    merged.http = normalizeHttpConfig(payload.httpConfig);
    const inputRoot =
      payload.suggestedInputRoot ??
      (payload.responseSchema?.input_root as string | undefined) ??
      '$';
    const suggestedId = connectorIdFromOperation({
      operationId: payload.operation.operationId ?? '',
      method: payload.operation.method,
      path: payload.operation.path,
      summary: payload.operation.summary ?? '',
      serverUrl: '',
      suggestedInputRoot: inputRoot,
      requestSchema: payload.requestSchema,
      responseSchema: payload.responseSchema,
      httpConfig: payload.httpConfig,
    });
    const openapiMeta = buildOpenApiMeta({
      requestSchema: payload.requestSchema,
      responseSchema: payload.responseSchema,
      operationId: payload.operation.operationId,
      path: payload.operation.path,
      method: payload.operation.method,
    });
    form.setFieldsValue({
      id: suggestedId,
      name: payload.operation.summary || `${payload.operation.method} ${payload.operation.path}`,
      mode: 'pull',
      ...merged,
      transform: {
        ...(merged.transform as Record<string, unknown>),
        input_root: inputRoot,
      },
      ...(openapiMeta ? { openapi_meta: openapiMeta } : {}),
    });
    setRequestSchema(payload.requestSchema ?? null);
    setSamplePasteText('');
    setTrialResult(null);
    setJsonPaths([]);
    if (payload.responseSchema && Object.keys(payload.responseSchema).length > 0) {
      setResponseSchemaText(JSON.stringify(payload.responseSchema, null, 2));
      setSchemaSource('manual');
      await inferAndApplyMappings(undefined, true, payload.responseSchema);
    } else {
      setResponseSchemaText('');
      setSchemaSource('none');
    }
    message.success(t('openapiImport.applied'));
  };

  const applyTemplate = (template: ConnectorTemplate) => {
    form.setFieldsValue({
      id: '',
      name: template.name,
      mode: template.mode,
      ...mergeConfig(defaultConfig, template.config),
    });
    if (template.responseSchema) {
      setResponseSchemaText(JSON.stringify(template.responseSchema, null, 2));
      setSchemaSource('manual');
    } else {
      setResponseSchemaText('');
      setSchemaSource('none');
    }
    setRequestSchema(template.requestSchema ?? null);
    setSamplePasteText('');
    setTrialResult(null);
    setJsonPaths([]);
  };

  const parseRecordSchema = (): Record<string, unknown> | null => {
    if (!responseSchemaText.trim()) {
      return null;
    }
    try {
      return JSON.parse(responseSchemaText) as Record<string, unknown>;
    } catch {
      message.error(t('connectorWizard.schemaInvalid'));
      return null;
    }
  };

  const applyMappingsToForm = (mappings: FieldMapping[], replaceExisting = true) => {
    const current = form.getFieldValue(['transform', 'steps', 0, 'mappings']) as FieldMapping[] | undefined;
    if (!replaceExisting && current && current.length > 0) {
      return false;
    }
    form.setFieldValue(['transform', 'steps', 0, 'mappings'], mappings);
    const existingKeys = form.getFieldValue(['sink', 'keys']) as string[] | undefined;
    if ((!existingKeys || !existingKeys[0]) && mappings[0]?.target) {
      form.setFieldValue(['sink', 'keys', 0], mappings[0].target);
    }
    return true;
  };

  const inferAndApplyMappings = async (
    responseBody?: string,
    replaceExisting = true,
    recordSchemaOverride?: Record<string, unknown> | null,
  ) => {
    const schema = recordSchemaOverride ?? parseRecordSchema();
    const inputRoot = form.getFieldValue(['transform', 'input_root']) as string | undefined;
    const mappings = await api.inferMappings({
      responseBody,
      inputRoot,
      recordSchema: schema ?? undefined,
    });
    if (mappings.length > 0) {
      applyMappingsToForm(mappings, replaceExisting);
    }
    return mappings;
  };

  const ensureTrialReadyForNext = () => {
    if (!parseRecordSchema()) {
      message.warning(t('connectorWizard.schemaRequired'));
      return false;
    }
    if (schemaSource === 'none') {
      message.warning(t('connectorWizard.trialStepRequired'));
      return false;
    }
    return true;
  };

  const applyInferredSchema = async (
    schema: Record<string, unknown>,
    source: 'live' | 'manual',
    responseBody?: string,
  ) => {
    setResponseSchemaText(JSON.stringify(schema, null, 2));
    setSchemaSource(source);
    if (schema.input_root && typeof schema.input_root === 'string') {
      form.setFieldValue(['transform', 'input_root'], schema.input_root);
    }
    await inferAndApplyMappings(responseBody, false, schema);
  };

  const resolvePreviewBody = async (): Promise<string | null> => {
    if (trialResult?.body && schemaSource === 'live') {
      return trialResult.body;
    }
    const schema = parseRecordSchema();
    if (!schema) {
      return null;
    }
    const generated = await api.generateSampleResponse({
      recordSchema: schema,
      mode: 'random',
    });
    return generated.body;
  };

  const goNext = async () => {
    try {
      if (step === 0) {
        await form.validateFields(['id', 'name', 'mode']);
      } else if (step === 1) {
        await form.validateFields([
          ['http', 'method'],
          ['http', 'url'],
        ]);
      } else if (step === 2) {
        if (!ensureTrialReadyForNext()) {
          return;
        }
        await form.validateFields([['transform', 'input_root']]);
      } else if (step === 4) {
        if (isKafkaSink) {
          await form.validateFields([['sink', 'target', 'topic']]);
        } else {
          await form.validateFields([['sink', 'target', 'table']]);
        }
        const mappings = form.getFieldValue(['transform', 'steps', 0, 'mappings']) as FieldMapping[] | undefined;
        if (!mappings || mappings.length === 0) {
          if (trialResult?.body) {
            await inferAndApplyMappings(trialResult.body);
          } else {
            await inferAndApplyMappings();
          }
        }
      }
      setStep((current) => current + 1);
    } catch {
      message.warning(t('connectorWizard.stepValidationFailed'));
    }
  };

  const buildRequest = (): ConnectorRequest => {
    const values = form.getFieldsValue(true);
    const { id: connectorId, name, mode, ...config } = values;
    if (config.http) {
      config.http = prepareHttpForSave(normalizeHttpConfig(config.http as Record<string, unknown>));
    }
    const recordSchema = parseRecordSchema();
    const existingMeta = extractOpenApiMeta(config as Record<string, unknown>);
    const requestSchemaForSave =
      parseRequestSchema(existingMeta?.request_schema) ?? requestSchema ?? undefined;
    const openapiMeta = buildOpenApiMeta({
      requestSchema: requestSchemaForSave,
      responseSchema: recordSchema,
      operationId: existingMeta?.operation_id,
      path: existingMeta?.path,
      method: existingMeta?.method,
      existing: existingMeta,
    });
    if (openapiMeta) {
      config.openapi_meta = openapiMeta;
    }
    const mappings = config.transform?.steps?.[0]?.mappings ?? [];
    if (config.transform?.steps?.[0]) {
      config.transform.steps[0].type = 'map_fields';
      for (const mapping of mappings) {
        if (mapping && !mapping.type) {
          mapping.type = 'string';
        }
      }
    }
    return { id: connectorId, name, mode, config };
  };

  const save = async () => {
    await form.validateFields();
    setSaving(true);
    try {
      const body = buildRequest();
      if (isEdit && id) {
        await api.updateConnector(id, body);
        message.success(t('connectorWizard.savedDraft'));
        navigate(`/connectors/${id}`);
      } else {
        const created = await api.createConnector(body);
        message.success(t('connectorWizard.created'));
        navigate(`/connectors/${created.id}`);
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorWizard.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const runTrial = async () => {
    await form.validateFields([['http', 'url']]);
    setTrialing(true);
    setTrialResult(null);
    setJsonPaths([]);
    try {
      const http = normalizeHttpConfig(form.getFieldValue('http') as Record<string, unknown>);
      const inputRoot = form.getFieldValue(['transform', 'input_root']) as string | undefined;
      const result = await api.trialRequest(toTrialRequest(http));
      setTrialResult(result);
      if (result.error || !result.body) {
        message.warning(result.error ?? t('connectorWizard.trialEmptyBody'));
        return;
      }
      const paths = await api.suggestJsonPath(result.body);
      setJsonPaths(paths);
      try {
        const schema = await api.inferSchema({
          responseBody: result.body,
          inputRoot,
        });
        await applyInferredSchema(schema, 'live', result.body);
        message.success(t('connectorWizard.trialDone', { status: result.statusCode }));
      } catch {
        message.warning(t('connectorWizard.trialNoRecords'));
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorWizard.trialFailed'));
    } finally {
      setTrialing(false);
    }
  };

  const inferSchemaFromPaste = async () => {
    if (!samplePasteText.trim()) {
      message.warning(t('connectorWizard.samplePasteRequired'));
      return;
    }
    try {
      const schema = await api.inferSchema({ sampleJson: samplePasteText });
      await applyInferredSchema(schema, 'manual');
      const generated = await api.generateSampleResponse({
        recordSchema: schema,
        mode: 'random',
      });
      setTrialResult({
        statusCode: 200,
        durationMs: 0,
        body: generated.body,
        truncated: false,
        bodyLength: generated.body.length,
        error: null,
      });
      setJsonPaths(await api.suggestJsonPath(generated.body));
      message.success(t('connectorWizard.schemaInferredFromSample'));
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorWizard.schemaInferFailed'));
    }
  };

  const runDryRun = async () => {
    await form.validateFields();
    setDryRunning(true);
    try {
      const body = buildRequest();
      let connectorId = id;
      if (isEdit && id) {
        await api.updateConnector(id, body);
      } else {
        const created = await api.createConnector(body);
        connectorId = created.id;
      }
      if (!connectorId) {
        throw new Error('connector id missing');
      }
      await api.publishConnector(connectorId);
      const result = await api.triggerSampleSync(connectorId, 10, false);
      message.success(t('connectorWizard.dryRunDone', { jobId: result.jobRunId }));
      navigate(`/connectors/${connectorId}`);
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorWizard.dryRunFailed'));
    } finally {
      setDryRunning(false);
    }
  };

  const runPreview = async () => {
    setPreviewing(true);
    try {
      const previewBody = await resolvePreviewBody();
      if (!previewBody) {
        message.warning(t('connectorWizard.previewNeedSchema'));
        return;
      }
      const transform = form.getFieldValue('transform');
      const rows = await api.previewTransform(previewBody, transform, 10);
      setPreviewRows(rows);
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorWizard.previewFailed'));
    } finally {
      setPreviewing(false);
    }
  };

  const applyJsonPath = (path: string) => {
    form.setFieldValue(['transform', 'input_root'], path);
    message.info(t('connectorWizard.inputRootSet', { path }));
  };

  const steps = useMemo(
    () => [
      { title: t('connectorWizard.stepBasic') },
      { title: t('connectorWizard.stepHttp') },
      { title: t('connectorWizard.stepTrial') },
      { title: t('connectorWizard.stepPagination') },
      { title: t('connectorWizard.stepMapping') },
      { title: t('connectorWizard.stepSchedule') },
    ],
    [t],
  );

  const modeOptions = MODE_VALUES.map((value) => ({
    value,
    label: modeLabel(messages, value),
  }));

  const httpMethodOptions = HTTP_METHODS.map((value) => ({
    value,
    label: httpMethodLabel(messages, value),
  }));

  return (
    <div className="wizard-shell">
      <section className="page-hero">
        <Typography.Title level={1} className="page-hero-title">
          {isEdit ? t('connectorWizard.editTitle') : t('connectorWizard.createTitle')}
        </Typography.Title>
        <Typography.Paragraph className="page-hero-subtitle" style={{ marginBottom: 0 }}>
          {t('connectorWizard.sprintHint')}
        </Typography.Paragraph>
      </section>

      <div className="wizard-progress">
        <Steps current={step} items={steps} responsive />
      </div>

      <div className="wizard-panel">
        <Form form={form} layout="vertical" size="large">
          <Form.Item name={['sink', 'type']} hidden>
            <Input />
          </Form.Item>
          <div style={{ display: step === 0 ? 'block' : 'none' }}>
            <Form.Item name="id" label={t('connectorWizard.connectorId')} rules={[{ required: true }]}>
              <Input disabled={isEdit} />
            </Form.Item>
            <Form.Item name="name" label={t('common.name')} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="mode" label={t('connectorWizard.mode')} rules={[{ required: true }]}>
              <Select options={modeOptions} />
            </Form.Item>
          </div>

          <div style={{ display: step === 1 ? 'block' : 'none' }}>
            <Form.Item name={['http', 'method']} label={t('connectorWizard.httpMethod')} rules={[{ required: true }]}>
              <Select options={httpMethodOptions} />
            </Form.Item>
            <Form.Item
              name={['http', 'url']}
              label={t('connectorWizard.url')}
              rules={[{ required: true }, { validator: urlValidator }]}
            >
              <Input placeholder="https://jsonplaceholder.typicode.com/users" data-testid="http-url" />
            </Form.Item>
            <Form.Item name={['http', 'timeout_ms']} label={t('connectorWizard.timeoutMs')}>
              <InputNumber min={1000} max={120000} style={{ width: 200 }} />
            </Form.Item>
            <HttpRequestConfigPanel namePath={['http']} requestSchema={effectiveRequestSchema} />
          </div>

          <div style={{ display: step === 2 ? 'block' : 'none' }}>
            <Alert type="info" showIcon message={t('connectorWizard.trialFlowHint')} style={{ marginBottom: 16 }} />
            <Space style={{ marginBottom: 16 }} wrap align="center">
              <Button
                type="primary"
                loading={trialing}
                data-testid="trial-request-btn"
                onClick={() => void runTrial()}
              >
                {t('connectorWizard.sendTrial')}
              </Button>
              {schemaSource === 'live' ? (
                <Tag color="success">{t('connectorWizard.schemaFromLive')}</Tag>
              ) : schemaSource === 'manual' ? (
                <Tag color="processing">{t('connectorWizard.schemaFromManual')}</Tag>
              ) : null}
            </Space>
            <TrialResponsePanel result={trialResult} />
            {schemaSource !== 'live' ? (
              <>
                <Typography.Title level={5} style={{ marginTop: 20 }}>
                  {t('connectorWizard.manualSchemaTitle')}
                </Typography.Title>
                <Typography.Paragraph type="secondary">{t('connectorWizard.manualSchemaHint')}</Typography.Paragraph>
                <TextArea
                  rows={6}
                  value={samplePasteText}
                  onChange={(e) => setSamplePasteText(e.target.value)}
                  placeholder='{"results":[{"recordId":"rec-1"}],"totalCount":1}'
                  style={{ marginBottom: 12 }}
                  data-testid="sample-paste-json"
                />
                <Button data-testid="infer-schema-from-sample-btn" onClick={() => void inferSchemaFromPaste()}>
                  {t('connectorWizard.inferSchemaFromSample')}
                </Button>
              </>
            ) : null}
            <div style={{ marginTop: 20 }}>
              <ResponseSchemaPanel
              value={responseSchemaText}
              readOnly={schemaSource === 'live'}
              onChange={(next) => {
                setResponseSchemaText(next);
                if (schemaSource === 'live') {
                  setSchemaSource('manual');
                }
              }}
              />
            </div>
            <Typography.Title level={5} style={{ marginTop: 20 }}>
              {t('connectorWizard.jsonPathPick')}
            </Typography.Title>
            <JsonPathTree paths={jsonPaths} onSelect={applyJsonPath} />
            <Form.Item
              name={['transform', 'input_root']}
              label={t('connectorWizard.inputRoot')}
              style={{ marginTop: 16 }}
              rules={[{ required: true }]}
            >
              <Input placeholder="$ 或 $.data[*]" />
            </Form.Item>
          </div>

          <div style={{ display: step === 3 ? 'block' : 'none' }}>
            <Alert type="info" showIcon message={t('connectorWizard.sprintHint')} style={{ marginBottom: 16 }} />
            <Form.Item name={['pagination', 'strategy']} label={t('connectorWizard.paginationStrategy')}>
              <Select
                options={[
                  { value: 'page_page_size', label: 'page / page_size' },
                  { value: 'offset_limit', label: 'offset / limit' },
                  { value: 'cursor', label: 'cursor' },
                  { value: 'link_header', label: 'link_header' },
                ]}
              />
            </Form.Item>
            {isLinkHeaderPagination ? (
              <>
                <Form.Item name={['pagination', 'link_header_name']} label={t('connectorWizard.linkHeaderName')}>
                  <Input placeholder="Link" data-testid="link-header-name" />
                </Form.Item>
                <Form.Item name={['pagination', 'link_rel']} label={t('connectorWizard.linkRel')}>
                  <Input placeholder="next" data-testid="link-rel" />
                </Form.Item>
                <Form.Item name={['pagination', 'stop_when']} label={t('connectorWizard.stopWhen')}>
                  <Checkbox.Group
                    options={[
                      { label: 'no_next_link', value: 'no_next_link' },
                      { label: 'empty_page', value: 'empty_page' },
                    ]}
                  />
                </Form.Item>
              </>
            ) : isCursorPagination ? (
              <>
                <Form.Item name={['pagination', 'location']} label={t('connectorWizard.paginationLocation')}>
                  <Select
                    options={[
                      { value: 'query', label: 'query' },
                      { value: 'body', label: 'body' },
                    ]}
                  />
                </Form.Item>
                <Form.Item
                  name={['pagination', 'cursor_param']}
                  label={t('connectorWizard.cursorParam')}
                  rules={[{ required: true }]}
                >
                  <Input data-testid="cursor-param" />
                </Form.Item>
                <Form.Item
                  name={['pagination', 'cursor_response_path']}
                  label={t('connectorWizard.cursorResponsePath')}
                  rules={[{ required: true }]}
                >
                  <Input placeholder="$.meta.nextCursor" data-testid="cursor-response-path" />
                </Form.Item>
                <Form.Item name={['pagination', 'has_more_path']} label={t('connectorWizard.hasMorePath')}>
                  <Input placeholder="$.meta.hasMore" />
                </Form.Item>
                <Form.Item
                  name={['pagination', 'first_page_omit_cursor']}
                  label={t('connectorWizard.firstPageOmitCursor')}
                  valuePropName="checked"
                >
                  <Switch />
                </Form.Item>
                <Form.Item name={['pagination', 'stop_when']} label={t('connectorWizard.stopWhen')}>
                  <Checkbox.Group
                    options={[
                      { label: 'empty_cursor', value: 'empty_cursor' },
                      { label: 'has_more_false', value: 'has_more_false' },
                      { label: 'empty_page', value: 'empty_page' },
                    ]}
                  />
                </Form.Item>
                <Form.Item name={['pagination', 'page_size_param']} label={t('connectorWizard.pageSizeParam')}>
                  <Input />
                </Form.Item>
                <Form.Item name={['pagination', 'page_size']} label={t('connectorWizard.pageSize')}>
                  <InputNumber min={1} max={1000} style={{ width: 200 }} />
                </Form.Item>
              </>
            ) : (
              <>
                <Form.Item name={['pagination', 'page_param']} label={t('connectorWizard.pageParam')}>
                  <Input />
                </Form.Item>
                <Form.Item name={['pagination', 'page_size_param']} label={t('connectorWizard.pageSizeParam')}>
                  <Input />
                </Form.Item>
                <Form.Item name={['pagination', 'page_size']} label={t('connectorWizard.pageSize')}>
                  <InputNumber min={1} max={1000} style={{ width: 200 }} />
                </Form.Item>
                <Form.Item name={['pagination', 'total_count', 'json_path']} label={t('connectorWizard.totalJsonPath')}>
                  <Input placeholder="$.meta.total" />
                </Form.Item>
                <Form.Item name={['pagination', 'total_count', 'source']} label={t('connectorWizard.totalCountSource')}>
                  <Select
                    options={[
                      { value: 'none', label: 'none' },
                      { value: 'json_path', label: 'json_path' },
                      { value: 'separate_request', label: 'separate_request' },
                    ]}
                  />
                </Form.Item>
                <Form.Item name={['pagination', 'total_count', 'http', 'url']} label={t('connectorWizard.totalCountUrl')}>
                  <Input placeholder="https://example/api/count" data-testid="total-count-url" />
                </Form.Item>
              </>
            )}
            <Form.Item
              name={['incremental', 'enabled']}
              label={t('connectorWizard.incrementalEnabled')}
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
            <Form.Item name={['incremental', 'timestamp', 'response_path']} label={t('connectorWizard.timestampPath')}>
              <Input />
            </Form.Item>
            <Form.Item name={['incremental', 'timestamp', 'request_param']} label={t('connectorWizard.incrementalParam')}>
              <Input />
            </Form.Item>
          </div>

          <div style={{ display: step === 4 ? 'block' : 'none' }}>
            <Alert type="info" showIcon message={t('connectorWizard.mappingHint')} style={{ marginBottom: 16 }} />
            <FieldMappingEditor namePath={['transform', 'steps', 0, 'mappings']} />
            <Form.Item name={['sink', 'keys', 0]} label={t('connectorWizard.primaryKey')} style={{ marginTop: 16 }}>
              <Input />
            </Form.Item>
            {isKafkaSink ? (
              <Form.Item
                name={['sink', 'target', 'topic']}
                label={t('connectorWizard.kafkaTopic')}
                rules={[{ required: true }]}
              >
                <Input placeholder="ingest.items" data-testid="sink-kafka-topic" />
              </Form.Item>
            ) : (
              <>
                <Form.Item name={['sink', 'target', 'schema']} label={t('connectorWizard.pgSchema')}>
                  <Input />
                </Form.Item>
                <Form.Item
                  name={['sink', 'target', 'table']}
                  label={t('connectorWizard.pgTable')}
                  rules={[{ required: true }]}
                >
                  <Input />
                </Form.Item>
              </>
            )}
            <Space>
              <Button loading={previewing} onClick={() => void runPreview()}>
                {t('connectorWizard.mappingPreview')}
              </Button>
            </Space>
            {previewRows.length > 0 && (
              <pre className="preview-block" style={{ marginTop: 12 }}>
                {JSON.stringify(previewRows, null, 2)}
              </pre>
            )}
          </div>

          <div style={{ display: step === 5 ? 'block' : 'none' }}>
            <Form.Item name={['schedule', 'enabled']} label={t('connectorWizard.scheduleEnabled')} valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name={['schedule', 'expression']} label={t('connectorWizard.cronExpression')}>
              <Input placeholder="0 0/5 * * * ?" />
            </Form.Item>
            <Alert type="info" showIcon message={t('connectorWizard.dryRunHint')} style={{ marginTop: 16 }} />
            <Button
              loading={dryRunning}
              style={{ marginTop: 12 }}
              data-testid="dry-run-btn"
              onClick={() => void runDryRun()}
            >
              {t('connectorWizard.dryRun')}
            </Button>
          </div>
        </Form>
      </div>

      <div className="wizard-footer">
        <Button disabled={step === 0} size="large" data-testid="wizard-prev" onClick={() => setStep((s) => s - 1)}>
          {t('common.prev')}
        </Button>
        {step < steps.length - 1 ? (
          <Button type="primary" size="large" data-testid="wizard-next" onClick={() => void goNext()}>
            {t('common.next')}
          </Button>
        ) : (
          <Button type="primary" size="large" data-testid="wizard-save" loading={saving} onClick={() => void save()}>
            {t('connectorWizard.saveDraft')}
          </Button>
        )}
        <Button size="large" onClick={() => navigate('/connectors')}>
          {t('common.cancel')}
        </Button>
      </div>
    </div>
  );
}
