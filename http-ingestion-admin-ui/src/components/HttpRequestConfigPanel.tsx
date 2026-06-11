import { Radio, Tabs } from 'antd';
import { Form } from 'antd';
import type { NamePath } from 'antd/es/form/interface';
import { useEffect, useMemo, useState } from 'react';
import type { HttpBodyType } from '../utils/httpConfig';
import type { RequestSchema } from '../utils/schemaForm';
import SchemaParamEditor from './SchemaParamEditor';
import StructuredBodyEditor from './StructuredBodyEditor';
import { useI18n } from '../i18n/useI18n';

interface HttpRequestConfigPanelProps {
  namePath: NamePath;
  requestSchema?: RequestSchema | null;
}

export default function HttpRequestConfigPanel({ namePath, requestSchema }: HttpRequestConfigPanelProps) {
  const { t } = useI18n();
  const form = Form.useFormInstance();
  const querySchema = useMemo(() => requestSchema?.query?.properties, [requestSchema]);
  const headerSchema = useMemo(() => requestSchema?.headers?.properties, [requestSchema]);
  const bodySchema = useMemo(() => requestSchema?.body?.properties, [requestSchema]);
  const httpValues = Form.useWatch(namePath, form) as { body_type?: HttpBodyType } | undefined;
  const bodyType = httpValues?.body_type;
  const hasBodySchema = Boolean(bodySchema && Object.keys(bodySchema).length > 0);
  const effectiveBodyType: HttpBodyType =
    bodyType ?? (hasBodySchema ? 'json' : 'none');

  useEffect(() => {
    if (hasBodySchema && bodyType !== 'json') {
      form.setFieldValue([...namePath, 'body_type'], 'json');
    }
  }, [bodySchema, bodyType, form, hasBodySchema, namePath]);
  const preferredTab = useMemo(() => {
    if (bodySchema && Object.keys(bodySchema).length > 0) {
      return 'body';
    }
    if (querySchema && Object.keys(querySchema).length > 0) {
      return 'params';
    }
    if (headerSchema && Object.keys(headerSchema).length > 0) {
      return 'headers';
    }
    return 'params';
  }, [bodySchema, headerSchema, querySchema]);
  const [activeTab, setActiveTab] = useState(preferredTab);

  useEffect(() => {
    setActiveTab(preferredTab);
  }, [preferredTab]);

  const tabItems = [
    {
      key: 'params',
      label: t('connectorWizard.httpTabParams'),
      children: (
        <Form.Item name={[...namePath, 'query']} label={t('connectorWizard.httpQueryParams')}>
          <SchemaParamEditor
            testIdPrefix="http-query"
            addLabel={t('connectorWizard.httpAddParam')}
            keyPlaceholder={t('connectorWizard.httpParamKey')}
            valuePlaceholder={t('connectorWizard.httpParamValue')}
            schemaProperties={querySchema}
          />
        </Form.Item>
      ),
    },
    {
      key: 'headers',
      label: t('connectorWizard.httpTabHeaders'),
      children: (
        <Form.Item name={[...namePath, 'headers']} label={t('connectorWizard.httpHeaders')}>
          <SchemaParamEditor
            testIdPrefix="http-header"
            addLabel={t('connectorWizard.httpAddParam')}
            keyPlaceholder={t('connectorWizard.httpHeaderName')}
            valuePlaceholder={t('connectorWizard.httpHeaderValue')}
            schemaProperties={headerSchema}
          />
        </Form.Item>
      ),
    },
    {
      key: 'body',
      label: t('connectorWizard.httpTabBody'),
      forceRender: true,
      children: (
        <>
          <Form.Item name={[...namePath, 'body_type']} label={t('connectorWizard.httpBodyType')}>
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              data-testid="http-body-type"
              options={[
                { value: 'none', label: t('connectorWizard.httpBodyNone') },
                { value: 'json', label: t('connectorWizard.httpBodyJson') },
                { value: 'form-urlencoded', label: t('connectorWizard.httpBodyForm') },
              ]}
            />
          </Form.Item>
          {effectiveBodyType === 'json' ? (
            <Form.Item name={[...namePath, 'body']} label={t('connectorWizard.httpBodyStructured')}>
              <StructuredBodyEditor schemaProperties={bodySchema} testIdPrefix="http-body" />
            </Form.Item>
          ) : null}
          {effectiveBodyType === 'form-urlencoded' ? (
            <Form.Item name={[...namePath, 'form']} label={t('connectorWizard.httpFormFields')}>
              <SchemaParamEditor
                testIdPrefix="http-form"
                addLabel={t('connectorWizard.httpAddParam')}
                keyPlaceholder={t('connectorWizard.httpParamKey')}
                valuePlaceholder={t('connectorWizard.httpParamValue')}
              />
            </Form.Item>
          ) : null}
        </>
      ),
    },
  ];

  return (
    <Tabs
      activeKey={activeTab}
      destroyInactiveTabPane={false}
      onChange={setActiveTab}
      items={tabItems}
    />
  );
}
