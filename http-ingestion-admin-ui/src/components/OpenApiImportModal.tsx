import { useMemo, useState } from 'react';
import { Button, Input, Modal, Select, Space, Table, Tabs, Typography, Upload, message } from 'antd';
import type { UploadProps } from 'antd';
import { CloseOutlined, InboxOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api, OpenApiOperation, OpenApiParseResult } from '../api/client';
import { useI18n } from '../i18n/useI18n';
import {
  buildConnectorRequestFromOperation,
  connectorIdFromOperation,
  filterOperations,
  operationRowKey,
  operationSearchText,
  toWizardImportPayload,
} from '../utils/openApiImport';

export const OPENAPI_IMPORT_STORAGE_KEY = 'http-ingestion-openapi-import';

const { Dragger } = Upload;
const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const;

interface OpenApiImportModalProps {
  open: boolean;
  onClose: () => void;
}

export default function OpenApiImportModal({ open, onClose }: OpenApiImportModalProps) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [specText, setSpecText] = useState('');
  const [specUrl, setSpecUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<OpenApiParseResult | null>(null);
  const [methodFilter, setMethodFilter] = useState<string>('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedSearchKeyword, setSelectedSearchKeyword] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);

  const reset = () => {
    setSpecText('');
    setSpecUrl('');
    setResult(null);
    setMethodFilter('');
    setSearchKeyword('');
    setSelectedSearchKeyword('');
    setSelectedKeys([]);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const parse = async () => {
    if (!specText.trim() && !specUrl.trim()) {
      message.warning(t('openapiImport.specRequired'));
      return;
    }
    setLoading(true);
    try {
      const parsed = await api.parseOpenApi({
        spec: specText.trim() || undefined,
        specUrl: specUrl.trim() || undefined,
      });
      setResult(parsed);
      setSelectedKeys([]);
      if (parsed.operations.length === 0) {
        message.warning(t('openapiImport.noOperations'));
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : t('openapiImport.parseFailed'));
    } finally {
      setLoading(false);
    }
  };

  const filteredOperations = useMemo(() => {
    if (!result) {
      return [];
    }
    return filterOperations(result.operations, {
      method: methodFilter || undefined,
      keyword: searchKeyword,
    });
  }, [methodFilter, searchKeyword, result]);

  const selectedOperations = useMemo(() => {
    if (!result) {
      return [];
    }
    const selected = new Set(selectedKeys);
    return result.operations.filter((operation) => selected.has(operationRowKey(operation)));
  }, [result, selectedKeys]);

  const filteredSelectedOperations = useMemo(() => {
    const keyword = selectedSearchKeyword.trim().toLowerCase();
    if (!keyword) {
      return selectedOperations;
    }
    return selectedOperations.filter((operation) => operationSearchText(operation).includes(keyword));
  }, [selectedOperations, selectedSearchKeyword]);

  const removeSelected = (operation: OpenApiOperation) => {
    const key = operationRowKey(operation);
    setSelectedKeys((current) => current.filter((item) => item !== key));
  };

  const importToWizard = (operation: OpenApiOperation) => {
    const payload = toWizardImportPayload(operation);
    sessionStorage.setItem(OPENAPI_IMPORT_STORAGE_KEY, JSON.stringify(payload));
    handleClose();
    navigate('/connectors/new?from=openapi', { state: payload });
  };

  const importSelectedToWizard = () => {
    if (selectedOperations.length !== 1) {
      message.warning(t('openapiImport.wizardSingleOnly'));
      return;
    }
    importToWizard(selectedOperations[0]);
  };

  const batchCreateSelected = async () => {
    if (selectedOperations.length === 0) {
      message.warning(t('openapiImport.selectRequired'));
      return;
    }

    setImporting(true);
    let created = 0;
    let firstId = '';
    try {
      for (const [index, operation] of selectedOperations.entries()) {
        const suffix = selectedOperations.length > 1 ? String(index + 1) : '';
        const connectorId = connectorIdFromOperation(operation, suffix);
        await api.createConnector(buildConnectorRequestFromOperation(operation, connectorId));
        if (created === 0) {
          firstId = connectorId;
        }
        created += 1;
      }
      message.success(t('openapiImport.batchCreated', { count: created }));
      handleClose();
      if (created === 1) {
        navigate(`/connectors/${firstId}/edit`);
      } else {
        navigate(`/connectors/${firstId}/edit`);
      }
    } catch (e) {
      if (created > 0) {
        message.warning(t('openapiImport.batchPartial', { count: created }));
        navigate(`/connectors/${firstId}/edit`);
      } else {
        message.error(e instanceof Error ? e.message : t('openapiImport.batchFailed'));
      }
    } finally {
      setImporting(false);
    }
  };

  const uploadProps: UploadProps = {
    multiple: false,
    showUploadList: false,
    beforeUpload: async (file) => {
      try {
        setSpecText(await file.text());
      } catch {
        message.error(t('openapiImport.readFileFailed'));
      }
      return false;
    },
  };

  return (
    <Modal
      title={t('openapiImport.title')}
      open={open}
      onCancel={handleClose}
      footer={null}
      width={1040}
      destroyOnClose
    >
      {!result ? (
        <>
          <Tabs
            items={[
              {
                key: 'paste',
                label: t('openapiImport.pasteTab'),
                children: (
                  <Input.TextArea
                    rows={10}
                    value={specText}
                    onChange={(e) => setSpecText(e.target.value)}
                    placeholder={t('openapiImport.pastePlaceholder')}
                    data-testid="openapi-spec-text"
                  />
                ),
              },
              {
                key: 'upload',
                label: t('openapiImport.uploadTab'),
                children: (
                  <Dragger {...uploadProps} data-testid="openapi-spec-upload">
                    <p className="ant-upload-drag-icon">
                      <InboxOutlined />
                    </p>
                    <p className="ant-upload-text">{t('openapiImport.uploadHint')}</p>
                  </Dragger>
                ),
              },
            ]}
          />
          <Space style={{ marginTop: 16, width: '100%' }} direction="vertical">
            <Input
              value={specUrl}
              onChange={(e) => setSpecUrl(e.target.value)}
              placeholder={t('openapiImport.urlPlaceholder')}
              data-testid="openapi-spec-url"
            />
            <Button type="primary" loading={loading} data-testid="openapi-parse-btn" onClick={() => void parse()}>
              {t('openapiImport.parse')}
            </Button>
          </Space>
        </>
      ) : (
        <>
          <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 12 }} wrap>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t('openapiImport.serverHint', { servers: result.serverUrls.join(', ') || '-' })}
            </Typography.Paragraph>
            <Button onClick={() => setResult(null)}>{t('openapiImport.reparse')}</Button>
          </Space>
          <Typography.Paragraph style={{ marginBottom: 12 }}>
            {t('openapiImport.selectHint')}
          </Typography.Paragraph>
          <Space wrap style={{ marginBottom: 12, width: '100%' }}>
            <Select
              allowClear
              placeholder={t('openapiImport.filterMethod')}
              style={{ width: 140 }}
              value={methodFilter || undefined}
              onChange={(value) => setMethodFilter(value ?? '')}
              options={HTTP_METHODS.map((method) => ({ value: method, label: method }))}
              data-testid="openapi-filter-method"
            />
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder={t('openapiImport.searchPlaceholder')}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              style={{ width: 420 }}
              data-testid="openapi-search"
            />
          </Space>
          <Table
            size="small"
            rowKey={operationRowKey}
            dataSource={filteredOperations}
            data-testid="openapi-operations-table"
            pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: ['10', '20', '50', '100'] }}
            rowSelection={{
              type: 'checkbox',
              selectedRowKeys: selectedKeys,
              onChange: (keys) => setSelectedKeys(keys as string[]),
              preserveSelectedRowKeys: true,
              selections: [
                Table.SELECTION_ALL,
                Table.SELECTION_INVERT,
                Table.SELECTION_NONE,
              ],
            }}
            columns={[
              { title: t('openapiImport.colMethod'), dataIndex: 'method', width: 90 },
              { title: t('openapiImport.colPath'), dataIndex: 'path', ellipsis: true },
              { title: t('openapiImport.colOperationId'), dataIndex: 'operationId', ellipsis: true, width: 180 },
              { title: t('openapiImport.colSummary'), dataIndex: 'summary', ellipsis: true },
              { title: t('openapiImport.colInputRoot'), dataIndex: 'suggestedInputRoot', width: 120 },
            ]}
          />
          {selectedOperations.length > 0 ? (
            <div style={{ marginTop: 16 }} data-testid="openapi-selected-panel">
              <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }} wrap>
                <Typography.Text strong>
                  {t('openapiImport.selectedListTitle', { count: selectedOperations.length })}
                </Typography.Text>
                <Button type="link" size="small" onClick={() => setSelectedKeys([])}>
                  {t('openapiImport.clearSelected')}
                </Button>
              </Space>
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder={t('openapiImport.selectedSearchPlaceholder')}
                value={selectedSearchKeyword}
                onChange={(e) => setSelectedSearchKeyword(e.target.value)}
                style={{ marginBottom: 8, maxWidth: 420 }}
                data-testid="openapi-selected-search"
              />
              <Table
                size="small"
                rowKey={operationRowKey}
                dataSource={filteredSelectedOperations}
                pagination={{ pageSize: 5, hideOnSinglePage: true }}
                columns={[
                  { title: t('openapiImport.colMethod'), dataIndex: 'method', width: 90 },
                  { title: t('openapiImport.colPath'), dataIndex: 'path', ellipsis: true },
                  { title: t('openapiImport.colOperationId'), dataIndex: 'operationId', ellipsis: true, width: 180 },
                  { title: t('openapiImport.colSummary'), dataIndex: 'summary', ellipsis: true },
                  {
                    title: t('openapiImport.colActions'),
                    width: 80,
                    render: (_, operation) => (
                      <Button
                        type="text"
                        size="small"
                        icon={<CloseOutlined />}
                        aria-label={t('openapiImport.removeSelected')}
                        onClick={() => removeSelected(operation)}
                      />
                    ),
                  },
                ]}
              />
            </div>
          ) : null}
          <Space style={{ marginTop: 16 }} wrap>
            <Button
              type="primary"
              disabled={selectedOperations.length === 0}
              loading={importing}
              data-testid="openapi-import-batch-btn"
              onClick={() => void batchCreateSelected()}
            >
              {t('openapiImport.importBatch', { count: selectedOperations.length })}
            </Button>
            <Button
              disabled={selectedOperations.length !== 1}
              data-testid="openapi-import-wizard-btn"
              onClick={importSelectedToWizard}
            >
              {t('openapiImport.importWizard')}
            </Button>
            <Typography.Text type="secondary">
              {t('openapiImport.selectedCount', { count: selectedOperations.length })}
            </Typography.Text>
          </Space>
        </>
      )}
    </Modal>
  );
}
