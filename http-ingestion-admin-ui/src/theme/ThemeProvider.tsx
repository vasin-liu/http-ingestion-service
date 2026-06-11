import { ConfigProvider, theme as antdTheme } from 'antd';
import {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'http-ingestion-theme';

type ThemeContextValue = {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  toggleMode: () => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readInitialMode(): ThemeMode {
  if (typeof window === 'undefined') {
    return 'dark';
  }
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function buildAntdTheme(mode: ThemeMode) {
  const isDark = mode === 'dark';
  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: isDark ? '#38bdf8' : '#0071e3',
      colorInfo: isDark ? '#818cf8' : '#5856d6',
      colorSuccess: isDark ? '#34d399' : '#248a3d',
      colorWarning: isDark ? '#fbbf24' : '#b45309',
      colorError: isDark ? '#fb7185' : '#d70015',
      colorBgLayout: 'transparent',
      colorBgContainer: isDark ? 'rgba(15, 23, 42, 0.55)' : 'rgba(255, 255, 255, 0.72)',
      colorBgElevated: isDark ? 'rgba(17, 24, 39, 0.92)' : 'rgba(255, 255, 255, 0.94)',
      colorBorder: isDark ? 'rgba(148, 163, 184, 0.22)' : 'rgba(15, 23, 42, 0.12)',
      colorText: isDark ? '#f8fafc' : '#0f172a',
      colorTextSecondary: isDark ? 'rgba(248, 250, 252, 0.72)' : 'rgba(15, 23, 42, 0.62)',
      colorTextTertiary: isDark ? 'rgba(248, 250, 252, 0.52)' : 'rgba(15, 23, 42, 0.45)',
      colorLink: isDark ? '#7dd3fc' : '#0071e3',
      borderRadius: 14,
      borderRadiusLG: 18,
      borderRadiusSM: 10,
      fontFamily:
        "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Segoe UI', system-ui, sans-serif",
      controlHeight: 40,
      wireframe: false,
    },
    components: {
      Layout: {
        headerBg: 'transparent',
        bodyBg: 'transparent',
      },
      Card: {
        colorBgContainer: isDark ? 'rgba(15, 23, 42, 0.55)' : 'rgba(255, 255, 255, 0.72)',
      },
      Button: {
        primaryShadow: isDark ? '0 8px 24px rgba(56, 189, 248, 0.28)' : '0 8px 24px rgba(0, 113, 227, 0.22)',
        defaultBg: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(255,255,255,0.82)',
        defaultColor: isDark ? '#f8fafc' : '#0f172a',
        defaultBorderColor: isDark ? 'rgba(148, 163, 184, 0.28)' : 'rgba(15, 23, 42, 0.14)',
      },
      Table: {
        headerBg: isDark ? 'rgba(30, 41, 59, 0.72)' : 'rgba(241, 245, 249, 0.92)',
        rowHoverBg: isDark ? 'rgba(56, 189, 248, 0.08)' : 'rgba(0, 113, 227, 0.06)',
        borderColor: isDark ? 'rgba(148, 163, 184, 0.16)' : 'rgba(15, 23, 42, 0.08)',
      },
      Modal: {
        contentBg: isDark ? 'rgba(17, 24, 39, 0.96)' : 'rgba(255, 255, 255, 0.98)',
        headerBg: isDark ? 'rgba(17, 24, 39, 0.96)' : 'rgba(255, 255, 255, 0.98)',
      },
      Segmented: {
        itemSelectedBg: isDark ? 'rgba(56, 189, 248, 0.22)' : '#ffffff',
        itemSelectedColor: isDark ? '#f8fafc' : '#0f172a',
        trackBg: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(15, 23, 42, 0.06)',
      },
      Steps: {
        colorPrimary: isDark ? '#38bdf8' : '#0071e3',
      },
      Tag: {
        defaultBg: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(15, 23, 42, 0.06)',
      },
    },
  };
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(() => readInitialMode());

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    window.localStorage.setItem(STORAGE_KEY, next);
  }, []);

  const toggleMode = useCallback(() => {
    setMode(mode === 'dark' ? 'light' : 'dark');
  }, [mode, setMode]);

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
    document.documentElement.style.colorScheme = mode;
  }, [mode]);

  const contextValue = useMemo(
    () => ({ mode, setMode, toggleMode }),
    [mode, setMode, toggleMode],
  );

  const antdConfig = useMemo(() => buildAntdTheme(mode), [mode]);

  return (
    <ThemeContext.Provider value={contextValue}>
      <ConfigProvider theme={antdConfig}>{children}</ConfigProvider>
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return ctx;
}
