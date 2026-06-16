import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Monotonic ID incremental', () => {
  test('rest-monotonic-id template full then incremental via mock', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-monotonic');
    await createConnectorFromTemplate(
      page,
      'rest-monotonic-id',
      connectorId,
      `Monotonic ${connectorId}`,
      `${baseURL}/mock/e2e/monotonic-items`,
      { enableSchedule: false },
    );
    await page.getByTestId('publish-btn').click();
    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');

    await page.getByTestId('incremental-sync-btn').click();
    await waitForJobWithRunType(page, '增量');

    await page.goto(`/connectors/${connectorId}`);
    await expect(page.getByText('last_id')).toBeVisible();
  });
});
