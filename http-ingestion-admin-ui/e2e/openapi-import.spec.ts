import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';
import { deleteConnectorIfExists, listConnectorIds } from './helpers';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SAMPLE_OPENAPI_SPEC = readFileSync(path.join(__dirname, 'fixtures/sample-users.json'), 'utf8');

const BATCH_CONNECTOR_IDS = ['openapi-listusers-1', 'openapi-createuser-2'] as const;

function openApiDialog(page: Page) {
  return page.getByRole('dialog', { name: '从 OpenAPI 导入' });
}

async function openOpenApiImportModal(page: Page) {
  await page.goto('/connectors');
  await page.getByTestId('import-openapi-btn').click();
  await expect(openApiDialog(page)).toBeVisible();
}

async function parseSampleOpenApi(page: Page) {
  await openOpenApiImportModal(page);
  const dialog = openApiDialog(page);
  await dialog.getByTestId('openapi-spec-text').fill(SAMPLE_OPENAPI_SPEC);
  await dialog.getByTestId('openapi-parse-btn').click();
  await expect(dialog.getByTestId('openapi-operations-table')).toBeVisible();
}

function operationRows(page: Page) {
  return openApiDialog(page).getByTestId('openapi-operations-table').locator('tbody tr');
}

async function selectOperation(page: Page, operationId: string) {
  const row = operationRows(page).filter({ hasText: operationId });
  await row.locator('input[type="checkbox"]').check();
}

