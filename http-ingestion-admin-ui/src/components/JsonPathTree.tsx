import { List, Typography } from 'antd';
import { JsonPathSuggestion } from '../api/client';
import { useI18n } from '../i18n/useI18n';

interface Props {
  paths: JsonPathSuggestion[];
  onSelect: (path: string) => void;
}

export default function JsonPathTree({ paths, onSelect }: Props) {
  const { t } = useI18n();

  if (!paths.length) {
    return <Typography.Text type="secondary">{t('jsonPathTree.empty')}</Typography.Text>;
  }
  return (
    <List
      size="small"
      bordered
      className="json-path-list"
      dataSource={paths}
      style={{ maxHeight: 240, overflow: 'auto' }}
      renderItem={(item) => (
        <List.Item style={{ cursor: 'pointer' }} onClick={() => onSelect(item.path)}>
          <Typography.Text code>{item.path}</Typography.Text>
          {item.sample != null && (
            <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
              {item.sample}
            </Typography.Text>
          )}
        </List.Item>
      )}
    />
  );
}
