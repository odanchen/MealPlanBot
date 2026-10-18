import { test, expect, type BrowserContext } from '@playwright/test';
async function principal(context: BrowserContext, displayName?: string) {
 await context.route('**/api/session', route => route.fulfill({json:{authenticated:!!displayName,authMode:'google',displayName,userId:displayName ? 'test-only-uuid' : undefined}}));
}
test('public shell and protected sign-in message work anonymously', async ({page,context}) => {
 await principal(context);await page.goto('/recipes/example');await expect(page.getByRole('heading',{name:'Meal planner'})).toBeVisible();
 await page.goto('/schedules');await expect(page.getByRole('heading',{name:'Sign in to continue'})).toBeVisible();
 await expect(page.getByRole('main').getByRole('link',{name:'Sign in with Google'})).toHaveAttribute('href',/returnTo=%2Fschedules/);
});
test('isolated browser principals are supplied only by test infrastructure', async ({browser}) => {
 const first=await browser.newContext();const second=await browser.newContext();
 await principal(first,'First cook');await principal(second,'Second cook');
 for(const [context,name,other] of [[first,'First cook','Second cook'],[second,'Second cook','First cook']] as const) {
  const page=await context.newPage();await page.goto('/schedules');await expect(page.getByText(name,{exact:false})).toBeVisible();await expect(page.getByText(other,{exact:false})).toHaveCount(0);await expect(page.getByRole('heading',{name:'Your planner'})).toBeVisible();
 }
 await first.close();await second.close();
});
