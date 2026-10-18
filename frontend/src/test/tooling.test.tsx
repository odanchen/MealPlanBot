import { render, screen } from '@testing-library/react';
import { test, expect } from 'vitest';
import axe from 'axe-core';
test('component accessibility tooling inspects rendered content', async () => {
 render(<main><h1>Meal planner</h1></main>);
 expect(screen.getByRole('heading')).toBeVisible();
 const result = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } });
 expect(result.violations).toEqual([]);
});
