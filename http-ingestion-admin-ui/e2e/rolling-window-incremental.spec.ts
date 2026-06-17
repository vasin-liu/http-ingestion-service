import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Rolling window incremental', () => {
  test('rest-rolling-window template full then incremental via mock', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-rolling');
    await createConnectorFromTemplate(
      page,
      'rest-rolling-window',
      connectorId,
      `Rolling ${connectorId}`,
      `${baseURL}/mock/e2e/window-items`,
      { enableSchedule: false },
    );
    await page.getByTestId('publish-btn').click();
    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');

    await page.getByTestId('incremental-sync-btn').click();
    await waitForJobWithRunType(page, '增量');

    await page.goto(`/connectors/${connectorId}`);
    await expect(page.getByText('timestamp')).toBeVisible();
  });
});
