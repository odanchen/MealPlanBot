import { InlineContent } from "./InlineContent";
import type { Recipe } from "../api/recipes";
export function RecipeInstructions({ sections }: { sections: Recipe["sections"] }) {
  return (
    <>
      {sections.map((section) => (
        <section key={section.id} id={section.id}>
          <h2>{section.title}</h2>
          <ol className="steps">
            {section.steps.map((step) => (
              <li key={step.id} id={step.id}>
                <InlineContent segments={step.content} />
              </li>
            ))}
          </ol>
        </section>
      ))}
    </>
  );
}
