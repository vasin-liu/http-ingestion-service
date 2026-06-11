import { Button, Form, Input, Select, Space, Table } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useI18n } from '../i18n/useI18n';

const FIELD_TYPES = ['string', 'long', 'double', 'boolean', 'datetime', 'decimal'] as const;
const DATE_FORMATS = ['dahua_utc', 'meiya_datetime', 'epoch_ms', 'iso_instant'] as const;

type FieldMappingEditorProps = {
  namePath: (string | number)[];
};

function DateFormatSelect({
  namePath,
  fieldName,
  formatField,
  placeholder,
}: {
  namePath: (string | number)[];
  fieldName: number;
  formatField: 'source_format' | 'target_format';
  placeholder: string;
}) {
  const typePath = [...namePath, fieldName, 'type'];
  const fieldType = Form.useWatch(typePath);
  if (fieldType !== 'datetime') {
    return <span style={{ color: 'var(--text-secondary)' }}>-</span>;
  }
  return (
    <Form.Item name={[fieldName, formatField]} style={{ marginBottom: 0 }}>
      <Select
        allowClear
        placeholder={placeholder}
        options={DATE_FORMATS.map((value) => ({ value, label: value }))}
      />
    </Form.Item>
  );
}

export default function FieldMappingEditor({ namePath }: FieldMappingEditorProps) {
  const { t } = useI18n();

  return (
    <Form.List name={namePath}>
      {(fields, { add, remove }) => (
        <>
          <Table
            size="small"
            pagination={false}
            rowKey="key"
            dataSource={fields}
            scroll={{ x: 980 }}
            columns={[
              {
                title: t('connectorWizard.mappingTarget'),
                width: 140,
                render: (_, field) => (
                  <Form.Item
                    {...field}
                    name={[field.name, 'target']}
                    rules={[{ required: true }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="field_name" />
                  </Form.Item>
                ),
              },
              {
                title: t('connectorWizard.mappingSource'),
                width: 160,
                render: (_, field) => (
                  <Form.Item
                    {...field}
                    name={[field.name, 'source']}
                    rules={[{ required: true }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Input placeholder="$.field" />
                  </Form.Item>
                ),
              },
              {
                title: t('connectorWizard.mappingType'),
                width: 120,
                render: (_, field) => (
                  <Form.Item
                    {...field}
                    name={[field.name, 'type']}
                    initialValue="string"
                    style={{ marginBottom: 0 }}
                  >
                    <Select options={FIELD_TYPES.map((value) => ({ value, label: value }))} />
                  </Form.Item>
                ),
              },
              {
                title: t('connectorWizard.mappingSourceFormat'),
                width: 150,
                render: (_, field) => (
                  <DateFormatSelect
                    namePath={namePath}
                    fieldName={field.name}
                    formatField="source_format"
                    placeholder={t('connectorWizard.mappingFormatAuto')}
                  />
                ),
              },
              {
                title: t('connectorWizard.mappingTargetFormat'),
                width: 150,
                render: (_, field) => (
                  <DateFormatSelect
                    namePath={namePath}
                    fieldName={field.name}
                    formatField="target_format"
                    placeholder="iso_instant"
                  />
                ),
              },
              {
                title: t('common.actions'),
                width: 60,
                fixed: 'right',
                render: (_, field) => (
                  <Button type="text" danger icon={<MinusCircleOutlined />} onClick={() => remove(field.name)} />
                ),
              },
            ]}
          />
          <Space style={{ marginTop: 12 }}>
            <Button icon={<PlusOutlined />} onClick={() => add({ target: '', source: '', type: 'string' })}>
              {t('connectorWizard.addMapping')}
            </Button>
          </Space>
        </>
      )}
    </Form.List>
  );
}
