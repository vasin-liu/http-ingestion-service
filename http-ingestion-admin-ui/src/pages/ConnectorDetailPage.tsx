import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { api, ConnectorDetail, ConnectorSchedule, ConnectorState, JobRun, JobRunDetail } from '../api/client';
import {
  jobStatusColor,
  jobStatusLabel,
  modeLabel,
  runTypeLabel,
  stageLabel,
} from '../i18n/labels';
import { useI18n } from '../i18n/useI18n';

function formatDuration(ms: number | null | undefined) {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export default function ConnectorDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { t, messages } = useI18n();
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<ConnectorDetail | null>(null);
  const [state, setState] = useState<ConnectorState | null>(null);
  const [jobs, setJobs] = useState<JobRun[]>([]);
  const [syncing, setSyncing] = useState(false);
  const [sampleOpen, setSampleOpen] = useState(false);
  const [sampleForm] = Form.useForm();
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedJob, setSelectedJob] = useState<JobRun | null>(null);
  const [jobDetails, setJobDetails] = useState<JobRunDetail[]>([]);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [schedule, setSchedule] = useState<ConnectorSchedule | null>(null);
  const [scheduleForm] = Form.useForm<{ enabled: boolean; expression: string }>();
  const [scheduleSaving, setScheduleSaving] = useState(false);

  const load = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [connector, connectorState, jobRuns] = await Promise.all([
        api.getConnector(id),
        api.getState(id),
        api.listJobs(id),
      ]);
      setDetail(connector);
      setState(connectorState);
      setJobs(jobRuns);
      if (connector.latestPublishedVersion != null) {
        try {
          setSchedule(await api.getSchedule(id));
        } catch {
          setSchedule(null);
        }
      } else {
        setSchedule(null);
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('common.loadingFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    const timer = setInterval(() => void load(), 5000);
    return () => clearInterval(timer);
  }, [id]);

  useEffect(() => {
    const scheduleConfig = (detail?.draftConfig?.schedule ?? {}) as Record<string, unknown>;
    scheduleForm.setFieldsValue({
      enabled: Boolean(scheduleConfig.enabled),
      expression: String(scheduleConfig.expression ?? '0 0/5 * * * ?'),
    });
  }, [detail, scheduleForm]);

  const exportConfig = async () => {
    if (!id) return;
    try {
      const bundle = await api.exportConnector(id);
      const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${id}-connector.json`;
      anchor.click();
      URL.revokeObjectURL(url);
      message.success(t('importExport.exportDone'));
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('common.loadingFailed'));
    }
  };

  const publish = async () => {
    if (!id) return;
    try {
      await api.publishConnector(id);
      message.success(t('connectorDetail.published'));
      await load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.publishFailed'));
    }
  };

  const remove = async () => {
    if (!id) return;
    try {
      await api.deleteConnector(id);
      message.success(t('connectorDetail.deleted'));
      navigate('/connectors');
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.deleteFailed'));
    }
  };

  const sync = async (type: 'full' | 'incremental') => {
    if (!id) return;
    setSyncing(true);
    try {
      const result = await api.triggerSync(id, type);
      message.success(
        t('connectorDetail.syncTriggered', {
          type: runTypeLabel(messages, type),
          jobId: result.jobRunId,
        }),
      );
      await load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.syncFailed'));
    } finally {
      setSyncing(false);
    }
  };

  const runSample = async () => {
    if (!id) return;
    const values = await sampleForm.validateFields();
    setSyncing(true);
    try {
      const result = await api.triggerSampleSync(id, values.limit, values.writeSink);
      message.success(t('connectorDetail.sampleTriggered', { jobId: result.jobRunId }));
      setSampleOpen(false);
      await load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.sampleFailed'));
    } finally {
      setSyncing(false);
    }
  };

  const resetState = async () => {
    if (!id) return;
    try {
      await api.resetState(id);
      message.success(t('connectorDetail.watermarkReset'));
      await load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.resetFailed'));
    }
  };

  const pauseSchedule = async () => {
    if (!id) return;
    try {
      setSchedule(await api.pauseSchedule(id));
      message.success(t('connectorDetail.schedulePausedSuccess'));
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.scheduleActionFailed'));
    }
  };

  const resumeSchedule = async () => {
    if (!id) return;
    try {
      setSchedule(await api.resumeSchedule(id));
      message.success(t('connectorDetail.scheduleResumedSuccess'));
      await load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.scheduleActionFailed'));
    }
  };

  const saveSchedule = async () => {
    if (!id || !detail) return;
    const values = await scheduleForm.validateFields();
    setScheduleSaving(true);
    try {
      const existingSchedule = (detail.draftConfig.schedule ?? {}) as Record<string, unknown>;
      const config = {
        ...detail.draftConfig,
        schedule: {
          ...existingSchedule,
          type: String(existingSchedule.type ?? 'cron'),
          enabled: values.enabled,
          expression: values.expression,
        },
      };
      await api.updateConnector(id, {
        id,
        name: detail.name,
        mode: detail.mode,
        config,
      });
      await api.publishConnector(id);
      message.success(t('connectorDetail.scheduleSaved'));
      await load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.scheduleSaveFailed'));
    } finally {
      setScheduleSaving(false);
    }
  };

  const scheduleLabel = () => {
    if (!schedule?.registered) {
      return t('connectorDetail.scheduleDisabled');
    }
    if (schedule.paused) {
      return t('connectorDetail.schedulePaused');
    }
    return t('connectorDetail.scheduleRunning');
  };

  const openJobDetails = async (job: JobRun) => {
    if (!id) return;
    setSelectedJob(job);
    setDetailOpen(true);
    setDetailsLoading(true);
    try {
      setJobDetails(await api.getJobDetails(id, job.id));
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorDetail.loadDetailsFailed'));
      setJobDetails([]);
    } finally {
      setDetailsLoading(false);
    }
  };

  const jobColumns = useMemo(
    () => [
      { title: t('common.id'), dataIndex: 'id', width: 80 },
      {
        title: t('connectorDetail.runType'),
        dataIndex: 'runType',
        render: (v: string) => runTypeLabel(messages, v),
      },
      {
        title: t('connectorDetail.status'),
        dataIndex: 'status',
        render: (v: string) => (
          <Tag color={jobStatusColor(v)}>{jobStatusLabel(messages, v)}</Tag>
        ),
      },
      { title: t('connectorDetail.recordsOk'), dataIndex: 'recordsOk' },
      { title: t('connectorDetail.recordsFailed'), dataIndex: 'recordsFailed' },
      {
        title: t('connectorDetail.duration'),
        dataIndex: 'durationMs',
        render: (v: number | null) => formatDuration(v),
      },
      {
        title: t('connectorDetail.startedAt'),
        dataIndex: 'startedAt',
        render: (v: string) => new Date(v).toLocaleString(),
      },
      { title: t('connectorDetail.error'), dataIndex: 'errorMessage', ellipsis: true },
    ],
    [messages, t],
  );

  if (!id) return null;

  const http = (detail?.draftConfig?.http ?? {}) as Record<string, unknown>;
  const sink = (detail?.draftConfig?.sink as Record<string, unknown>) ?? {};
  const target = (sink.target as Record<string, unknown>) ?? {};
  const successJobs = jobs.filter((job) => job.status === 'success').length;

  return (
    <>
      <section className="page-hero">
        <Space wrap style={{ marginBottom: 12 }}>
          <Tag color="processing">{modeLabel(messages, detail?.mode)}</Tag>
          {detail?.latestPublishedVersion != null ? (
            <Tag color="success">v{detail.latestPublishedVersion}</Tag>
          ) : (
            <Tag>{t('connectorDetail.publishedVersion')}: -</Tag>
          )}
        </Space>
        <Typography.Title level={1} className="page-hero-title">
          {detail?.name ?? id}
        </Typography.Title>
        <Typography.Paragraph className="page-hero-subtitle" style={{ marginBottom: 0 }}>
          {String(http.url ?? '-')}
        </Typography.Paragraph>
        <div className="action-bar">
          <Button size="large" onClick={() => navigate(`/connectors/${id}/edit`)}>
            {t('common.edit')}
          </Button>
          <Button size="large" data-testid="export-config-btn" onClick={() => void exportConfig()}>
            {t('importExport.export')}
          </Button>
          <Button size="large" loading={syncing} data-testid="incremental-sync-btn" onClick={() => void sync('incremental')}>
            {t('connectorDetail.incrementalSync')}
          </Button>
          <Button size="large" loading={syncing} data-testid="full-sync-btn" onClick={() => void sync('full')}>
            {t('connectorDetail.fullSync')}
          </Button>
          <Button size="large" loading={syncing} data-testid="sample-run-btn" onClick={() => setSampleOpen(true)}>
            {t('connectorDetail.sampleRun')}
          </Button>
          <Popconfirm
            title={t('connectorDetail.resetConfirmTitle')}
            description={t('connectorDetail.resetConfirmDesc')}
            onConfirm={() => void resetState()}
          >
            <Button size="large">{t('connectorDetail.resetWatermark')}</Button>
          </Popconfirm>
          {schedule?.registered && !schedule.paused ? (
            <Button size="large" data-testid="schedule-pause" onClick={() => void pauseSchedule()}>
              {t('connectorDetail.pauseSchedule')}
            </Button>
          ) : null}
          {schedule?.paused ? (
            <Button size="large" data-testid="schedule-resume" onClick={() => void resumeSchedule()}>
              {t('connectorDetail.resumeSchedule')}
            </Button>
          ) : null}
          <Button type="primary" size="large" data-testid="publish-btn" onClick={() => void publish()}>
            {t('common.publish')}
          </Button>
          <Button danger size="large" onClick={() => void remove()}>
            {t('common.delete')}
          </Button>
        </div>
      </section>

      <div className="metric-grid">
        <div className="metric-tile">
          <span className="metric-tile-label">{t('connectorDetail.runHistory')}</span>
          <span className="metric-tile-value">{jobs.length}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-tile-label">{t('connectorDetail.status')}</span>
          <span className="metric-tile-value">{successJobs}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-tile-label">{t('connectorDetail.recordsOk')}</span>
          <span className="metric-tile-value">
            {jobs.reduce((sum, job) => sum + job.recordsOk, 0)}
          </span>
        </div>
      </div>

      <Card loading={loading} className="glass-card" style={{ marginBottom: 20 }}>
        <div className="info-grid">
          <div className="info-tile">
            <span className="info-tile-label">{t('common.id')}</span>
            <span className="info-tile-value">{detail?.id}</span>
          </div>
          <div className="info-tile">
            <span className="info-tile-label">{t('connectorDetail.sinkTable')}</span>
            <span className="info-tile-value">
              {target.schema ? `${target.schema}.` : ''}
              {String(target.table ?? '-')}
            </span>
          </div>
          <div className="info-tile">
            <span className="info-tile-label">{t('connectorDetail.watermark')}</span>
            <span className="info-tile-value">
              <Typography.Text code>{state?.watermarkJson ?? '-'}</Typography.Text>
            </span>
          </div>
          <div className="info-tile">
            <span className="info-tile-label">{t('connectorDetail.scheduleTitle')}</span>
            <span className="info-tile-value">
              <Tag color={schedule?.paused ? 'default' : schedule?.registered ? 'success' : 'default'}>
                {scheduleLabel()}
              </Tag>
              {schedule?.scheduleType ? (
                <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
                  {schedule.scheduleType}
                  {schedule.expression ? ` · ${schedule.expression}` : ''}
                </Typography.Text>
              ) : null}
            </span>
          </div>
          <div className="info-tile">
            <span className="info-tile-label">{t('connectorDetail.watermarkUpdatedAt')}</span>
            <span className="info-tile-value">
              {state?.updatedAt ? new Date(state.updatedAt).toLocaleString() : '-'}
            </span>
          </div>
        </div>
      </Card>

      <Card className="glass-card" title={t('connectorDetail.scheduleEditTitle')} style={{ marginBottom: 20 }}>
        <Alert type="info" showIcon message={t('connectorDetail.scheduleEditHint')} style={{ marginBottom: 16 }} />
        <Form form={scheduleForm} layout="vertical">
          <Form.Item name="enabled" label={t('connectorWizard.scheduleEnabled')} valuePropName="checked">
            <Switch data-testid="schedule-enabled" />
          </Form.Item>
          <Form.Item
            name="expression"
            label={t('connectorWizard.cronExpression')}
            rules={[{ required: true, message: t('connectorDetail.scheduleExpressionRequired') }]}
          >
            <Input placeholder="0 0/5 * * * ?" data-testid="schedule-expression" />
          </Form.Item>
          <Button
            type="primary"
            loading={scheduleSaving}
            data-testid="schedule-save-btn"
            onClick={() => void saveSchedule()}
          >
            {t('connectorDetail.scheduleSavePublish')}
          </Button>
        </Form>
      </Card>

      <Card className="glass-card" title={t('connectorDetail.runHistory')}>
        <Table
          rowKey="id"
          size="middle"
          pagination={{ pageSize: 10 }}
          dataSource={jobs}
          onRow={(record) => ({
            onClick: () => void openJobDetails(record),
            style: { cursor: 'pointer' },
          })}
          columns={jobColumns}
        />
        <Typography.Paragraph style={{ marginTop: 16, marginBottom: 0 }}>
          <Button type="link" onClick={() => navigate(`/connectors/${id}/edit`)} style={{ paddingInline: 0 }}>
            {t('connectorDetail.continueEdit')}
          </Button>
        </Typography.Paragraph>
      </Card>

      <Modal
        title={t('connectorDetail.sampleModalTitle')}
        open={sampleOpen}
        onCancel={() => setSampleOpen(false)}
        onOk={() => void runSample()}
        confirmLoading={syncing}
        okText={t('connectorDetail.sampleStart')}
      >
        <Form form={sampleForm} layout="vertical" initialValues={{ limit: 10, writeSink: false }}>
          <Form.Item name="limit" label={t('connectorDetail.sampleLimit')} rules={[{ required: true }]}>
            <InputNumber min={1} max={1000} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="writeSink" label={t('connectorDetail.sampleWriteSink')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Typography.Text type="secondary">{t('connectorDetail.sampleHint')}</Typography.Text>
        </Form>
      </Modal>

      <Modal
        title={
          selectedJob
            ? t('connectorDetail.troubleshootTitle', { jobId: selectedJob.id })
            : t('connectorDetail.troubleshootTitle', { jobId: '-' })
        }
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={760}
      >
        {selectedJob && (
          <div className="info-grid" style={{ marginBottom: 16 }}>
            <div className="info-tile">
              <span className="info-tile-label">{t('connectorDetail.runType')}</span>
              <span className="info-tile-value">{runTypeLabel(messages, selectedJob.runType)}</span>
            </div>
            <div className="info-tile">
              <span className="info-tile-label">{t('connectorDetail.status')}</span>
              <span className="info-tile-value">
                <Tag color={jobStatusColor(selectedJob.status)}>
                  {jobStatusLabel(messages, selectedJob.status)}
                </Tag>
              </span>
            </div>
            <div className="info-tile">
              <span className="info-tile-label">{t('connectorDetail.error')}</span>
              <span className="info-tile-value">{selectedJob.errorMessage ?? '-'}</span>
            </div>
          </div>
        )}
        <Table
          rowKey="id"
          size="small"
          loading={detailsLoading}
          pagination={false}
          dataSource={jobDetails}
          locale={{ emptyText: t('connectorDetail.troubleshootEmpty') }}
          columns={[
            {
              title: t('connectorDetail.stage'),
              dataIndex: 'stage',
              width: 90,
              render: (v: string) => stageLabel(messages, v),
            },
            {
              title: t('connectorDetail.pageNumber'),
              dataIndex: 'pageNumber',
              width: 60,
              render: (v) => v ?? '-',
            },
            {
              title: t('connectorDetail.recordIndex'),
              dataIndex: 'recordIndex',
              width: 60,
              render: (v) => v ?? '-',
            },
            { title: t('connectorDetail.message'), dataIndex: 'message', ellipsis: true },
          ]}
        />
        {jobDetails.some((d) => d.sampleJson) && (
          <Typography.Paragraph style={{ marginTop: 16 }}>
            <Typography.Text strong>{t('connectorDetail.sampleData')}</Typography.Text>
            <pre className="code-block preview-block" style={{ maxHeight: 240 }}>
              {jobDetails
                .filter((d) => d.sampleJson)
                .map((d) => d.sampleJson)
                .join('\n---\n')}
            </pre>
          </Typography.Paragraph>
        )}
      </Modal>
    </>
  );
}
