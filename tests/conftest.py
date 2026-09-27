import json
import shutil
from datetime import date
from pathlib import Path

import pytest

from mealplan.catalog import Catalog
from mealplan.config import Settings


@pytest.fixture
def content(tmp_path):
    root = tmp_path / "content"
    shutil.copytree(Path(__file__).parents[1] / "content", root)
    # Error-path tests start without purchase records; real content has separate integration tests.
    for record in (root / "recipes").glob("*.json"):
        record.unlink()
    return root


@pytest.fixture
def settings(content):
    return Settings(
        anchor=date(2026, 9, 27),
        content_dir=content,
        token="123456:synthetic-test-token",
        chat_ids=(42,),
    )


@pytest.fixture
def catalog(content):
    return Catalog(content)


@pytest.fixture
def write_records(catalog):
    def write(ids):
        fixture = json.loads(
            (Path(__file__).parent / "fixtures/synthetic-ingredients.json").read_text()
        )
        for recipe_id in ids:
            fixture["recipe_id"] = recipe_id
            catalog.path(catalog.recipes[recipe_id].ingredients, ".json").write_text(
                json.dumps(fixture)
            )

    return write
