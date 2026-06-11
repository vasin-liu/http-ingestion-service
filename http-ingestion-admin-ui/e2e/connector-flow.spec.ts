import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, resetMockData, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Connector end-to-end flow', () => {
  test('creates from example template, publishes, and runs full sync', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-rest');
    const connectorName = `UI E2E ${connectorId}`;

    await resetMockData(baseURL!);
    await page.goto('/connectors');
    await page.getByTestId('create-from-template-btn').click();
    await page.getByTestId('template-rest-pagination').click();
    await expect(page).toHaveURL(/template=rest-pagination/);

    await page.getByLabel('连接器 ID').fill(connectorId);
    await page.getByLabel('名称').fill(connectorName);

    await page.getByTestId('wizard-next').click();
    await page.getByTestId('http-url').fill(`${baseURL}/mock/e2e/pagination-items`);
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('trial-request-btn').click();
    await expect(page.getByText('来自真实试请求')).toBeVisible({ timeout: 30_000 });
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();

    await page.getByLabel('启用调度').click();
    await page.getByTestId('wizard-save').click();
    await expect(page).toHaveURL(new RegExp(`/connectors/${connectorId}$`));

    await page.getByTestId('publish-btn').click();
    await expect(page.getByText('v1')).toBeVisible();

    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');

    await page.getByTestId('sample-run-btn').click();
    await page.getByRole('button', { name: '开始试跑' }).click();
    await waitForJobWithRunType(page, '采样');

    await page.getByRole('row').filter({ hasText: '采样' }).first().click();
    const jobModal = page.getByRole('dialog').filter({ hasText: /排错详情/ });
    await expect(jobModal).toBeVisible();
    await jobModal.locator('button.ant-modal-close').click();
    await expect(jobModal).toBeHidden();
  });
});
