import { expect, test } from '@playwright/test';
import { expectKafkaMessageCount, uniqueId, waitForJobWithRunType } from './helpers';

test.describe('Kafka sink', () => {
  test('creates rest-kafka connector, syncs, and writes to Kafka topic', async ({ page, baseURL }) => {
    const connectorId = uniqueId('ui-kafka');
    const connectorName = `Kafka UI ${connectorId}`;
    const topic = `ingest.ui.${connectorId}`;

    await page.goto('/connectors');
    await page.getByTestId('create-from-template-btn').click();
    await page.getByTestId('template-rest-kafka').click();
    await expect(page.getByLabel('名称')).toHaveValue('REST → Kafka 示例');

    await page.getByLabel('连接器 ID').fill(connectorId);
    await page.getByLabel('名称').fill(connectorName);
    await page.getByTestId('wizard-next').click();

    await page.getByTestId('http-url').fill(`${baseURL}/mock/e2e/kafka-users`);
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('trial-request-btn').click();
    await expect(page.getByText('来自真实试请求')).toBeVisible({ timeout: 30_000 });
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();

    await page.getByTestId('sink-kafka-topic').waitFor({ state: 'visible', timeout: 30_000 });
    await page.getByTestId('sink-kafka-topic').fill(topic);
    await page.getByTestId('wizard-next').click();
    await page.getByLabel('启用调度').click();
    await page.getByTestId('wizard-save').click();
    await page.waitForURL(new RegExp(`/connectors/${connectorId}$`));

    await page.getByTestId('publish-btn').click();
    await expect(page.getByText('v1')).toBeVisible();

    await page.getByTestId('full-sync-btn').click();
    await waitForJobWithRunType(page, '全量');

    const count = await expectKafkaMessageCount(baseURL!, topic, 2);
    expect(count).toBeGreaterThanOrEqual(2);
  });
});
