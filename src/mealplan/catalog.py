from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, StrictInt, ValidationError, field_validator


class Recipe(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: StrictInt = Field(ge=1, le=15)
    title: str = Field(min_length=1, max_length=160)
    html: str
    ingredients: str

    @field_validator("title")
    @classmethod
    def title_is_plain(cls, value: str) -> str:
        if not value.strip() or any(ord(c) < 32 for c in value):
            raise ValueError("Title must be plain text")
        return value


class Catalog:
    def __init__(self, root: Path):
        self.root = root.resolve()
        try:
            raw = json.loads((self.root / "manifest.json").read_text())
            recipes = [Recipe.model_validate(item) for item in raw]
            self.recipes = {recipe.id: recipe for recipe in recipes}
            if len(recipes) != 15 or set(self.recipes) != set(range(1, 16)):
                raise ValueError("Manifest must contain IDs 1-15 exactly once")
            for recipe in recipes:
                self.path(recipe.html, ".html")
                self.path(recipe.ingredients, ".json")
            if len({r.html for r in recipes}) != 15 or len({r.ingredients for r in recipes}) != 15:
                raise ValueError("Manifest filenames must be unique")
        except (OSError, ValueError, TypeError, ValidationError):
            raise ValueError("Invalid or missing recipe manifest") from None

    def path(self, relative: str, suffix: str) -> Path:
        path = (self.root / relative).resolve()
        if not path.is_relative_to(self.root / "recipes") or path.suffix != suffix:
            raise ValueError(
                "Recipe path must stay inside content/recipes with the correct extension"
            )
        return path

    def missing_html(self) -> list[int]:
        missing = []
        for recipe in self.recipes.values():
            try:
                with self.path(recipe.html, ".html").open("rb") as page:
                    if not page.read(1):
                        missing.append(recipe.id)
            except (OSError, ValueError):
                missing.append(recipe.id)
        return missing
