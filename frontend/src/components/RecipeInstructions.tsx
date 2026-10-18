import type { Recipe } from '../api/recipes';
export function RecipeInstructions({sections}:{sections:Recipe['sections']}) {
 return <>{sections.map(section=><section key={section.id} id={section.id}><h2>{section.title}</h2><ol className="steps">{section.steps.map(step=><li key={step.id} id={step.id}>{step.content.map((s,i)=>s.text!==undefined ? <span key={i}>{s.text}</span> : s.recipe ? <a key={i} href={`/recipes/${encodeURIComponent(s.recipe)}${s.section?'#'+encodeURIComponent(s.section):''}`}>{s.label}</a> : <span key={i} title={s.explanation}>{s.label}</span>)}</li>)}</ol></section>)}</>;
}
