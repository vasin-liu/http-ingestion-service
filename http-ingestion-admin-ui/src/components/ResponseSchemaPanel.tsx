import { Input, Typography } from 'antd';
import TextArea from 'antd/es/input/TextArea';
import { useMemo } from 'react';
import JsonTreeTable from './JsonTreeTable';
import { useI18n } from '../i18n/useI18n';
import { schemaPropertiesToTreeRows } from '../utils/jsonTree';
import { ResponseSchemaShape, parseResponseSchema } from '../utils/schemaForm';

interface ResponseSchemaPanelProps {
  value: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
}

function serializeSchema(schema: ResponseSchemaShape): string {
  return JSON.stringify(schema, null, 2);
}

export default function ResponseSchemaPanel({ value, onChange, readOnly = false }: ResponseSchemaPanelProps) {
  const { t } = useI18n();
  const parsed = useMemo(() => parseResponseSchema(value), [value]);

  const updateSchema = (next: ResponseSchemaShape) => {
    onChange?.(serializeSchema(next));
  };

  const recordRows = useMemo(
    () => schemaPropertiesToTreeRows(parsed?.record?.properties, 'record'),
    [parsed],
  );
  const envelopeRows = useMemo(
    () => schemaPropertiesToTreeRows(parsed?.envelope?.properties, 'envelope'),
    [parsed],
  );

  if (!parsed) {
    return (
      <TextArea
        rows={10}
        value={value}
        readOnly={readOnly}
        placeholder='{"input_root":"$.results","record":{"type":"object","properties":{...}},"envelope":{...}}'
        data-testid="response-schema-json"
        onChange={(event) => onChange?.(event.target.value)}
      />
    );
  }

  return (
    <div data-testid="response-schema-visual">
      <Typography.Title level={5} style={{ marginTop: 0 }}>
        {t('connectorWizard.responseSchemaVisualTitle')}
      </Typography.Title>
      <Typography.Text type="secondary">{t('connectorWizard.responseSchemaVisualHint')}</Typography.Text>
      <div className="response-schema-input-root">
        <Typography.Text strong className="response-schema-input-root__label">
          {t('connectorWizard.inputRoot')}
        </Typography.Text>
        <Input
          className="response-schema-input-root__input"
          value={parsed.input_root ?? ''}
          readOnly={readOnly}
          placeholder="$ 或 $.data[*]"
          data-testid="response-schema-input-root"
          onChange={readOnly ? undefined : (event) => updateSchema({ ...parsed, input_root: event.target.value })}
        />
      </div>
      <Typography.Title level={5}>{t('connectorWizard.responseRecordSchema')}</Typography.Title>
      <JsonTreeTable
        data={recordRows}
        mode="schema"
        testIdPrefix="response-record"
        emptyText={t('connectorWizard.responseSchemaEmpty')}
      />
      <Typography.Title level={5} style={{ marginTop: 16 }}>
        {t('connectorWizard.responseEnvelopeSchema')}
      </Typography.Title>
      <JsonTreeTable
        data={envelopeRows}
        mode="schema"
        testIdPrefix="response-envelope"
        emptyText={t('connectorWizard.responseSchemaEmpty')}
      />
      {!readOnly ? (
        <>
          <Typography.Text strong style={{ display: 'block', marginTop: 16 }}>
            {t('connectorWizard.responseSchemaAdvanced')}
          </Typography.Text>
          <TextArea
            rows={6}
            value={value}
            style={{ marginTop: 8 }}
            data-testid="response-schema-json"
            onChange={(event) => onChange?.(event.target.value)}
          />
        </>
      ) : null}
    </div>
  );
}
