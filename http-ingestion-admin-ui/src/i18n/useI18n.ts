import { useContext } from 'react';
import { I18nContext } from './I18nProvider';

export function useI18n() {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error('useI18n must be used within I18nProvider');
  }
  return context;
}

export function enumLabel(
  messages: Record<string, string>,
  value: string | null | undefined,
  fallback = '-',
) {
  if (!value) return fallback;
  return messages[value] ?? value;
}
