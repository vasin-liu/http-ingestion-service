import { useMemo } from 'react';
import JsonTreeTable from './JsonTreeTable';
import { useI18n } from '../i18n/useI18n';
import {
  addTreeRow,
  mergeSchemaPropertiesWithValue,
  normalizeBodyObject,
  removeTreeRow,
  renameTreeRow,
  treeRowsToObject,
  updateTreeRowType,
  updateTreeRowValue,
} from '../utils/jsonTree';
import { SchemaProperty, parseParamValue } from '../utils/schemaForm';

interface StructuredBodyEditorProps {
  value?: Record<string, unknown> | string | null;
  onChange?: (value: Record<string, unknown>) => void;
  schemaProperties?: Record<string, SchemaProperty>;
  testIdPrefix?: string;
}

export default function StructuredBodyEditor({
  value,
  onChange,
  schemaProperties,
  testIdPrefix = 'http-body',
}: StructuredBodyEditorProps) {
  const { t } = useI18n();
  const objectValue = useMemo(() => normalizeBodyObject(value), [value]);
  const rows = useMemo(
    () => mergeSchemaPropertiesWithValue(schemaProperties, objectValue),
    [objectValue, schemaProperties],
  );

  const emitChange = (nextRows: ReturnType<typeof mergeSchemaPropertiesWithValue>) => {
    onChange?.(treeRowsToObject(nextRows));
  };

  return (
    <div data-testid={`${testIdPrefix}-tree`}>
      <JsonTreeTable
        data={rows}
        mode="edit"
        showAddButton
        testIdPrefix={testIdPrefix}
        emptyText={t('connectorWizard.httpBodyEmptySchema')}
        onValueChange={(key, raw) => {
          const row = findRow(rows, key);
          if (!row) {
            return;
          }
          emitChange(updateTreeRowValue(rows, key, parseParamValue(String(raw), row.type)));
        }}
        onTypeChange={(key, type) => emitChange(updateTreeRowType(rows, key, type))}
        onRename={(key, name) => emitChange(renameTreeRow(rows, key, name))}
        onRemove={(key) => emitChange(removeTreeRow(rows, key))}
        onAdd={(parentKey) => emitChange(addTreeRow(rows, parentKey))}
      />
    </div>
  );
}

function findRow(rows: ReturnType<typeof mergeSchemaPropertiesWithValue>, key: string) {
  for (const row of rows) {
    if (row.key === key) {
      return row;
    }
    if (row.children) {
      const nested = findRow(row.children, key);
      if (nested) {
        return nested;
      }
    }
  }
  return null;
}
