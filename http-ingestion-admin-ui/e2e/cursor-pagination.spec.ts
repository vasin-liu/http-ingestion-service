import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Cursor pagination', () => {
  test('rest-cursor template full sync via mock', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-cursor');
    await createConnectorFromTemplate(
      page,
      'rest-cursor',
      connectorId,
      `Cursor ${connectorId}`,
      `${baseURL}/mock/e2e/cursor-items`,
      { enableSchedule: false },
    );
    await page.getByTestId('publish-btn').click();
    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');
  });
});
