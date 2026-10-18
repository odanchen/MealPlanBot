import { IngredientGroups } from "../components/IngredientGroups";
import { MeasurementSystemSelector } from "../components/MeasurementSystemSelector";
import { useEffect, useState } from "react";
import { recipes, type RecipeDetail } from "../api/recipes";
import { RecipeInstructions } from "../components/RecipeInstructions";
function RecipeImage({ src, alt }: { src: string; alt: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? null : <img src={src} alt={alt} onError={() => setFailed(true)} />;
}
export function RecipePage({ id }: { id: string }) {
  const [system, setSystem] = useState<"metric" | "us">("metric");
  const [data, setData] = useState<RecipeDetail>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    recipes
      .get(id, controller.signal, system)
      .then(setData)
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      });
    return () => controller.abort();
  }, [id, system]);
  if (error) return <p role="alert">{error}</p>;
  if (!data) return <p role="status">Loading recipe…</p>;
  const r = data.recipe;
  return (
    <>
      <a href="/">All recipes</a>
      {!data.previous && (
        <button
          onClick={() => {
            if (window.history.length > 1) window.history.back();
            else window.location.assign("/");
          }}
        >
          Back to previous recipe
        </button>
      )}
      <h2>{r.title}</h2>
      <p className="facts">
        {r.servings.note} ·{" "}
        {r.timing.guidance ||
          `${r.timing.prepMinutes ?? 0} min prep · ${r.timing.cookMinutes ?? 0} min cooking`}
      </p>
      {r.images.map((i) => (
        <RecipeImage
          key={i.path}
          src={`/api/recipes/${encodeURIComponent(id)}/images/${i.path.split("/").map(encodeURIComponent).join("/")}`}
          alt={i.alt}
        />
      ))}
      {r.preparationGuidance && (
        <aside className="guidance">
          <h3>Before you start</h3>
          {r.preparationGuidance.split("\n\n").map((p, i) => (
            <p key={i}>{p}</p>
          ))}
        </aside>
      )}
      <div className="recipe-layout">
        <section>
          <h2>Ingredients</h2>
          <MeasurementSystemSelector value={system} onChange={setSystem} />
          <IngredientGroups ingredients={data.ingredientDisplay} categories={data.categories} />
        </section>
        <RecipeInstructions sections={r.sections} />
      </div>
      <nav aria-label="Recipe navigation" className="recipe-nav">
        {data.previous && <a href={`/recipes/${data.previous}`}>← Previous recipe</a>}
        {data.next && <a href={`/recipes/${data.next}`}>Next recipe →</a>}
      </nav>
    </>
  );
}
