import { expect, test } from '@playwright/test';

test.describe('Theme', () => {
  test('toggles light and dark themes with readable contrast tokens', async ({ page }) => {
    await page.goto('/connectors');

    const root = page.locator('html');
    await expect(root).toHaveAttribute('data-theme', /light|dark/);

    await page.getByTestId('theme-switch').getByText('浅色').click();
    await expect(root).toHaveAttribute('data-theme', 'light');
    await expect(page.locator('.page-hero-title').first()).toBeVisible();

    await page.getByTestId('theme-switch').getByText('深色').click();
    await expect(root).toHaveAttribute('data-theme', 'dark');
    await expect(page.getByTestId('create-new-btn')).toBeVisible();
  });

  test('switches locale between Chinese and English', async ({ page }) => {
    await page.goto('/connectors');
    await expect(page.getByRole('heading', { name: '连接器' })).toBeVisible();

    await page.getByTestId('locale-switch').getByText('EN').click();
    await expect(page.getByRole('heading', { name: 'Connectors' })).toBeVisible();

    await page.getByTestId('locale-switch').getByText('中文').click();
    await expect(page.getByRole('heading', { name: '连接器' })).toBeVisible();
  });
});
