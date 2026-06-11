import { ConfigProvider } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { ReactNode, createContext, useCallback, useEffect, useMemo, useState } from 'react';
import messagesEn from './locales/en-US';
import messagesZh from './locales/zh-CN';
import { Locale, LocaleMessages } from './types';

const STORAGE_KEY = 'http-ingestion.locale';

type I18nContextValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
  messages: LocaleMessages;
};

export const I18nContext = createContext<I18nContextValue | null>(null);

function readStoredLocale(): Locale {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === 'en-US' ? 'en-US' : 'zh-CN';
}

function resolveMessage(messages: LocaleMessages, key: string): string | undefined {
  return key.split('.').reduce<unknown>((current, part) => {
    if (current && typeof current === 'object' && part in (current as Record<string, unknown>)) {
      return (current as Record<string, unknown>)[part];
    }
    return undefined;
  }, messages) as string | undefined;
}

function formatMessage(template: string, params?: Record<string, string | number>) {
  if (!params) return template;
  return Object.entries(params).reduce(
    (result, [name, value]) => result.replaceAll(`{${name}}`, String(value)),
    template,
  );
}

export default function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => readStoredLocale());

  const messages = locale === 'en-US' ? messagesEn : messagesZh;
  const antdLocale = locale === 'en-US' ? enUS : zhCN;

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    localStorage.setItem(STORAGE_KEY, next);
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const resolved = resolveMessage(messages, key);
      if (resolved == null) return key;
      return formatMessage(resolved, params);
    },
    [messages],
  );

  useEffect(() => {
    document.documentElement.lang = locale === 'en-US' ? 'en' : 'zh-CN';
    document.title = messages.app.title;
  }, [locale, messages.app.title]);

  const value = useMemo(
    () => ({ locale, setLocale, t, messages }),
    [locale, setLocale, t, messages],
  );

  return (
    <I18nContext.Provider value={value}>
      <ConfigProvider locale={antdLocale}>{children}</ConfigProvider>
    </I18nContext.Provider>
  );
}
