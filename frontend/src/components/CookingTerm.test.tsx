import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { CookingTerm } from './CookingTerm';
test('hover opens, click pins, Escape dismisses without reopening on retained focus', async () => {
 const user = userEvent.setup(); render(<CookingTerm label="Simmer" explanation="Small gentle bubbles." />); const trigger = screen.getByRole('button', { name: 'Simmer' });
 await user.hover(trigger); expect(screen.getByRole('tooltip')).toHaveTextContent('Small gentle bubbles.');
 await user.click(trigger); await user.unhover(trigger); expect(screen.getByRole('tooltip')).toBeVisible();
 await user.keyboard('{Escape}'); expect(screen.queryByRole('tooltip')).toBeNull(); expect(trigger).toHaveFocus();
 fireEvent.focus(trigger); expect(screen.queryByRole('tooltip')).toBeNull(); await user.tab(); await user.tab({shift:true}); expect(screen.getByRole('tooltip')).toBeVisible();
});
test('only one bubble opens, outside click dismisses and pointer can enter its bubble', async () => {
 const user = userEvent.setup(); render(<><CookingTerm label="Braise" explanation="Cook gently." /><CookingTerm label="Deglaze" explanation="Lift browned bits." /><button>Outside</button></>);
 await user.hover(screen.getByRole('button', { name: 'Braise' })); const bubble = screen.getByRole('tooltip'); fireEvent.pointerLeave(screen.getByRole('button', { name: 'Braise' }), { relatedTarget: bubble }); fireEvent.pointerEnter(bubble); expect(bubble).toBeVisible();
 await user.click(screen.getByRole('button', { name: 'Deglaze' })); expect(screen.getAllByRole('tooltip')).toHaveLength(1); expect(screen.getByRole('tooltip')).toHaveTextContent('Lift browned bits.');
 await user.click(screen.getByRole('button', { name: 'Outside' })); expect(screen.queryByRole('tooltip')).toBeNull();
});
