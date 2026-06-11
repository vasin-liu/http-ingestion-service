import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, resetMockData, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Connector schedule controls', () => {
  test('pause and resume published schedule', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-schedule');
    const connectorName = `UI Schedule ${connectorId}`;

    await resetMockData(baseURL!);
    await createConnectorFromTemplate(
      page,
      'rest-pagination',
      connectorId,
      connectorName,
      `${baseURL}/mock/e2e/pagination-items`,
      { enableSchedule: true },
    );

    await page.getByTestId('publish-btn').click();
    await expect(page.getByText('v1')).toBeVisible();
    await expect(page.getByText('运行中')).toBeVisible();

    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');

    await page.getByTestId('schedule-pause').click();
    await expect(page.locator('span.ant-tag').filter({ hasText: /^已暂停$/ })).toBeVisible();
    await expect(page.getByTestId('schedule-resume')).toBeVisible();

    await page.getByTestId('schedule-resume').click();
    await expect(page.locator('span.ant-tag').filter({ hasText: /^运行中$/ })).toBeVisible();
    await expect(page.getByTestId('schedule-pause')).toBeVisible();
  });
});
