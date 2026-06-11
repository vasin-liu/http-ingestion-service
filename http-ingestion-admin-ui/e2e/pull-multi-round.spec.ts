import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, resetMockData, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Pull example full sync', () => {
  test('rest-pagination full sync after publish', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-rest-pagination');
    const connectorName = `UI MR ${connectorId}`;

    await resetMockData(baseURL!);
    await createConnectorFromTemplate(
      page,
      'rest-pagination',
      connectorId,
      connectorName,
      `${baseURL}/mock/e2e/pagination-items`,
      { enableSchedule: false },
    );

    await page.getByTestId('publish-btn').click();
    await expect(page.getByText('v1')).toBeVisible();

    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');
  });
});
