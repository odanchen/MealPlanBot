import json
from datetime import datetime

import pytest
from fastapi.testclient import TestClient

from mealplan.catalog import Catalog
from mealplan.config import TORONTO
from mealplan.web import create_app


def client(settings, day=27):
    return TestClient(create_app(settings, lambda: datetime(2026, 9, day, 9, tzinfo=TORONTO)))


def test_cooking_redirect_and_exact_html(settings, catalog):
    web = client(settings)
    response = web.get("/today", follow_redirects=False)
    assert response.headers["location"] == "/recipes/1"
    assert response.headers["cache-control"] == "no-store"
    assert web.get("/", follow_redirects=False).headers["cache-control"] == "no-store"
    assert (
        web.get("/recipes/1").content == catalog.path(catalog.recipes[1].html, ".html").read_bytes()
    )
    assert web.get("/api/today").json()["recipe_id"] == 1


@pytest.mark.parametrize("day", [25, 26])
def test_rest_day(settings, day):
    web = client(settings, day)
    assert web.get("/today", follow_redirects=False).headers["location"] == "/rest-day"
    assert web.get("/today").status_code == 200
    data = web.get("/api/today").json()
    assert data["status"] == "rest_day" and data["recipe"] is None


def test_api_and_missing_ingredients_are_healthy(settings, write_records):
    web = client(settings, 26)
    assert len(web.get("/api/recipes").json()) == 15
    assert web.get("/api/recipes").json()[-1]["cycle_day"] == 19
    assert web.get("/health").json()["status"] == "ok"
    data = web.get("/api/recipes/1").json()
    assert data["ingredients_available"] is False
    assert data["ingredients_status"] == "missing"
    assert "html" not in data and data["html_url"] == "/recipes/1"
    write_records([1])
    data = web.get("/api/recipes/1").json()
    assert data["ingredients_available"] is True
    assert data["raw_ingredients"]["ingredients"][0]["unit"] == "kg"
    days = web.get("/api/next-week").json()["days"]
    assert [day["recipe_id"] for day in days] == [1, 2, 3, 4, 5]
    assert days[0]["date"] == "2026-09-27"
    assert settings.token not in web.get("/health").text


@pytest.mark.parametrize("recipe_id", ["0", "16", "-1", "abc", "01", "%2e%2e%2f.env"])
def test_unknown_and_malicious_ids(settings, recipe_id):
    web = client(settings)
    assert web.get(f"/recipes/{recipe_id}").status_code == 404
    assert web.get(f"/api/recipes/{recipe_id}").status_code == 404


def test_missing_html_readiness(settings, catalog):
    catalog.path(catalog.recipes[15].html, ".html").unlink()
    web = client(settings)
    assert web.get("/health").status_code == 503
    assert web.get("/health").json()["missing_html_recipe_ids"] == [15]
    assert web.get("/recipes/15").status_code == 503
    assert web.get("/recipes/1").status_code == 200


@pytest.mark.parametrize("path", ["../secret.html", "/etc/secret.html", "static/wrong.html"])
def test_manifest_path_escape(content, path):
    manifest = content / "manifest.json"
    data = json.loads(manifest.read_text())
    data[0]["html"] = path
    manifest.write_text(json.dumps(data))
    with pytest.raises(ValueError):
        Catalog(content)


def test_manifest_symlink_escape(content, tmp_path):
    (tmp_path / "outside.html").write_text("secret")
    recipe = Catalog(content).recipes[1]
    page = content / recipe.html
    page.unlink()
    page.symlink_to(tmp_path / "outside.html")
    with pytest.raises(ValueError):
        Catalog(content)
