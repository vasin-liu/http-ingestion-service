import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Card, Table, Tag, Typography, message } from 'antd';
import { api, IngestionStats, JobRun } from '../api/client';
import { jobStatusColor, jobStatusLabel, runTypeLabel } from '../i18n/labels';
import { useI18n } from '../i18n/useI18n';

export default function StatsPage() {
  const { t, messages } = useI18n();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<IngestionStats | null>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        setStats(await api.getStats(50));
      } catch (e) {
        message.error(e instanceof Error ? e.message : t('common.loadingFailed'));
      } finally {
        setLoading(false);
      }
    })();
  }, [t]);

  return (
    <>
      <section className="page-hero">
        <Typography.Title level={1} className="page-hero-title" data-testid="stats-page-title">
          {t('statsPage.title')}
        </Typography.Title>
        <Typography.Paragraph className="page-hero-subtitle" style={{ marginBottom: 0 }}>
          {t('statsPage.subtitle')}
        </Typography.Paragraph>
      </section>

      <div className="metric-grid">
        <div className="metric-tile">
          <span className="metric-tile-label">{t('statsPage.totalJobs')}</span>
          <span className="metric-tile-value">{stats?.totalJobs ?? 0}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-tile-label">{t('statsPage.successJobs')}</span>
          <span className="metric-tile-value">{stats?.successJobs ?? 0}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-tile-label">{t('statsPage.recordsOk')}</span>
          <span className="metric-tile-value">{stats?.recordsOk ?? 0}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-tile-label">{t('statsPage.recordsFailed')}</span>
          <span className="metric-tile-value">{stats?.recordsFailed ?? 0}</span>
        </div>
      </div>

      <Card loading={loading} className="glass-card" title={t('statsPage.byConnector')} style={{ marginBottom: 20 }}>
        <Table
          rowKey="connectorId"
          size="middle"
          pagination={false}
          dataSource={stats?.byConnector ?? []}
          columns={[
            {
              title: t('common.id'),
              dataIndex: 'connectorId',
              render: (id: string) => <Link to={`/connectors/${id}`}>{id}</Link>,
            },
            { title: t('statsPage.totalJobs'), dataIndex: 'totalJobs' },
            { title: t('statsPage.successJobs'), dataIndex: 'successJobs' },
            { title: t('statsPage.failedJobs'), dataIndex: 'failedJobs' },
            { title: t('statsPage.recordsOk'), dataIndex: 'recordsOk' },
            { title: t('statsPage.recordsFailed'), dataIndex: 'recordsFailed' },
          ]}
        />
      </Card>

      <Card loading={loading} className="glass-card" title={t('statsPage.recentJobs')}>
        <Table
          rowKey="id"
          size="middle"
          dataSource={stats?.recentJobs ?? []}
          columns={[
            { title: t('common.id'), dataIndex: 'id', width: 80 },
            {
              title: t('nav.connectors'),
              dataIndex: 'connectorId',
              render: (id: string) => <Link to={`/connectors/${id}`}>{id}</Link>,
            },
            {
              title: t('connectorDetail.runType'),
              dataIndex: 'runType',
              render: (v: string) => runTypeLabel(messages, v),
            },
            {
              title: t('connectorDetail.status'),
              dataIndex: 'status',
              render: (v: string) => <Tag color={jobStatusColor(v)}>{jobStatusLabel(messages, v)}</Tag>,
            },
            { title: t('connectorDetail.recordsOk'), dataIndex: 'recordsOk' },
            { title: t('connectorDetail.recordsFailed'), dataIndex: 'recordsFailed' },
          ]}
        />
      </Card>
    </>
  );
}
