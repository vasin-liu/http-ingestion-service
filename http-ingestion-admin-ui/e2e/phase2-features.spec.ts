import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import {
  createConnectorFromTemplate,
  exportConnectorConfig,
  resetMockData,
  uniqueId,
  waitForJobWithRunType,
} from './helpers';

const MOCK_URL = (baseURL: string) => `${baseURL}/mock/e2e/pagination-items`;

test.describe('Phase 2 UI features', () => {
  test('export from detail and import from list', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-export');
    const connectorName = `Export ${connectorId}`;

    await resetMockData(baseURL!);
    await createConnectorFromTemplate(
      page,
      'rest-pagination',
      connectorId,
      connectorName,
      MOCK_URL(baseURL!),
      { enableSchedule: false },
    );

    await page.getByTestId('publish-btn').click();
    await expect(page.getByText('v1')).toBeVisible();

    await page.getByTestId('export-config-btn').click();
    await expect(page.getByText('配置已导出')).toBeVisible();

    const bundle = await exportConnectorConfig(baseURL!, connectorId);
    const importId = uniqueId('ui-imported');
    bundle.id = importId;
    bundle.name = `Imported ${importId}`;

    const importPath = path.join(os.tmpdir(), `${importId}.json`);
    fs.writeFileSync(importPath, JSON.stringify(bundle));

    await page.goto('/connectors');
    await page.getByTestId('import-config-btn').click();
    await page.locator('input[type="file"]').setInputFiles(importPath);
    await expect(page.getByText('配置已导入')).toBeVisible();
    await expect(page.getByRole('heading', { name: bundle.name })).toBeVisible();
  });

  test('stats page shows connector metrics after sync', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-stats');
    const connectorName = `Stats ${connectorId}`;

    await resetMockData(baseURL!);
    await createConnectorFromTemplate(
      page,
      'rest-pagination',
      connectorId,
      connectorName,
      MOCK_URL(baseURL!),
      { enableSchedule: false },
    );

    await page.getByTestId('publish-btn').click();
    await page.getByTestId('sample-run-btn').click();
    await page.getByRole('button', { name: '开始试跑' }).click();
    await waitForJobWithRunType(page, '采样');

    await page.goto('/stats');
    await expect(page.getByTestId('stats-page-title')).toBeVisible();
    await expect(page.getByText('任务总数')).toBeVisible();
    await expect(page.getByRole('link', { name: connectorId }).first()).toBeVisible();
  });

  test('wizard skip trial and pasted sample JSON', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-sample');
    const connectorName = `Sample ${connectorId}`;

    await page.goto('/connectors');
    await page.getByTestId('create-from-template-btn').click();
    await page.getByTestId('template-rest-pagination').click();
    await page.getByLabel('连接器 ID').fill(connectorId);
    await page.getByLabel('名称').fill(connectorName);

    await page.getByTestId('wizard-next').click();
    await page.getByTestId('http-url').fill(MOCK_URL(baseURL!));
    await page.getByTestId('wizard-next').click();

    await page.getByTestId('sample-paste-json').fill(
      '{"meta":{"total":1},"data":[{"id":1,"name":"Alice","updated_at":"2025-06-01T08:00:00Z"}]}',
    );
    await page.getByTestId('infer-schema-from-sample-btn').click();
    await expect(page.getByText('来自样例推断')).toBeVisible();

    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();
    await page.getByLabel('启用调度').click();
    await page.getByTestId('wizard-save').click();
    await expect(page).toHaveURL(new RegExp(`/connectors/${connectorId}$`));
  });

  test('wizard dry run triggers sample job without sink write', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-dryrun');
    const connectorName = `DryRun ${connectorId}`;

    await resetMockData(baseURL!);
    await page.goto('/connectors');
    await page.getByTestId('create-from-template-btn').click();
    await page.getByTestId('template-rest-pagination').click();
    await page.getByLabel('连接器 ID').fill(connectorId);
    await page.getByLabel('名称').fill(connectorName);

    await page.getByTestId('wizard-next').click();
    await page.getByTestId('http-url').fill(MOCK_URL(baseURL!));
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('trial-request-btn').click();
    await expect(page.getByText('来自真实试请求')).toBeVisible({ timeout: 30_000 });
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();
    await page.getByLabel('启用调度').click();

    await page.getByTestId('dry-run-btn').click();
    await expect(page.getByText(/试运行已触发，job #/)).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/connectors/${connectorId}$`));
    await waitForJobWithRunType(page, '采样');
  });

  test('detail page saves schedule inline', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-sched-edit');
    const connectorName = `Schedule Edit ${connectorId}`;

    await resetMockData(baseURL!);
    await createConnectorFromTemplate(
      page,
      'rest-pagination',
      connectorId,
      connectorName,
      MOCK_URL(baseURL!),
      { enableSchedule: false },
    );

    await page.getByTestId('publish-btn').click();
    await expect(page.locator('span.ant-tag').filter({ hasText: /^未启用$/ })).toBeVisible();

    await page.getByTestId('schedule-enabled').click();
    await page.getByTestId('schedule-expression').fill('0 0/5 * * * ?');
    await page.getByTestId('schedule-save-btn').click();
    await expect(page.getByText('调度已保存并发布')).toBeVisible();
  });
});
