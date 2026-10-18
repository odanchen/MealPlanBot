import { render, screen } from '@testing-library/react';
import { test, expect } from 'vitest';
import { IngredientGroups, type IngredientDisplay } from './IngredientGroups';
test('all ingredients render once under their category or other fallback with canonical values retained', () => {
 const base={quantity:'1',unit:'kg',displayQuantity:'2.205',displayUnit:'lb',displayUnitLabel:'lb',optional:false,note:''};
 const ingredients:IngredientDisplay[]=[{...base,ingredientId:'carrot',name:'Carrots',category:'vegetables'},{...base,ingredientId:'unknown',name:'Uncategorized food'},{...base,ingredientId:'odd',name:'Odd category',category:'missing'}];
 const result=render(<IngredientGroups ingredients={ingredients} categories={['vegetables','other']}/>);
 expect(screen.getByRole('heading',{name:'vegetables'})).toBeVisible();expect(screen.getByRole('heading',{name:'other'})).toBeVisible();expect(screen.getAllByRole('listitem')).toHaveLength(3);expect(screen.getByText(/2.205 lb Carrots/)).toBeVisible();expect(result.container.querySelector('[data-ingredient-id="carrot"]')).toHaveAttribute('data-canonical-quantity','1');
});
