import { expect, test } from '@playwright/test';
import { createConnectorFromTemplate, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Link header pagination', () => {
  test('rest-link-header template full sync via mock', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-link');
    await createConnectorFromTemplate(
      page,
      'rest-link-header',
      connectorId,
      `Link ${connectorId}`,
      `${baseURL}/mock/e2e/link-items`,
      { enableSchedule: false },
    );
    await page.getByTestId('publish-btn').click();
    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');
  });
});
