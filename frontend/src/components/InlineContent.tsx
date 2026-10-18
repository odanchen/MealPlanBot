import type { Segment } from '../api/recipes';
import { CookingTerm } from './CookingTerm';
export function InlineContent({ segments }: { segments: Segment[] }) {
 return <>{segments.map((segment,index) => segment.text !== undefined ? <span key={index}>{segment.text}</span> : segment.recipe ? <a key={index} href={`/recipes/${encodeURIComponent(segment.recipe)}${segment.section ? '#' + encodeURIComponent(segment.section) : ''}`}>{segment.label}</a> : <CookingTerm key={index} label={segment.label!} explanation={segment.explanation!} />)}</>;
}
