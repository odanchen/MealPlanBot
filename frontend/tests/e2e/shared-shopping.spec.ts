import { test, expect, type BrowserContext, type Page } from '@playwright/test';
async function write(context: BrowserContext, path: string) {
 const csrf = await (await context.request.get('/api/csrf')).json();
 return context.request.post(`http://127.0.0.1:8000${path}`, { headers: { [csrf.headerName]: csrf.token } });
}
async function createList(page: Page, name: string) {
 await page.goto('/lists'); await page.getByLabel('List name', { exact: true }).fill(name); await page.getByRole('button', { name: 'Create list', exact: true }).click(); await expect(page).toHaveURL(/\/lists\/[a-f0-9-]+$/); return new URL(page.url()).pathname;
}
async function invitation(page: Page) { await page.getByRole('button', { name: 'Create invitation', exact: true }).click(); const field = page.getByLabel('Invitation link', { exact: true }); await expect(field).toBeVisible(); return new URL(await field.inputValue()); }
async function join(page: Page, link: URL) { await page.goto(link.pathname + link.hash); await expect(page).toHaveURL(/\/join$/); await expect(page.getByRole('heading', { name: 'Join a shopping list', exact: true })).toBeVisible(); await page.getByRole('button', { name: 'Accept invitation', exact: true }).click(); await expect(page).toHaveURL(/\/lists\/[a-f0-9-]+$/); }
async function add(page: Page, name: string) { await page.getByLabel('Item name', { exact: true }).fill(name); await page.getByRole('button', { name: 'Add item', exact: true }).click(); await expect(page.getByRole('checkbox', { name, exact: true })).toBeVisible(); }
test('two real principals share changes, pause hidden polling, reconnect, clear archive, leave and delete', async ({ browser }, info) => {
 const owner = await browser.newContext({ extraHTTPHeaders: { 'X-Test-Principal': 'owner-' + crypto.randomUUID() } });
 const member = await browser.newContext({ extraHTTPHeaders: { 'X-Test-Principal': 'member-' + crypto.randomUUID() } });
 const outsider = await browser.newContext({ extraHTTPHeaders: { 'X-Test-Principal': 'other-' + crypto.randomUUID() } });
 try {
  const first = await owner.newPage(), second = await member.newPage(), other = await outsider.newPage(); const path = await createList(first, 'Shared household'); const link = await invitation(first);
  await other.goto(path); await expect(other.getByRole('alert')).toHaveText('Resource not found'); await expect(other.getByLabel('Item name', { exact: true })).toHaveCount(0);
  await second.goto(link.pathname + link.hash); await expect(second).toHaveURL(/\/join$/); expect(await second.evaluate(() => sessionStorage.getItem('mealplan.pending-invitation'))).toBe(link.hash.split('token=')[1]);
  expect((await member.request.get('/api' + path)).status()).toBe(404); await second.reload(); await second.getByRole('button', { name: 'Accept invitation', exact: true }).click(); await expect(second).toHaveURL(new RegExp(path + '$'));
  await expect(second.getByRole('button', { name: 'Leave list', exact: true })).toBeVisible(); await expect(second.getByRole('button', { name: 'Create invitation', exact: true })).toHaveCount(0);
  await add(first, 'Milk'); let start = Date.now(); await expect(second.getByRole('checkbox', { name: 'Milk', exact: true })).toBeVisible({ timeout: 5000 }); const additionMs = Date.now() - start;
  await second.getByRole('checkbox', { name: 'Milk', exact: true }).click(); start = Date.now(); await expect(first.getByRole('checkbox', { name: 'Milk', exact: true })).toBeChecked({ timeout: 5000 }); const boughtMs = Date.now() - start;
  await second.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => true }); document.dispatchEvent(new Event('visibilitychange')); });
  let hiddenRequests = 0; const count = (request: { url(): string; method(): string }) => { if (request.url().includes('/api/lists/') && request.method() === 'GET') hiddenRequests++; }; second.on('request', count);
  await add(first, 'Rice'); await second.waitForTimeout(2400); expect(hiddenRequests).toBe(0); await expect(second.getByRole('checkbox', { name: 'Rice', exact: true })).toHaveCount(0);
  second.off('request', count); start = Date.now(); await second.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => false }); document.dispatchEvent(new Event('visibilitychange')); }); await expect(second.getByRole('checkbox', { name: 'Rice', exact: true })).toBeVisible({ timeout: 5000 }); const restoredMs = Date.now() - start;
  await member.setOffline(true); await expect(second.getByRole('status')).toContainText('Disconnected'); await expect(second.getByRole('button', { name: 'Add item', exact: true })).toBeDisabled(); await add(first, 'Bread'); await member.setOffline(false); await expect(second.getByRole('checkbox', { name: 'Bread', exact: true })).toBeVisible({ timeout: 5000 }); await expect(second.getByRole('button', { name: 'Add item', exact: true })).toBeEnabled();
  const entries = (await (await owner.request.get('/api' + path + '/entries')).json()).entries as { id: string; name: string }[]; const rice = entries.find(item => item.name === 'Rice')!;
  expect((await write(owner, `/__test/entries/${rice.id}/archive`)).status()).toBe(200);
  await second.getByRole('link', { name: 'View archive', exact: true }).click(); await expect(second.getByText('Rice · Not bought')).toBeVisible(); await second.getByRole('button', { name: 'Clear Rice', exact: true }).click(); await second.getByRole('button', { name: 'Clear archived item', exact: true }).click(); await expect(second.getByText('The archive is empty.')).toBeVisible();
  const milk = entries.find(item => item.name === 'Milk')!; expect((await write(owner, `/__test/entries/${milk.id}/archive`)).status()).toBe(200); await second.reload(); await second.getByRole('button', { name: 'Clear archive', exact: true }).click(); await second.getByRole('button', { name: 'Clear all archived items', exact: true }).click(); await expect(second.getByText('The archive is empty.')).toBeVisible();
  await second.getByRole('link', { name: '← Back to list', exact: true }).click(); await expect(second.getByRole('checkbox', { name: 'Bread', exact: true })).toBeVisible();
  await second.getByRole('button', { name: 'Leave list', exact: true }).click(); await second.getByRole('dialog').getByRole('button', { name: 'Leave list', exact: true }).click(); await expect(second).toHaveURL('/lists'); await expect(second.getByRole('link', { name: 'Shared household', exact: true })).toHaveCount(0); expect((await member.request.get('/api' + path)).status()).toBe(404);
  await join(second, link); await expect(second.getByRole('checkbox', { name: 'Bread', exact: true })).toBeVisible();
  await first.getByRole('button', { name: 'Delete list', exact: true }).click(); await first.getByRole('dialog').getByRole('button', { name: 'Delete list', exact: true }).click(); await expect(first).toHaveURL('/lists'); await expect(second.getByRole('alert')).toHaveText('Resource not found', { timeout: 5000 });
  let stoppedRequests = 0; const stopped = (request: { url(): string }) => { if (request.url().includes('/api' + path)) stoppedRequests++; }; second.on('request', stopped); await second.waitForTimeout(2300); expect(stoppedRequests).toBe(0);
  await second.goto(link.pathname + link.hash); await second.getByRole('button', { name: 'Accept invitation', exact: true }).click(); await expect(second.getByRole('alert')).toContainText('unavailable or has expired');
  await info.attach('shared-visibility-timing', { body: JSON.stringify({ additionMs, boughtMs, restoredMs, hiddenRequests, stoppedRequests }), contentType: 'application/json' });
 } finally { await owner.close(); await member.close(); await outsider.close(); }
});
test('invitation fragments survive sign-in navigation and withdrawn or expired links grant no access', async ({ browser }) => {
 const owner = await browser.newContext({ extraHTTPHeaders: { 'X-Test-Principal': 'expiry-owner-' + crypto.randomUUID() } }); const recipient = await browser.newContext({ extraHTTPHeaders: { 'X-Test-Principal': 'expiry-member-' + crypto.randomUUID() } }); const anonymous = await browser.newContext();
 try {
  const first = await owner.newPage(), second = await recipient.newPage(), visitor = await anonymous.newPage(); await createList(first, 'Invitation boundaries'); const withdrawn = await invitation(first);
  await anonymous.route('**/api/session', route => route.fulfill({ json: { authenticated: false, authMode: 'google' } })); await visitor.goto(withdrawn.pathname + withdrawn.hash); await expect(visitor).toHaveURL(/\/join$/); await expect(visitor.getByRole('heading', { name: 'Sign in to continue' })).toBeVisible(); await expect(visitor.getByRole('main').getByRole('link', { name: 'Sign in with Google' })).toHaveAttribute('href', '/auth/login?returnTo=%2Fjoin'); expect(await visitor.evaluate(() => sessionStorage.getItem('mealplan.pending-invitation'))).toBe(withdrawn.hash.split('token=')[1]);
  await first.getByRole('button', { name: 'Withdraw invitation 1', exact: true }).click(); await second.goto(withdrawn.pathname + withdrawn.hash); await second.getByRole('button', { name: 'Accept invitation', exact: true }).click(); await expect(second.getByRole('alert')).toContainText('unavailable or has expired');
  const expired = await invitation(first); expect((await write(owner, '/__test/advance?hours=72')).status()).toBe(200); await second.goto(expired.pathname + expired.hash); await second.getByRole('button', { name: 'Accept invitation', exact: true }).click(); await expect(second.getByRole('alert')).toContainText('unavailable or has expired');
 } finally { await owner.close(); await recipient.close(); await anonymous.close(); }
});
