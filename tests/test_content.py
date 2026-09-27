"""Integration checks of the shipped content, independent of synthetic error-path fixtures."""

from datetime import datetime, timedelta
from html.parser import HTMLParser
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from mealplan.catalog import Catalog
from mealplan.config import TORONTO, Settings
from mealplan.ingredients import load_records
from mealplan.schedule import DAYS, next_week
from mealplan.shopping import shopping_text, split_messages
from mealplan.web import create_app

CONTENT = Path(__file__).parents[1] / "content"


class Page(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.links = []
        self.assets = []
        self.ids = []
        self.text = []
        self.heading = []
        self.in_heading = False
        self.in_ingredients = False
        self.ingredient_lines = []
        self.in_ingredient = False
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "ul" and attrs.get("class") == "ingredients":
            self.in_ingredients = True
        if tag == "li" and self.in_ingredients:
            self.in_ingredient = True
            self.ingredient_lines.append("")
        if tag == "a":
            self.links.append(attrs.get("href", ""))
        if tag in {"script", "img"} and "src" in attrs:
            self.assets.append(attrs["src"])
        if tag == "link" and attrs.get("rel") == "stylesheet":
            self.assets.append(attrs["href"])
        if "id" in attrs:
            self.ids.append(attrs["id"])
        if tag == "h1":
            self.in_heading = True

    def handle_endtag(self, tag):
        if tag == "ul":
            self.in_ingredients = False
        if tag == "li":
            self.in_ingredient = False
        if tag == "h1":
            self.in_heading = False

    def handle_data(self, data):
        self.text.append(data)
        if self.in_ingredient:
            self.ingredient_lines[-1] += data
        if self.in_heading:
            self.heading.append(data)


@pytest.fixture
def shipped_app():
    settings = Settings(content_dir=CONTENT)
    return TestClient(create_app(settings, lambda: datetime(2026, 9, 27, 9, tzinfo=TORONTO)))


@pytest.mark.parametrize("recipe_id", range(1, 16))
def test_shipped_recipe_title_schedule_links_and_raw_purchases(shipped_app, recipe_id):
    catalog = Catalog(CONTENT)
    recipe = catalog.recipes[recipe_id]
    response = shipped_app.get(f"/recipes/{recipe_id}")
    assert response.status_code == 200
    assert response.content == catalog.path(recipe.html, ".html").read_bytes()
    page = Page(response.text)
    assert "".join(page.heading) == recipe.title
    assert len(page.ids) == len(set(page.ids))
    text = " ".join(page.text)
    assert "servings servings" not in text
    assert "tutorial pending" not in text
    assert f"Week {(recipe_id - 1) // 5 + 1} · {DAYS[(recipe_id - 1) % 5]}" in text
    assert "/today" in page.links
    assert "/static/recipes.html" in page.links
    if recipe_id > 1:
        assert f"/recipes/{recipe_id - 1}" in page.links
    if recipe_id < 15:
        assert f"/recipes/{recipe_id + 1}" in page.links
    for url in page.links + page.assets:
        assert url.startswith("/"), f"Unexpected non-local resource: {url}"
        assert shipped_app.get(url).status_code == 200, url
    metadata = shipped_app.get(f"/api/recipes/{recipe_id}").json()
    assert metadata["ingredients_available"] is True
    assert metadata["title"] == recipe.title
    record = metadata["raw_ingredients"]
    assert record["recipe_id"] == recipe_id
    assert all(not item["name"].startswith("cooked ") for item in record["ingredients"])
    assert len(page.ingredient_lines) == len(record["ingredients"])
    for item, line in zip(record["ingredients"], page.ingredient_lines, strict=True):
        assert item["name"] in line.casefold()
        assert float(line.split()[0]) == item["quantity"]
        if item["unit"] == "tin_156ml":
            assert "156 ml per tin" in line
        elif item["unit"] != "each":
            assert item["unit"] in line
    if recipe_id in {2, 7, 14}:
        assert "Before you start:" in text
        assert "200 g dry" in text
        assert "cooked yield varies" in text
        assert "3 cups cooked" not in text


def test_shipped_menu_and_health(shipped_app):
    menu = shipped_app.get("/static/recipes.html")
    assert menu.status_code == 200
    page = Page(menu.text)
    for recipe in Catalog(CONTENT).recipes.values():
        assert recipe.title in " ".join(page.text)
        assert f"/recipes/{recipe.id}" in page.links
    for url in page.links + page.assets:
        assert shipped_app.get(url).status_code == 200
    assert shipped_app.get("/health").json()["status"] == "ok"


@pytest.mark.parametrize("week", range(3))
def test_shipped_shopping_weeks(week):
    settings = Settings(content_dir=CONTENT)
    catalog = Catalog(CONTENT)
    saturday = settings.anchor + timedelta(days=week * 7 - 1)
    days = next_week(saturday, settings.anchor)
    records = load_records(catalog, [day.recipe_id for day in days])
    assert len(records) == 5
    text = shopping_text(catalog, days)
    for day in days:
        assert catalog.recipes[day.recipe_id].title in text
    assert "tutorial pending" not in text
    assert "tin(s), 156 ml each" in text
    assert all(len(part.encode("utf-16-le")) // 2 <= 4096 for part in split_messages(text))
