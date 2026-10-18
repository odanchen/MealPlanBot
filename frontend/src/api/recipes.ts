import { request } from './client';
export type Segment = { text?: string; term?: string; recipe?: string; section?: string; label?: string; explanation?: string };
export type Recipe = { id: string; title: string; servings: { estimate: number; note: string }; timing: { prepMinutes?: number; cookMinutes?: number; guidance?: string }; preparationGuidance: string; images: {path:string;alt:string}[]; ingredients: { ingredientId: string; quantity: string; unit: string; optional: boolean; note: string }[]; sections: { id: string; title: string; steps: {id:string;content:Segment[]}[] }[] };
export type RecipeDetail = { revision: string; recipe: Recipe; ingredientCatalogue: Record<string,{name:string;category:string}>; categories: string[]; previous?: string; next?: string };
export const recipes = {
 list: (signal?:AbortSignal) => request<{ revision:string;recipes:{id:string;title:string;url:string}[] }>('/api/recipes',{signal}),
 get: (id:string,signal?:AbortSignal) => request<RecipeDetail>(`/api/recipes/${encodeURIComponent(id)}`,{signal}),
};
