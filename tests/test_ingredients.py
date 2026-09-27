import json
from decimal import Decimal

import pytest
from pydantic import ValidationError

from mealplan.ingredients import IngredientDataError, IngredientRecord, load_records
from mealplan.shopping import aggregate, split_messages


def record(items):
    return IngredientRecord.model_validate({"recipe_id": 1, "ingredients": items})


def item(name, quantity, unit):
    return {"name": name, "quantity": quantity, "unit": unit}


def test_compatible_and_incompatible_aggregation():
    result = aggregate(
        [
            record(
                [
                    item(" Dry Chickpeas ", 0.5, "kg"),
                    item("dry chickpeas", 250, "g"),
                    item("onion", 2, "each"),
                    item("onion", 300, "g"),
                    item("onion", 1, "bunch"),
                    item("milk", 0.5, "l"),
                    item("milk", 200, "ml"),
                    item("tomato paste", 1, "tin_156ml"),
                ]
            ),
            record([item("tomato paste", 1, "tin_156ml")]),
        ]
    )
    assert result == [
        ("dry chickpeas", Decimal(750), "g"),
        ("milk", Decimal(700), "ml"),
        ("onion", Decimal(1), "bunch"),
        ("onion", Decimal(2), "each"),
        ("onion", Decimal(300), "g"),
        ("tomato paste", Decimal(2), "tin_156ml"),
    ]


@pytest.mark.parametrize(
    "bad",
    [
        item("a", -1, "g"),
        item("a", 0, "g"),
        item("a", True, "g"),
        item("a", "2", "g"),
        item("a", float("nan"), "g"),
        item("a", 1, "cup"),
        item("tomato paste", 156, "ml"),
        item("tomato paste", 2, "tin_156ml"),
        item("beans", 0.5, "tin_156ml"),
        item("\n", 1, "g"),
        {**item("a", 1, "g"), "notes": "not allowed"},
    ],
)
def test_invalid_ingredients(bad):
    with pytest.raises(ValidationError):
        record([bad])


def test_missing_and_invalid_all_reported(catalog, write_records):
    write_records([1, 2])
    catalog.path(catalog.recipes[2].ingredients, ".json").write_text('{"recipe_id": 2}')
    with pytest.raises(IngredientDataError) as error:
        load_records(catalog, [1, 2, 3, 4, 5])
    assert error.value.issues == {2: "invalid", 3: "missing", 4: "missing", 5: "missing"}


def test_mismatched_id(catalog, write_records):
    write_records([1])
    path = catalog.path(catalog.recipes[1].ingredients, ".json")
    data = json.loads(path.read_text())
    data["recipe_id"] = 2
    path.write_text(json.dumps(data))
    with pytest.raises(IngredientDataError):
        load_records(catalog, [1])


def test_numbered_unicode_messages_preserve_content():
    text = ("🥕 carrots: 5 each\n" * 900) + "end"
    messages = split_messages(text)
    assert len(messages) > 1
    assert all(len(m.encode("utf-16-le")) // 2 <= 4096 for m in messages)
    assert "".join(m.split("\n", 1)[1] for m in messages) == text
    assert messages[0].startswith(f"(1/{len(messages)})\n")
