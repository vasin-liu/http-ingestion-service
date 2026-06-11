import { Button, Input, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useMemo } from 'react';
import TypedValueInput from './TypedValueInput';
import { useI18n } from '../i18n/useI18n';
import {
  PARAM_TYPE_OPTIONS,
  ParamFieldRow,
  SchemaProperty,
  buildParamRows,
  paramRowsToMap,
} from '../utils/schemaForm';

interface SchemaParamEditorProps {
  value?: Record<string, string>;
  onChange?: (value: Record<string, string>) => void;
  schemaProperties?: Record<string, SchemaProperty>;
  testIdPrefix: string;
  addLabel?: string;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
}

export default function SchemaParamEditor({
  value,
  onChange,
  schemaProperties,
  testIdPrefix,
  addLabel,
  keyPlaceholder,
  valuePlaceholder,
}: SchemaParamEditorProps) {
  const { t } = useI18n();
  const rows = useMemo(() => buildParamRows(schemaProperties, value), [schemaProperties, value]);

  const updateRows = (nextRows: ParamFieldRow[]) => {
    onChange?.(paramRowsToMap(nextRows));
  };

  const columns: ColumnsType<ParamFieldRow & { index: number }> = [
    {
      title: keyPlaceholder ?? t('connectorWizard.httpParamKey'),
      dataIndex: 'key',
      width: 180,
      render: (_, record) => (
        <Input
          value={record.key}
          placeholder={keyPlaceholder}
          data-testid={`${testIdPrefix}-key-${record.index}`}
          onChange={(event) => {
            const next = [...rows];
            next[record.index] = { ...next[record.index], key: event.target.value };
            updateRows(next);
          }}
        />
      ),
    },
    {
      title: t('connectorWizard.requestParamType'),
      dataIndex: 'type',
      width: 120,
      render: (_, record) => (
        <Select
          style={{ width: '100%' }}
          value={record.type}
          options={PARAM_TYPE_OPTIONS.map((type) => ({ value: type, label: type }))}
          data-testid={`${testIdPrefix}-type-${record.index}`}
          onChange={(type) => {
            const next = [...rows];
            next[record.index] = { ...next[record.index], type };
            updateRows(next);
          }}
        />
      ),
    },
    {
      title: valuePlaceholder ?? t('connectorWizard.httpParamValue'),
      dataIndex: 'value',
      render: (_, record) => (
        <TypedValueInput
          type={record.type}
          value={record.value}
          placeholder={valuePlaceholder}
          testId={`${testIdPrefix}-value-${record.index}`}
          onChange={(nextValue) => {
            const next = [...rows];
            next[record.index] = { ...next[record.index], value: nextValue };
            updateRows(next);
          }}
        />
      ),
    },
    {
      title: t('connectorWizard.requestParamDescription'),
      dataIndex: 'description',
      width: 160,
      render: (_, record) =>
        record.description ? (
          <Typography.Text type="secondary">{record.description}</Typography.Text>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
    {
      title: '',
      width: 48,
      render: (_, record) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          data-testid={`${testIdPrefix}-remove-${record.index}`}
          onClick={() => updateRows(rows.filter((_, index) => index !== record.index))}
        />
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Table
        size="small"
        pagination={false}
        rowKey={(_, index) => String(index)}
        dataSource={rows.map((row, index) => ({ ...row, index }))}
        columns={columns}
      />
      <Button
        type="dashed"
        icon={<PlusOutlined />}
        data-testid={`${testIdPrefix}-add`}
        onClick={() => updateRows([...rows, { key: '', type: 'string', value: '' }])}
      >
        {addLabel ?? '+'}
      </Button>
    </Space>
  );
}
