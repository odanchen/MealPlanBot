from datetime import datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from telegram.error import BadRequest, NetworkError, RetryAfter

from mealplan.bot import BotService, build_application, send_with_retry
from mealplan.config import TORONTO


def context():
    return SimpleNamespace(bot=SimpleNamespace(send_message=AsyncMock()))


async def test_help_allowlist(settings, catalog):
    service = BotService(settings, catalog)
    ctx = context()
    await service.help(SimpleNamespace(effective_chat=SimpleNamespace(id=99)), ctx)
    ctx.bot.send_message.assert_not_called()
    await service.help(SimpleNamespace(effective_chat=None), ctx)
    ctx.bot.send_message.assert_not_called()
    await service.help(SimpleNamespace(effective_chat=SimpleNamespace(id=42)), ctx)
    assert ctx.bot.send_message.call_args.kwargs["chat_id"] == 42
    assert settings.lan_url in ctx.bot.send_message.call_args.kwargs["text"]


async def test_scheduled_send_skips_missing_then_sends(settings, catalog, write_records, caplog):
    service = BotService(settings, catalog, lambda: datetime(2026, 9, 26, 9, tzinfo=TORONTO))
    ctx = context()
    await service.shopping(ctx)
    ctx.bot.send_message.assert_not_called()
    assert "recipe 1 (missing)" in caplog.text and "recipe 5 (missing)" in caplog.text
    write_records([1, 2, 3, 4, 5])
    await service.shopping(ctx)
    text = ctx.bot.send_message.call_args.kwargs["text"]
    assert "2026-09-27 – 2026-10-01" in text
    assert "tomato paste: 5 tin(s), 156 ml each" in text
    assert "dry chickpeas: 2500 g" in text
    assert ctx.bot.send_message.call_args.kwargs["chat_id"] == 42


async def test_invalid_week_never_sends(settings, catalog, write_records):
    write_records([1, 2, 3, 4, 5])
    catalog.path(catalog.recipes[4].ingredients, ".json").write_text("invalid")
    service = BotService(settings, catalog, lambda: datetime(2026, 9, 26, 9, tzinfo=TORONTO))
    ctx = context()
    await service.shopping(ctx)
    ctx.bot.send_message.assert_not_called()


async def test_bounded_retries_and_definitive_errors(monkeypatch):
    sleep = AsyncMock()
    monkeypatch.setattr("mealplan.bot.asyncio.sleep", sleep)
    bot = SimpleNamespace(send_message=AsyncMock(side_effect=[NetworkError("fail"), None]))
    await send_with_retry(bot, 42, "test")
    assert bot.send_message.call_count == 2
    sleep.assert_awaited_once_with(1)
    bot.send_message = AsyncMock(side_effect=NetworkError("fail"))
    with pytest.raises(NetworkError):
        await send_with_retry(bot, 42, "test")
    assert bot.send_message.call_count == 3
    bot.send_message = AsyncMock(side_effect=BadRequest("invalid"))
    with pytest.raises(BadRequest):
        await send_with_retry(bot, 42, "test")
    assert bot.send_message.call_count == 1
    bot.send_message = AsyncMock(side_effect=RetryAfter(60))
    with pytest.raises(RetryAfter):
        await send_with_retry(bot, 42, "test")
    assert bot.send_message.call_count == 1


def test_jobqueue_saturday_local_time(settings):
    app = build_application(settings)
    (job,) = app.job_queue.jobs()
    assert job.name == "saturday-shopping"
    assert str(job.job.trigger.timezone) == "America/Toronto"
    first = job.job.trigger.get_next_fire_time(None, datetime(2026, 3, 6, tzinfo=TORONTO))
    following = job.job.trigger.get_next_fire_time(first, first)
    assert (first.weekday(), first.hour, first.minute) == (5, 9, 0)
    assert (following.weekday(), following.hour, following.minute) == (5, 9, 0)
    assert first.utcoffset() != following.utcoffset()


async def test_multiple_chats_help_replies_only_to_requesting_chat(settings, catalog):
    from dataclasses import replace

    service = BotService(replace(settings, chat_ids=(42, -1001234567890)), catalog)
    ctx = context()
    for chat_id in (42, -1001234567890):
        ctx.bot.send_message.reset_mock()
        await service.help(SimpleNamespace(effective_chat=SimpleNamespace(id=chat_id)), ctx)
        ctx.bot.send_message.assert_awaited_once()
        assert ctx.bot.send_message.call_args.kwargs["chat_id"] == chat_id
    ctx.bot.send_message.reset_mock()
    await service.help(SimpleNamespace(effective_chat=SimpleNamespace(id=99)), ctx)
    ctx.bot.send_message.assert_not_called()


async def test_shopping_broadcast_deduplicates_and_preserves_parts(settings, catalog, monkeypatch):
    from dataclasses import replace

    service = BotService(replace(settings, chat_ids=(42, -1001234567890, 42)), catalog)
    text = "purchase line\n" * 900
    monkeypatch.setattr("mealplan.bot.shopping_text", lambda *_: text)
    ctx = context()
    await service.shopping(ctx)
    calls = ctx.bot.send_message.await_args_list
    first = [call.kwargs["text"] for call in calls if call.kwargs["chat_id"] == 42]
    second = [call.kwargs["text"] for call in calls if call.kwargs["chat_id"] == -1001234567890]
    assert len(first) > 1
    assert first == second
    assert len(calls) == 2 * len(first)
    assert first[0].startswith(f"(1/{len(first)})")


async def test_shopping_failure_does_not_block_other_chats(settings, catalog, monkeypatch, caplog):
    from dataclasses import replace

    from telegram.error import Forbidden

    service = BotService(replace(settings, chat_ids=(42, -1001234567890)), catalog)
    monkeypatch.setattr("mealplan.bot.shopping_text", lambda *_: "purchase line\n" * 900)
    ctx = context()

    async def send(**kwargs):
        if kwargs["chat_id"] == 42:
            raise Forbidden("private-token-or-id")

    ctx.bot.send_message.side_effect = send
    await service.shopping(ctx)
    calls = ctx.bot.send_message.await_args_list
    assert sum(call.kwargs["chat_id"] == 42 for call in calls) == 1
    assert sum(call.kwargs["chat_id"] == -1001234567890 for call in calls) > 1
    assert "recipient 1 (Forbidden)" in caplog.text
    assert "private-token-or-id" not in caplog.text
    assert "-1001234567890" not in caplog.text


async def test_missing_data_prevents_broadcast_to_all_chats(settings, catalog):
    from dataclasses import replace

    service = BotService(replace(settings, chat_ids=(42, -1001234567890)), catalog)
    ctx = context()
    await service.shopping(ctx)
    ctx.bot.send_message.assert_not_called()
