import { useEffect, useState } from 'react';
import { recipes } from '../api/recipes';
export function RecipeDirectoryPage(){
 const [data,setData]=useState<Awaited<ReturnType<typeof recipes.list>>>();const [error,setError]=useState('');
 useEffect(()=>{const controller=new AbortController();recipes.list(controller.signal).then(setData).catch(e=>{if(!controller.signal.aborted)setError(e.message);});return ()=>controller.abort();},[]);
 return <><p className="eyebrow">The household kitchen</p><h2>Recipes for everyday cooking</h2><p>Fifteen complete tutorials, with ingredients and guidance for every step.</p>{error&&<p role="alert">{error}</p>}{!data&&!error&&<p role="status">Loading recipes…</p>}<ul className="recipe-grid">{data?.recipes.map(r=><li key={r.id}><a href={r.url}>{r.title}</a></li>)}</ul></>;
}
