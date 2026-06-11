import { expect } from '@playwright/test';

export function uniqueId(prefix: string) {
  return `${prefix}-${Date.now()}`;
}

export async function waitForJobWithRunType(page: import('@playwright/test').Page, runType: string) {
  await page
    .getByRole('row')
    .filter({ hasText: runType })
    .filter({ hasText: '成功' })
    .first()
    .waitFor({ timeout: 120_000 });
}

export async function resetMockData(baseURL: string) {
  const response = await fetch(`${baseURL}/mock/_test/reset`, { method: 'POST' });
  if (!response.ok) {
    throw new Error(`Mock reset failed: ${response.status}`);
  }
}

export async function appendMockData(baseURL: string, path: string, body: Record<string, unknown>) {
  const response = await fetch(`${baseURL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`Mock append failed: ${response.status}`);
  }
}

export async function expectKafkaMessageCount(baseURL: string, topic: string, expected: number) {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const response = await fetch(
      `${baseURL}/mock/_test/kafka/count?topic=${encodeURIComponent(topic)}`,
    );
    if (response.ok) {
      const body = (await response.json()) as { ok?: boolean; count?: number };
      if (body.ok && (body.count ?? 0) >= expected) {
        return body.count ?? expected;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`Expected at least ${expected} Kafka messages on topic ${topic}`);
}

export async function createConnectorFromTemplate(
  page: import('@playwright/test').Page,
  templateId: string,
  connectorId: string,
  connectorName: string,
  mockUrl: string,
  options?: { enableSchedule?: boolean },
) {
  await page.goto('/connectors');
  await page.getByTestId('create-from-template-btn').click();
  await page.getByTestId(`template-${templateId}`).click();
  await page.getByLabel('连接器 ID').fill(connectorId);
  await page.getByLabel('名称').fill(connectorName);
  await page.getByTestId('wizard-next').click();
  await expect(page.getByTestId('http-url')).toBeVisible();
  await page.getByTestId('http-url').fill(mockUrl);
  await page.getByTestId('wizard-next').click();
  await page.getByTestId('trial-request-btn').click();
  await expect(page.getByText('来自真实试请求')).toBeVisible({ timeout: 30_000 });
  await page.getByTestId('wizard-next').click();
  await page.getByTestId('wizard-next').click();
  await page.getByTestId('wizard-next').click();
  if (options?.enableSchedule === false) {
    await page.getByLabel('启用调度').click();
  }
  await page.getByTestId('wizard-save').click();
  await page.waitForURL(new RegExp(`/connectors/${connectorId}$`));
}

export async function deleteConnectorIfExists(baseURL: string, connectorId: string) {
  const response = await fetch(`${baseURL}/api/connectors/${connectorId}`, { method: 'DELETE' });
  if (response.status === 404) {
    return;
  }
  if (!response.ok) {
    throw new Error(`Delete connector ${connectorId} failed: ${response.status}`);
  }
}

export async function listConnectorIds(baseURL: string): Promise<string[]> {
  const response = await fetch(`${baseURL}/api/connectors`);
  if (!response.ok) {
    throw new Error(`List connectors failed: ${response.status}`);
  }
  const rows = (await response.json()) as Array<{ id: string }>;
  return rows.map((row) => row.id);
}

export async function exportConnectorConfig(baseURL: string, connectorId: string) {
  const response = await fetch(`${baseURL}/api/connectors/${connectorId}/export`);
  if (!response.ok) {
    throw new Error(`Export failed: ${response.status}`);
  }
  return response.json() as Promise<{
    id: string;
    name: string;
    mode: string;
    config: Record<string, unknown>;
  }>;
}
