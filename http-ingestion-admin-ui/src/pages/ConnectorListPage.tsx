import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Empty, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import { ApiOutlined, PlusOutlined, RocketOutlined } from '@ant-design/icons';
import { api, ConnectorSummary, ConnectorTemplate } from '../api/client';
import OpenApiImportModal from '../components/OpenApiImportModal';
import { modeLabel, versionStatusLabel } from '../i18n/labels';
import { useI18n } from '../i18n/useI18n';

export default function ConnectorListPage() {
  const { t, messages } = useI18n();
  const [loading, setLoading] = useState(true);
  const [rows, setRows] = useState<ConnectorSummary[]>([]);
  const [templates, setTemplates] = useState<ConnectorTemplate[]>([]);
  const [templateOpen, setTemplateOpen] = useState(false);
  const [openApiOpen, setOpenApiOpen] = useState(false);
  const importInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    try {
      setRows(await api.listConnectors());
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('common.loadingFailed'));
    } finally {
      setLoading(false);
    }
  };

  const loadTemplates = async () => {
    try {
      setTemplates(await api.listTemplates());
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('connectorList.loadTemplatesFailed'));
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const integrationTemplates = useMemo(
    () => templates.filter((template) => template.category === 'integration'),
    [templates],
  );
  const exampleTemplates = useMemo(
    () => templates.filter((template) => template.category === 'example'),
    [templates],
  );

  const stats = useMemo(() => {
    const published = rows.filter((row) => row.latestPublishedVersion != null).length;
    const drafts = rows.filter((row) => row.hasDraft).length;
    return { total: rows.length, published, drafts };
  }, [rows]);

  const openTemplateModal = () => {
    void loadTemplates();
    setTemplateOpen(true);
  };

  const createFromTemplate = (template: ConnectorTemplate) => {
    setTemplateOpen(false);
    navigate(`/connectors/new?template=${template.id}`);
  };

  const handleImportFile = async (file?: File) => {
    if (!file) return;
    try {
      const text = await file.text();
      const bundle = JSON.parse(text) as {
        id: string;
        name: string;
        mode: string;
        config: Record<string, unknown>;
      };
      await api.importConnector({
        id: bundle.id,
        name: bundle.name,
        mode: bundle.mode,
        config: bundle.config,
        overwrite: false,
        publishAfterImport: false,
      });
      message.success(t('importExport.importDone'));
      await load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('importExport.importFailed'));
    } finally {
      if (importInputRef.current) {
        importInputRef.current.value = '';
      }
    }
  };

  return (
    <>
      <section className="page-hero">
        <Typography.Title level={1} className="page-hero-title">
          {t('connectorList.title')}
        </Typography.Title>
        <p className="page-hero-subtitle">{t('connectorList.subtitle')}</p>
        <div className="action-bar">
          <Button icon={<RocketOutlined />} size="large" data-testid="create-from-template-btn" onClick={() => openTemplateModal()}>
            {t('connectorList.createFromTemplate')}
          </Button>
          <Button icon={<ApiOutlined />} size="large" data-testid="import-openapi-btn" onClick={() => setOpenApiOpen(true)}>
            {t('connectorList.importFromOpenApi')}
          </Button>
          <Button size="large" data-testid="import-config-btn" onClick={() => importInputRef.current?.click()}>
            {t('importExport.import')}
          </Button>
          <input
            ref={importInputRef}
            type="file"
            accept="application/json"
            style={{ display: 'none' }}
            onChange={(e) => void handleImportFile(e.target.files?.[0])}
          />
          <Button type="primary" icon={<PlusOutlined />} size="large" data-testid="create-new-btn" onClick={() => navigate('/connectors/new')}>
            {t('connectorList.createNew')}
          </Button>
        </div>
      </section>

      <div className="metric-grid">
        <div className="metric-tile">
          <span className="metric-tile-label">{t('connectorList.totalConnectors')}</span>
          <span className="metric-tile-value">{stats.total}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-tile-label">{t('connectorList.publishedCount')}</span>
          <span className="metric-tile-value">{stats.published}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-tile-label">{t('connectorList.draftCount')}</span>
          <span className="metric-tile-value">{stats.drafts}</span>
        </div>
      </div>

      <Spin spinning={loading}>
        {rows.length === 0 && !loading ? (
          <Card className="glass-card">
            <Empty description={t('connectorList.empty')} />
          </Card>
        ) : (
          <div className="connector-grid">
            {rows.map((record) => (
              <Card
                key={record.id}
                className="glass-card connector-card"
                onClick={() => navigate(`/connectors/${record.id}`)}
              >
                <Typography.Title level={4} className="connector-card-title">
                  {record.name}
                </Typography.Title>
                <Typography.Text type="secondary">{record.id}</Typography.Text>
                <div className="connector-card-meta">
                  <Tag color="processing">{modeLabel(messages, record.mode)}</Tag>
                  {record.hasDraft ? (
                    <Tag color="gold">{versionStatusLabel(messages, 'draft')}</Tag>
                  ) : null}
                  {record.latestPublishedVersion != null ? (
                    <Tag color="success">v{record.latestPublishedVersion}</Tag>
                  ) : null}
                </div>
                <Typography.Paragraph type="secondary" style={{ marginTop: 14, marginBottom: 0 }}>
                  {t('common.updatedAt')}: {new Date(record.updatedAt).toLocaleString()}
                </Typography.Paragraph>
                <Space style={{ marginTop: 14 }} onClick={(event) => event.stopPropagation()}>
                  <Button size="small" type="primary" ghost onClick={() => navigate(`/connectors/${record.id}`)}>
                    {t('common.edit')}
                  </Button>
                </Space>
              </Card>
            ))}
          </div>
        )}
      </Spin>

      <Modal
        title={t('connectorList.templateModalTitle')}
        open={templateOpen}
        onCancel={() => setTemplateOpen(false)}
        footer={null}
        width={860}
      >
        {integrationTemplates.length > 0 ? (
          <>
            <Typography.Title level={5}>{t('connectorList.integrationTemplatesTitle')}</Typography.Title>
            <div className="template-grid" style={{ marginBottom: 24 }}>
              {integrationTemplates.map((template) => (
                <Card
                  key={template.id}
                  size="small"
                  hoverable
                  className="glass-card template-card"
                  data-testid={`template-${template.id}`}
                  onClick={() => createFromTemplate(template)}
                >
                  <Typography.Text strong style={{ color: 'var(--text-primary)' }}>
                    {template.name}
                  </Typography.Text>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 8 }}>
                    {template.description}
                  </Typography.Paragraph>
                  <Tag color="success" style={{ marginTop: 10 }}>
                    {t('connectorList.integrationTag')}
                  </Tag>
                  <Tag color="processing" style={{ marginTop: 10 }}>
                    {modeLabel(messages, template.mode)}
                  </Tag>
                </Card>
              ))}
            </div>
          </>
        ) : null}
        <Typography.Title level={5}>{t('connectorList.exampleTemplatesTitle')}</Typography.Title>
        <div className="template-grid">
          {exampleTemplates.map((template) => (
            <Card
              key={template.id}
              size="small"
              hoverable
              className="glass-card template-card"
              data-testid={`template-${template.id}`}
              onClick={() => createFromTemplate(template)}
            >
              <Typography.Text strong style={{ color: 'var(--text-primary)' }}>
                {template.name}
              </Typography.Text>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 8 }}>
                {template.description}
              </Typography.Paragraph>
              <Tag color="gold" style={{ marginTop: 10 }}>
                {t('connectorList.exampleTag')}
              </Tag>
              <Tag color="processing" style={{ marginTop: 10 }}>
                {modeLabel(messages, template.mode)}
              </Tag>
            </Card>
          ))}
        </div>
      </Modal>
      <OpenApiImportModal open={openApiOpen} onClose={() => setOpenApiOpen(false)} />
    </>
  );
}
