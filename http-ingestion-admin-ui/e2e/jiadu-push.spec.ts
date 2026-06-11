import { expect, test } from '@playwright/test';
import { uniqueId, waitForJobWithRunType } from './helpers';

async function createJiaduPushConnector(baseURL: string, connectorId: string, connectorName: string) {
  const response = await fetch(`${baseURL}/api/connectors`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      id: connectorId,
      name: connectorName,
      mode: 'push',
      config: {
        webhook: { enabled: true, path_suffix: '', verify_sign: false, plat_flag: 'ivsp' },
        transform: {
          input_root: '$',
          steps: [
            {
              type: 'map_fields',
              mappings: [
                { target: 'event_id', source: '$.EventID', type: 'string' },
                { target: 'event_type', source: '$.EventType', type: 'long' },
                { target: 'event_name', source: '$.EventName', type: 'string' },
              ],
            },
          ],
        },
        sink: {
          type: 'postgresql',
          target: { schema: 'public', table: 'jiadu_event_info' },
          keys: ['event_id'],
          write_mode: 'upsert',
          batch_size: 500,
        },
        schedule: { enabled: false, type: 'cron', expression: '0 0/5 * * * ?' },
      },
    }),
  });
  if (!response.ok) {
    throw new Error(`Create connector failed: ${response.status}`);
  }
}

test.describe('Push ingress', () => {
  test('creates push connector and ingests via mock simulator', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-jiadu');
    const connectorName = `Push ${connectorId}`;

    await createJiaduPushConnector(baseURL!, connectorId, connectorName);
    await page.goto(`/connectors/${connectorId}`);
    await page.getByTestId('publish-btn').click();
    await expect(page.getByText('v1')).toBeVisible();

    const simResponse = await fetch(`${baseURL}/mock/jiadu/push/${connectorId}?rounds=3`, {
      method: 'POST',
    });
    expect(simResponse.ok).toBeTruthy();
    const sim = (await simResponse.json()) as { success: number; sent: number };
    expect(sim.sent).toBe(3);
    expect(sim.success).toBe(3);

    await page.reload();
    await waitForJobWithRunType(page, 'push');
    await expect(
      page.getByRole('row').filter({ hasText: 'push' }).filter({ hasText: '成功' }).first(),
    ).toBeVisible();
  });
});
