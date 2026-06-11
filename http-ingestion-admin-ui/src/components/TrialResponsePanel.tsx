import { Alert, Tabs, Typography } from 'antd';
import { useMemo } from 'react';
import { TrialResponse } from '../api/client';
import JsonTreeTable from './JsonTreeTable';
import { useI18n } from '../i18n/useI18n';
import { jsonValueToTreeRows } from '../utils/jsonTree';

interface Props {
  result: TrialResponse | null;
}

function formatJson(text: string | null): string {
  if (!text) return '';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

function parseJsonBody(text: string | null): unknown {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export default function TrialResponsePanel({ result }: Props) {
  const { t } = useI18n();

  if (!result) {
    return <Typography.Text type="secondary">{t('trialPanel.empty')}</Typography.Text>;
  }

  if (result.error) {
    return <Alert type="error" message={t('trialPanel.failed')} description={result.error} showIcon />;
  }

  const parsedBody = parseJsonBody(result.body);
  const treeRows = useMemo(() => {
    if (parsedBody == null) {
      return [];
    }
    if (typeof parsedBody === 'object') {
      return jsonValueToTreeRows(parsedBody, 'response');
    }
    return jsonValueToTreeRows(parsedBody, 'response', 'body');
  }, [parsedBody]);

  return (
    <div>
      <Typography.Paragraph>
        HTTP {result.statusCode} · {result.durationMs} ms
        {result.truncated && result.bodyLength != null
          ? ` · ${t('trialPanel.truncated', { bytes: result.bodyLength })}`
          : ''}
      </Typography.Paragraph>
      <Tabs
        items={[
          {
            key: 'structured',
            label: t('connectorWizard.responseBodyStructured'),
            children: (
              <div className="json-tree-panel" data-testid="trial-response-tree">
                <JsonTreeTable data={treeRows} mode="view" testIdPrefix="trial-response" />
              </div>
            ),
          },
          {
            key: 'raw',
            label: t('connectorWizard.responseBodyRaw'),
            children: (
              <pre className="code-block code-block-dark" style={{ maxHeight: 480 }}>
                {formatJson(result.body)}
              </pre>
            ),
          },
        ]}
      />
    </div>
  );
}