test.describe('OpenAPI import', () => {
  test('parses pasted OAS3 and lists operations', async ({ page }) => {
    await parseSampleOpenApi(page);
    await expect(operationRows(page)).toHaveCount(2);
    await expect(operationRows(page).filter({ hasText: 'listUsers' })).toHaveCount(1);
    await expect(operationRows(page).filter({ hasText: 'createUser' })).toHaveCount(1);
  });

  test('filters operations by search keyword', async ({ page }) => {
    await parseSampleOpenApi(page);
    await expect(operationRows(page)).toHaveCount(2);

    await openApiDialog(page).getByTestId('openapi-search').fill('createUser');
    await expect(operationRows(page)).toHaveCount(1);
    await expect(operationRows(page).filter({ hasText: 'createUser' })).toHaveCount(1);

    await openApiDialog(page).getByTestId('openapi-search').fill('/users');
    await expect(operationRows(page)).toHaveCount(2);

    await openApiDialog(page).getByTestId('openapi-search').fill('List users');
    await expect(operationRows(page)).toHaveCount(1);
    await expect(operationRows(page).filter({ hasText: 'listUsers' })).toHaveCount(1);
  });

  test('imports list operation with inferred pagination on wizard step', async ({ page }) => {
    await parseSampleOpenApi(page);
    await selectOperation(page, 'listUsers');
    await openApiDialog(page).getByTestId('openapi-import-wizard-btn').click();

    await expect(page).toHaveURL(/\/connectors\/new\?from=openapi/);
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();

    await expect(page.locator('.ant-select:has(#pagination_strategy)')).toContainText('page / page_size');
    await expect(page.getByLabel('page 参数名')).toHaveValue('page');
    await expect(page.getByLabel('total JsonPath（可选）')).toHaveValue('$.meta.total');
  });

  test('imports single operation into wizard with HTTP config and params', async ({ page }) => {
    await parseSampleOpenApi(page);
    await selectOperation(page, 'listUsers');
    await openApiDialog(page).getByTestId('openapi-import-wizard-btn').click();

    await expect(page).toHaveURL(/\/connectors\/new\?from=openapi/);
    await expect(page.getByText('已从 OpenAPI 导入')).toBeVisible();

    await expect(page.getByLabel('连接器 ID')).toHaveValue('openapi-listusers');

    await page.getByTestId('wizard-next').click();
    await expect(page.getByTestId('http-url')).toHaveValue(/\/users$/);
    await expect(page.locator('.ant-select:has(#http_method)')).toContainText('GET');

    await page.getByRole('tab', { name: 'Params' }).click();
    await expect(page.getByTestId('http-query-key-0')).toHaveValue('page');
  });

  test('imports POST operation into wizard with JSON body tree', async ({ page }) => {
    await parseSampleOpenApi(page);
    await selectOperation(page, 'createUser');
    await openApiDialog(page).getByTestId('openapi-import-wizard-btn').click();

    await expect(page).toHaveURL(/\/connectors\/new\?from=openapi/);
    await expect(page.getByText('已从 OpenAPI 导入')).toBeVisible();
    await expect(page.getByLabel('连接器 ID')).toHaveValue('openapi-createuser');

    await page.getByTestId('wizard-next').click();
    await expect(page.getByTestId('http-url')).toHaveValue(/\/users$/);
    await expect(page.locator('.ant-select:has(#http_method)')).toContainText('POST');

    await expect(page.getByTestId('http-body-tree')).toBeVisible();
    await expect(page.getByTestId('http-body-name-body.name')).toHaveValue('name');
    await expect(page.getByTestId('http-body-value-body.name')).toHaveValue('Alice');
    await expect(page.getByTestId('http-body-table').locator('tbody tr')).toHaveCount(1);
  });

  test('shows structured response schema on trial step after wizard import', async ({ page }) => {
    await parseSampleOpenApi(page);
    await selectOperation(page, 'listUsers');
    await openApiDialog(page).getByTestId('openapi-import-wizard-btn').click();

    await expect(page).toHaveURL(/\/connectors\/new\?from=openapi/);
    await expect(page.getByText('已从 OpenAPI 导入')).toBeVisible();

    await page.getByTestId('wizard-next').click();
    await page.getByTestId('wizard-next').click();

    await expect(page.getByTestId('response-schema-visual')).toBeVisible();
    await expect(page.getByTestId('response-record-table').locator('tbody tr').first()).toBeVisible();
    await expect(page.getByTestId('response-record-table')).toContainText('id');
  });

  test('batch-created edit page restores request and response schema after reload', async ({ page, baseURL }) => {
    const connectorId = 'openapi-createuser';
    await deleteConnectorIfExists(baseURL!, connectorId);

    await parseSampleOpenApi(page);
    await selectOperation(page, 'createUser');
    await openApiDialog(page).getByTestId('openapi-import-batch-btn').click();
    await expect(page).toHaveURL(new RegExp(`/connectors/${connectorId}/edit$`));

    await page.getByTestId('wizard-next').click();
    await expect(page.getByTestId('http-url')).toHaveValue(/\/users$/);
    await expect(page.getByTestId('http-body-tree')).toBeVisible();
    await expect(page.getByTestId('http-body-value-body.name')).toHaveValue('Alice');

    await page.getByTestId('wizard-next').click();
    await expect(page.getByTestId('response-schema-visual')).toBeVisible();
    await expect(page.getByTestId('response-record-table')).toContainText('id');

    await page.reload();
    await expect(page.getByLabel('连接器 ID')).toHaveValue(connectorId);
    await page.getByTestId('wizard-next').click();
    await expect(page.getByTestId('http-url')).toHaveValue(/\/users$/);
    await expect(page.getByTestId('http-body-value-body.name')).toHaveValue('Alice');

    await page.getByTestId('wizard-next').click();
    await expect(page.getByTestId('response-schema-visual')).toBeVisible();
    await expect(page.getByTestId('response-record-table')).toContainText('id');
  });

  test('batch creates draft connectors from multi-select', async ({ page, baseURL }) => {
    for (const id of BATCH_CONNECTOR_IDS) {
      await deleteConnectorIfExists(baseURL!, id);
    }

    const beforeIds = await listConnectorIds(baseURL!);

    await parseSampleOpenApi(page);
    await selectOperation(page, 'listUsers');
    await selectOperation(page, 'createUser');
    await expect(openApiDialog(page).getByTestId('openapi-selected-panel')).toBeVisible();

    await openApiDialog(page).getByTestId('openapi-import-batch-btn').click();
    await expect(page).toHaveURL(/\/connectors\/openapi-listusers-1\/edit$/);

    const afterIds = await listConnectorIds(baseURL!);
    expect(afterIds.length - beforeIds.length).toBe(2);
    expect(afterIds).toEqual(expect.arrayContaining([...BATCH_CONNECTOR_IDS]));

    await page.goto('/connectors');
    for (const id of BATCH_CONNECTOR_IDS) {
      await expect(page.getByText(id)).toBeVisible();
    }
  });
});
