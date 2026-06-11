import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const NESTED_OPENAPI_SPEC = readFileSync(path.join(__dirname, 'fixtures/nested-request.json'), 'utf8');

function openApiDialog(page: import('@playwright/test').Page) {
  return page.getByRole('dialog', { name: '从 OpenAPI 导入' });
}

async function importNestedRecordToWizard(page: import('@playwright/test').Page) {
  await page.goto('/connectors');
  await page.getByTestId('import-openapi-btn').click();
  await expect(openApiDialog(page)).toBeVisible();

  const dialog = openApiDialog(page);
  await dialog.getByTestId('openapi-spec-text').fill(NESTED_OPENAPI_SPEC);
  await dialog.getByTestId('openapi-parse-btn').click();
  await expect(dialog.getByTestId('openapi-operations-table')).toBeVisible();

  const row = dialog.getByTestId('openapi-operations-table').locator('tbody tr').filter({ hasText: 'createRecord' });
  await row.locator('input[type="checkbox"]').check();
  await dialog.getByTestId('openapi-import-wizard-btn').click();

  await expect(page).toHaveURL(/\/connectors\/new\?from=openapi/);
  await page.getByTestId('wizard-next').click();
  await expect(page.getByTestId('http-body-tree')).toBeVisible();
}

function bodyRows(page: import('@playwright/test').Page) {
  return page.getByTestId('http-body-table').locator('tbody tr');
}

test.describe('JSON tree editor', () => {
  test('shows nested object, array [items], and stable expand/collapse row counts', async ({ page }) => {
    await importNestedRecordToWizard(page);

    await expect(page.getByTestId('http-body-name-body.profile')).toHaveValue('profile');
    await expect(page.getByTestId('http-body-name-body.tags')).toHaveValue('tags');

    const profileExpand = page.getByTestId('http-body-expand-body.profile');
    await expect(profileExpand).toBeVisible();

    const expandedCount = await bodyRows(page).count();
    expect(expandedCount).toBeGreaterThan(3);
    await expect(page.getByTestId('http-body-name-body.profile.address')).toBeVisible();
    await expect(page.getByTestId('http-body-table')).toContainText('[0]');

    await profileExpand.click();
    const collapsedCount = await bodyRows(page).count();
    expect(collapsedCount).toBeLessThan(expandedCount);

    for (let i = 0; i < 10; i += 1) {
      await profileExpand.click();
      await expect(bodyRows(page)).toHaveCount(i % 2 === 0 ? expandedCount : collapsedCount);
    }
  });

  test('adds a field under an object row without duplicating tree rows', async ({ page }) => {
    await importNestedRecordToWizard(page);

    const beforeCount = await bodyRows(page).count();
    await page.getByTestId('http-body-add-child-body.profile.address').click();
    await expect(bodyRows(page)).toHaveCount(beforeCount + 1);
    await expect(page.getByTestId('http-body-name-body.profile.address').last()).toBeVisible();
  });
});
