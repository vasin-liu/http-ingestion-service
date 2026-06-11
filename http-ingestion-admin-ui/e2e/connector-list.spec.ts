import { expect, test } from '@playwright/test';

const EXAMPLE_TEMPLATE_IDS = [
  'rest-pagination',
  'rest-offset-limit',
  'rest-kafka',
  'webhook-json-array',
] as const;

test.describe('Connector list', () => {
  test('shows hero metrics, OpenAPI import, and example templates', async ({ page }) => {
    await page.goto('/connectors');

    await expect(page.getByRole('heading', { name: '连接器' })).toBeVisible();
    await expect(page.getByText('连接器总数')).toBeVisible();
    await expect(page.getByText('已发布')).toBeVisible();
    await expect(page.getByText('含草稿')).toBeVisible();
    await expect(page.getByTestId('import-openapi-btn')).toBeVisible();

    await page.getByTestId('create-from-template-btn').click();
    await expect(page.getByText('选择连接器')).toBeVisible();
    await expect(page.getByRole('heading', { name: '能力示例' })).toBeVisible();
    for (const templateId of EXAMPLE_TEMPLATE_IDS) {
      await expect(page.getByTestId(`template-${templateId}`)).toBeVisible();
    }
  });

  test('navigates to blank wizard', async ({ page }) => {
    await page.goto('/connectors');
    await page.getByTestId('create-new-btn').click();
    await expect(page).toHaveURL(/\/connectors\/new$/);
    await expect(page.getByText('新建连接器')).toBeVisible();
  });
});
