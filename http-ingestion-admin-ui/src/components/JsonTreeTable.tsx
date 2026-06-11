import { Button, Input, Select, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CaretDownOutlined, CaretRightOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import TypedValueInput from './TypedValueInput';
import { useI18n } from '../i18n/useI18n';
import {
  FlatJsonTreeRow,
  JsonTreeRow,
  collectExpandableKeys,
  flattenTreeRows,
  formatDisplayValue,
} from '../utils/jsonTree';
import { PARAM_TYPE_OPTIONS, ParamValueType } from '../utils/schemaForm';

export type JsonTreeTableMode = 'edit' | 'view' | 'schema';

interface JsonTreeTableProps {
  data: JsonTreeRow[];
  mode?: JsonTreeTableMode;
  onChange?: (data: JsonTreeRow[]) => void;
  onValueChange?: (key: string, value: unknown) => void;
  onTypeChange?: (key: string, type: ParamValueType) => void;
  onRename?: (key: string, name: string) => void;
  onRemove?: (key: string) => void;
  onAdd?: (parentKey?: string) => void;
  testIdPrefix?: string;
  emptyText?: string;
  showAddButton?: boolean;
}

const TYPE_COLORS: Record<ParamValueType, string> = {
  string: 'green',
  number: 'blue',
  integer: 'geekblue',
  boolean: 'orange',
  object: 'purple',
  array: 'cyan',
};

const COLUMN_WIDTH = {
  type: 110,
  value: 220,
  description: 160,
  actions: 72,
} as const;

function isLeafRow(row: JsonTreeRow): boolean {
  return row.type !== 'object' && row.type !== 'array';
}

function TreeIndent({ depth }: { depth: number }) {
  if (depth <= 0) {
    return null;
  }
  return (
    <span className="json-tree-indent" aria-hidden>
      {Array.from({ length: depth }).map((_, index) => (
        <span key={index} className="json-tree-indent-level" />
      ))}
    </span>
  );
}

export default function JsonTreeTable({
  data,
  mode = 'view',
  onChange,
  onValueChange,
  onTypeChange,
  onRename,
  onRemove,
  onAdd,
  testIdPrefix = 'json-tree',
  emptyText,
  showAddButton = false,
}: JsonTreeTableProps) {
  const { t } = useI18n();
  const expandableKeys = useMemo(() => collectExpandableKeys(data), [data]);
  const [expandedKeys, setExpandedKeys] = useState<string[]>(expandableKeys);

  useEffect(() => {
    setExpandedKeys((current) => {
      const valid = new Set(expandableKeys);
      const kept = current.filter((key) => valid.has(key));
      return kept.length > 0 ? kept : expandableKeys;
    });
  }, [expandableKeys]);

  const expandedSet = useMemo(() => new Set(expandedKeys), [expandedKeys]);
  const flatRows = useMemo(() => flattenTreeRows(data, expandedSet), [data, expandedSet]);

  const toggleExpand = (key: string) => {
    setExpandedKeys((current) =>
      current.includes(key) ? current.filter((item) => item !== key) : [...current, key],
    );
  };

  const handleAdd = (parentKey?: string) => {
    if (parentKey) {
      setExpandedKeys((current) =>
        current.includes(parentKey) ? current : [...current, parentKey],
      );
    }
    onAdd?.(parentKey);
  };

  const renderFieldCell = (row: FlatJsonTreeRow) => {
    const hasChildren = row.hasChildren;
    const isArrayItem = row.name.startsWith('[');
    const isExpandablePlaceholder = row.name === '[items]';

    return (
      <div className="json-tree-field-cell">
        <TreeIndent depth={row.depth} />
        {hasChildren ? (
          <button
            type="button"
            className="json-tree-expand-btn"
            aria-label={expandedSet.has(row.key) ? 'Collapse' : 'Expand'}
            data-testid={`${testIdPrefix}-expand-${row.key}`}
            onClick={() => toggleExpand(row.key)}
          >
            {expandedSet.has(row.key) ? <CaretDownOutlined /> : <CaretRightOutlined />}
          </button>
        ) : (
          <span className="json-tree-expand-placeholder" />
        )}
        <div className="json-tree-field-content">
          {mode === 'edit' && !isArrayItem && !isExpandablePlaceholder ? (
            <Input
              size="small"
              value={row.name}
              data-testid={`${testIdPrefix}-name-${row.key}`}
              onChange={(event) => onRename?.(row.key, event.target.value)}
            />
          ) : (
            <Typography.Text
              strong={!isArrayItem}
              className="json-tree-field-name"
              data-testid={`${testIdPrefix}-name-${row.key}`}
            >
              {row.name}
            </Typography.Text>
          )}
          {mode === 'edit' && row.type === 'object' && onAdd ? (
            <Button
              type="text"
              size="small"
              icon={<PlusOutlined />}
              className="json-tree-add-child-btn"
              aria-label="Add field"
              data-testid={`${testIdPrefix}-add-child-${row.key}`}
              onClick={() => handleAdd(row.key)}
            />
          ) : null}
        </div>
      </div>
    );
  };

  const columns: ColumnsType<FlatJsonTreeRow> = [
    {
      title: t('connectorWizard.jsonTreeField'),
      dataIndex: 'name',
      render: (_, row) => renderFieldCell(row),
    },
    {
      title: t('connectorWizard.requestParamType'),
      dataIndex: 'type',
      width: COLUMN_WIDTH.type,
      render: (_, row) => {
        if (mode === 'edit' && onTypeChange) {
          return (
            <Select
              size="small"
              style={{ width: '100%' }}
              value={row.type}
              options={PARAM_TYPE_OPTIONS.map((type) => ({ value: type, label: type }))}
              data-testid={`${testIdPrefix}-type-${row.key}`}
              onChange={(type) => onTypeChange(row.key, type)}
            />
          );
        }
        return <Tag color={TYPE_COLORS[row.type]}>{row.type}</Tag>;
      },
    },
    {
      title: mode === 'schema' ? t('connectorWizard.requestParamExample') : t('connectorWizard.jsonTreeValue'),
      dataIndex: 'value',
      width: COLUMN_WIDTH.value,
      render: (_, row) => {
        if (!isLeafRow(row)) {
          return <Typography.Text type="secondary">{row.type}</Typography.Text>;
        }
        const displayValue = mode === 'schema' ? row.example ?? row.value : row.value;
        if (mode === 'edit' && onValueChange) {
          return (
            <TypedValueInput
              compact
              type={row.type}
              value={formatDisplayValue(row.value)}
              testId={`${testIdPrefix}-value-${row.key}`}
              onChange={(next) => onValueChange(row.key, next)}
            />
          );
        }
        return (
          <Typography.Text code className="json-tree-value">
            {formatDisplayValue(displayValue)}
          </Typography.Text>
        );
      },
    },
    {
      title: t('connectorWizard.requestParamDescription'),
      dataIndex: 'description',
      width: COLUMN_WIDTH.description,
      ellipsis: true,
      render: (_, row) =>
        row.description ? (
          <Typography.Text type="secondary">{row.description}</Typography.Text>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
  ];

  if (mode === 'edit' && onRemove) {
    columns.push({
      title: '',
      width: COLUMN_WIDTH.actions,
      render: (_, row) =>
        row.name.startsWith('[') ? null : (
          <Button
            type="text"
            danger
            size="small"
            icon={<DeleteOutlined />}
            data-testid={`${testIdPrefix}-remove-${row.key}`}
            onClick={() => onRemove(row.key)}
          />
        ),
    });
  }

  return (
    <div className={`json-tree-table json-tree-table--${mode}`} data-testid={`${testIdPrefix}-table`}>
      <Table
        size="small"
        tableLayout="fixed"
        pagination={false}
        rowKey="key"
        dataSource={flatRows}
        columns={columns}
        locale={{ emptyText: emptyText ?? t('connectorWizard.responseSchemaEmpty') }}
        rowClassName={(row) => `json-tree-row json-tree-row-depth-${row.depth}`}
        expandable={{ childrenColumnName: '__jsonTreeNoChildren__', showExpandColumn: false }}
      />
      {showAddButton && mode === 'edit' && onAdd ? (
        <Button
          type="dashed"
          size="small"
          icon={<PlusOutlined />}
          style={{ marginTop: 8 }}
          data-testid={`${testIdPrefix}-add`}
          onClick={() => handleAdd()}
        >
          {t('connectorWizard.httpAddParam')}
        </Button>
      ) : null}
    </div>
  );
}
