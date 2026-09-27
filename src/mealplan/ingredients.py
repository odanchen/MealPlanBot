from __future__ import annotations

import json
from decimal import Decimal
from typing import Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    ValidationError,
    field_serializer,
    field_validator,
    model_validator,
)

from .catalog import Catalog, Recipe

Unit = Literal["g", "kg", "ml", "l", "each", "bunch", "tin_156ml"]


class Ingredient(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=100)
    quantity: Decimal = Field(gt=0, max_digits=12, decimal_places=6, allow_inf_nan=False)
    unit: Unit

    @field_serializer("quantity", when_used="json")
    def json_quantity(self, value: Decimal) -> float:
        return float(value)

    @field_validator("quantity", mode="before")
    @classmethod
    def numeric_quantity(cls, value: object) -> object:
        if isinstance(value, bool) or not isinstance(value, (int, float, Decimal)):
            raise ValueError("Quantity must be a JSON number")  # noqa: TRY004 — Pydantic validators require ValueError
        return value

    @field_validator("name")
    @classmethod
    def canonical_name(cls, value: str) -> str:
        if any(ord(c) < 32 for c in value):
            raise ValueError("Ingredient names must be plain text")
        result = " ".join(value.casefold().split())
        if not result:
            raise ValueError("Ingredient name is empty")
        return result

    @model_validator(mode="after")
    def package_rules(self) -> Ingredient:
        if self.unit == "tin_156ml" and self.quantity != self.quantity.to_integral_value():
            raise ValueError("Tins must be whole purchases")
        if self.name == "tomato paste" and (self.unit != "tin_156ml" or self.quantity != 1):
            raise ValueError("Tomato paste must be one 156 ml tin per recipe")
        return self


class IngredientRecord(BaseModel):
    model_config = ConfigDict(extra="forbid")
    recipe_id: StrictInt = Field(ge=1, le=15)
    ingredients: list[Ingredient] = Field(min_length=1, max_length=200)

    @model_validator(mode="after")
    def one_tomato_paste(self) -> IngredientRecord:
        if sum(item.name == "tomato paste" for item in self.ingredients) > 1:
            raise ValueError("Only one tomato paste entry per recipe")
        return self


class IngredientDataError(ValueError):
    def __init__(self, issues: dict[int, str]):
        self.issues = issues
        super().__init__(
            "Ingredient data unavailable: "
            + ", ".join(f"recipe {key} ({value})" for key, value in sorted(issues.items()))
        )


def load_record(catalog: Catalog, recipe: Recipe) -> IngredientRecord:
    try:
        path = catalog.path(recipe.ingredients, ".json")
        raw = json.loads(path.read_text(), parse_float=Decimal)
        record = IngredientRecord.model_validate(raw)
        if record.recipe_id != recipe.id:
            raise ValueError("Recipe ID mismatch")
        return record
    except FileNotFoundError:
        raise IngredientDataError({recipe.id: "missing"}) from None
    except (OSError, ValueError, ValidationError):
        raise IngredientDataError({recipe.id: "invalid"}) from None


def load_records(catalog: Catalog, ids: list[int]) -> list[IngredientRecord]:
    records, issues = [], {}
    for recipe_id in ids:
        try:
            records.append(load_record(catalog, catalog.recipes[recipe_id]))
        except IngredientDataError as error:
            issues.update(error.issues)
    if issues:
        raise IngredientDataError(issues)
    return records
