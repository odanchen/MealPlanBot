import logging
import sys
from dataclasses import replace

import pytest

from mealplan.cli import configure_logging, main
from mealplan.config import Settings


@pytest.fixture
def cli_env(monkeypatch, settings, tmp_path):
    # Isolate from developer credentials and local .env files.
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("CONTENT_DIR", str(settings.content_dir))
    monkeypatch.setenv("CYCLE_ANCHOR", "2026-09-27")
    monkeypatch.setenv("WEB_HOST", "127.0.0.1")
    monkeypatch.setenv("WEB_PORT", "8000")
    monkeypatch.setenv("LAN_URL", "http://raspberrypi.local:8000/")
    monkeypatch.setenv("SHOPPING_TIME", "09:00")
    monkeypatch.delenv("TELEGRAM_BOT_TOKEN", raising=False)
    monkeypatch.delenv("TELEGRAM_CHAT_ID", raising=False)
    monkeypatch.delenv("TELEGRAM_CHAT_IDS", raising=False)


def invoke(monkeypatch, *args):
    monkeypatch.setattr(sys, "argv", ["mealplan", *args])
    return main()


def test_cli_missing_data_and_success(cli_env, monkeypatch, capsys, write_records):
    assert invoke(monkeypatch, "next-week", "--date", "2026-09-26") == 0
    assert '"recipe_id": 1' in capsys.readouterr().out
    assert invoke(monkeypatch, "dry-run", "--date", "2026-09-26") == 2
    assert capsys.readouterr().out == ""
    assert invoke(monkeypatch, "validate-ingredients") == 2
    write_records(range(1, 16))
    assert invoke(monkeypatch, "validate-ingredients") == 0
    assert invoke(monkeypatch, "dry-run", "--date", "2026-09-26") == 0
    assert "dry chickpeas: 2500 g" in capsys.readouterr().out


@pytest.mark.parametrize(
    "url",
    [
        "http://localhost:8000",
        "http://127.0.0.1",
        "http://[::1]",
        "http://user:secret@pi.local",
        "file:///tmp/a",
        "http://pi:bad",
    ],
)
def test_lan_url_validation(url):
    with pytest.raises(ValueError):
        Settings(lan_url=url)


def test_config_and_logs_do_not_leak(settings, monkeypatch, caplog, cli_env):
    secret = "sensitive-value"
    assert settings.token not in repr(settings)
    monkeypatch.setenv("TELEGRAM_CHAT_IDS", secret)
    assert invoke(monkeypatch, "bot") == 1
    assert secret not in caplog.text
    with pytest.raises(ValueError):
        replace(settings, token="").require_bot()
    configure_logging()
    logging.getLogger("httpx").info("https://api.telegram.org/bot%s/getUpdates", secret)
    assert secret not in caplog.text


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("42", (42,)),
        ("42,-1001234567890, 73", (42, -1001234567890, 73)),
        ("42, 42,-1001234567890,42", (42, -1001234567890)),
        ("", ()),
        ("  ", ()),
    ],
)
def test_chat_list_parsing(cli_env, monkeypatch, raw, expected):
    monkeypatch.setenv("TELEGRAM_CHAT_IDS", raw)
    settings = Settings.from_env()
    assert settings.chat_ids == expected
    assert "chat_ids" not in repr(settings)


@pytest.mark.parametrize(
    "raw", ["0", "42,0", "42,", ",42", "42,,73", "[42,73]", "42,not-an-id", "True", "4.2", "4_2"]
)
def test_invalid_chat_lists_fail_closed(cli_env, monkeypatch, raw):
    monkeypatch.setenv("TELEGRAM_CHAT_IDS", raw)
    with pytest.raises(ValueError, match="Invalid configuration") as error:
        Settings.from_env()
    assert raw not in str(error.value)


def test_legacy_chat_id_and_plural_precedence(cli_env, monkeypatch):
    monkeypatch.setenv("TELEGRAM_CHAT_ID", "42")
    assert Settings.from_env().chat_ids == (42,)
    monkeypatch.setenv("TELEGRAM_CHAT_IDS", "73,-1001234567890")
    assert Settings.from_env().chat_ids == (73, -1001234567890)
    monkeypatch.setenv("TELEGRAM_CHAT_IDS", "")
    assert Settings.from_env().chat_ids == ()
    with pytest.raises(ValueError):
        replace(Settings.from_env(), token="synthetic").require_bot()


@pytest.mark.parametrize("ids", [(0,), (True,), ("42",), (None,)])
def test_direct_settings_reject_invalid_chat_ids(ids):
    with pytest.raises(ValueError):
        Settings(chat_ids=ids)
