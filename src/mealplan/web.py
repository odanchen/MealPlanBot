from __future__ import annotations

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

from .catalog import Catalog, Recipe
from .config import Settings
from .ingredients import IngredientDataError, load_record
from .schedule import DAYS, Clock, Day, local_date, next_week, now, scheduled_day


def create_app(settings: Settings | None = None, clock: Clock = now) -> FastAPI:
    settings = settings or Settings.from_env()
    # Invalid configuration/manifest fails startup rather than serving an incorrect schedule.
    catalog = Catalog(settings.content_dir)
    app = FastAPI(title="Household meal planner")
    app.mount("/static", StaticFiles(directory=settings.content_dir / "static"), name="static")

    def metadata(recipe: Recipe, include_ingredients: bool = True) -> dict:
        result = {
            "id": recipe.id,
            "title": recipe.title,
            "html_url": f"/recipes/{recipe.id}",
            "cycle_week": (recipe.id - 1) // 5 + 1,
            "day_of_week": DAYS[(recipe.id - 1) % 5],
            "cycle_day": (recipe.id - 1) // 5 * 7 + (recipe.id - 1) % 5 + 1,
        }
        if include_ingredients:
            try:
                record = load_record(catalog, recipe)
                result.update(
                    ingredients_available=True,
                    ingredients_status="available",
                    raw_ingredients=record.model_dump(mode="json"),
                )
            except IngredientDataError as error:
                result.update(
                    ingredients_available=False,
                    raw_ingredients=None,
                    ingredients_status=error.issues[recipe.id],
                )
        return result

    def day_payload(day: Day) -> dict:
        return {
            "date": day.date.isoformat(),
            "day_of_week": day.day_of_week,
            "cycle_week": day.cycle_week,
            "recipe_id": day.recipe_id,
            "status": "rest_day" if day.recipe_id is None else "scheduled",
            "recipe": metadata(catalog.recipes[day.recipe_id]) if day.recipe_id else None,
        }

    def recipe_by_id(recipe_id: str) -> Recipe:
        # String parameter means all malformed/unknown identifiers consistently get 404.
        if recipe_id not in {str(i) for i in range(1, 16)}:
            raise HTTPException(404, "Unknown recipe")
        return catalog.recipes[int(recipe_id)]

    @app.middleware("http")
    async def cache_policy(request, call_next):
        response = await call_next(request)
        response.headers["Cache-Control"] = (
            "no-store"
            if request.url.path in {"/", "/today", "/api/today", "/api/next-week", "/health"}
            or request.url.path.startswith("/api/")
            else "no-cache"
        )
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.get("/")
    def root():
        return RedirectResponse("/today", status_code=302)

    @app.get("/today")
    def today():
        day = scheduled_day(local_date(clock), settings.anchor)
        return RedirectResponse(
            f"/recipes/{day.recipe_id}" if day.recipe_id else "/rest-day", status_code=302
        )

    @app.get("/rest-day")
    def rest_day():
        path = settings.content_dir / "rest-day.html"
        if not path.is_file():
            raise HTTPException(503, "Rest-day page unavailable")
        return FileResponse(path, media_type="text/html")

    @app.get("/recipes/{recipe_id}")
    def recipe_html(recipe_id: str):
        recipe = recipe_by_id(recipe_id)
        try:
            path = catalog.path(recipe.html, ".html")
            if not path.is_file():
                raise ValueError("Missing HTML")
        except ValueError:
            raise HTTPException(503, "Recipe HTML unavailable") from None
        return FileResponse(path, media_type="text/html")

    @app.get("/api/recipes")
    def recipes():
        return [metadata(catalog.recipes[i], False) for i in range(1, 16)]

    @app.get("/api/recipes/{recipe_id}")
    def recipe_api(recipe_id: str):
        return metadata(recipe_by_id(recipe_id))

    @app.get("/api/today")
    def today_api():
        return day_payload(scheduled_day(local_date(clock), settings.anchor))

    @app.get("/api/next-week")
    def next_week_api():
        return {"days": [day_payload(day) for day in next_week(local_date(clock), settings.anchor)]}

    @app.get("/health")
    def health():
        missing = catalog.missing_html()
        rest_ok = (settings.content_dir / "rest-day.html").is_file()
        healthy = not missing and rest_ok
        return JSONResponse(
            {
                "status": "ok" if healthy else "not_ready",
                "configuration_valid": True,
                "manifest_valid": True,
                "all_recipe_html_present": not missing,
                "missing_html_recipe_ids": missing,
                "rest_day_present": rest_ok,
            },
            status_code=200 if healthy else 503,
        )

    return app
