import { ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Segmented } from 'antd';
import { ApiOutlined, BulbOutlined, MoonOutlined } from '@ant-design/icons';
import { useI18n } from '../i18n/useI18n';
import type { Locale } from '../i18n/types';
import { ThemeMode, useTheme } from '../theme/ThemeProvider';

const { Content } = Layout;

export default function AppShell({ children }: { children: ReactNode }) {
  const { locale, setLocale, t } = useI18n();
  const { mode, setMode } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout className="app-shell">
      <header className="app-header">
        <Link to="/connectors" className="app-brand">
          <div className="app-brand-mark">
            <ApiOutlined />
          </div>
          <div className="app-brand-copy">
            <h1 className="app-brand-title">{t('app.title')}</h1>
            <p className="app-brand-subtitle">{t('app.subtitle')}</p>
          </div>
        </Link>

        <div className="app-header-actions">
          <Segmented
            className="header-switch"
            data-testid="nav-segmented"
            value={location.pathname.startsWith('/stats') ? 'stats' : 'connectors'}
            options={[
              { label: t('nav.connectors'), value: 'connectors' },
              { label: t('nav.stats'), value: 'stats' },
            ]}
            onChange={(value) => navigate(value === 'stats' ? '/stats' : '/connectors')}
          />
          <Segmented
            className="header-switch"
            data-testid="theme-switch"
            value={mode}
            onChange={(value) => setMode(value as ThemeMode)}
            options={[
              { label: t('theme.light'), value: 'light', icon: <BulbOutlined /> },
              { label: t('theme.dark'), value: 'dark', icon: <MoonOutlined /> },
            ]}
          />
          <Segmented
            className="header-switch"
            data-testid="locale-switch"
            value={locale}
            onChange={(value) => setLocale(value as Locale)}
            options={[
              { label: '中文', value: 'zh-CN' },
              { label: 'EN', value: 'en-US' },
            ]}
          />
        </div>
      </header>
      <Content className="app-content">{children}</Content>
    </Layout>
  );
}
