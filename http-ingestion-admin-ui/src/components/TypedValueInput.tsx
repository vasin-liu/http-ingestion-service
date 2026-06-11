import { Input, InputNumber, Select, Switch } from 'antd';
import TextArea from 'antd/es/input/TextArea';
import type { ParamValueType } from '../utils/schemaForm';
import { parseParamValue, stringifyParamValue } from '../utils/schemaForm';

interface TypedValueInputProps {
  type: ParamValueType;
  value: string;
  onChange: (value: string) => void;
  testId?: string;
  placeholder?: string;
  compact?: boolean;
}

export default function TypedValueInput({
  type,
  value,
  onChange,
  testId,
  placeholder,
  compact = false,
}: TypedValueInputProps) {
  const inputSize = compact ? 'small' : undefined;
  switch (type) {
    case 'boolean':
      return (
        <Switch
          size={compact ? 'small' : undefined}
          checked={value === 'true' || value === '1'}
          data-testid={testId}
          onChange={(checked) => onChange(String(checked))}
        />
      );
    case 'integer':
      return (
        <InputNumber
          size={inputSize}
          precision={0}
          style={{ width: '100%' }}
          value={value === '' ? undefined : Number.parseInt(value, 10)}
          data-testid={testId}
          placeholder={placeholder}
          onChange={(next) => onChange(next == null ? '' : String(next))}
        />
      );
    case 'number':
      return (
        <InputNumber
          size={inputSize}
          style={{ width: '100%' }}
          value={value === '' ? undefined : Number.parseFloat(value)}
          data-testid={testId}
          placeholder={placeholder}
          onChange={(next) => onChange(next == null ? '' : String(next))}
        />
      );
    case 'array':
    case 'object':
      return (
        <TextArea
          size={inputSize}
          rows={type === 'object' ? 3 : 2}
          value={value}
          placeholder={type === 'array' ? '[1,2,3]' : '{"key":"value"}'}
          data-testid={testId}
          onChange={(event) => onChange(event.target.value)}
        />
      );
    default:
      return (
        <Input
          size={inputSize}
          value={value}
          placeholder={placeholder}
          data-testid={testId}
          onChange={(event) => onChange(event.target.value)}
        />
      );
  }
}

interface TypedBodyValueInputProps {
  type: ParamValueType;
  value: unknown;
  onChange: (value: unknown) => void;
  testId?: string;
}

export function TypedBodyValueInput({ type, value, onChange, testId }: TypedBodyValueInputProps) {
  const stringValue = stringifyParamValue(value, type);
  return (
    <TypedValueInput
      type={type}
      value={stringValue}
      testId={testId}
      onChange={(next) => onChange(parseParamValue(next, type))}
    />
  );
}
