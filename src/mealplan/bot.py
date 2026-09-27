from __future__ import annotations

import asyncio
import logging
from datetime import timedelta

from telegram import Update
from telegram.error import BadRequest, NetworkError, RetryAfter, TelegramError
from telegram.ext import Application, CommandHandler, ContextTypes

from .catalog import Catalog
from .config import Settings
from .ingredients import IngredientDataError
from .schedule import Clock, local_date, next_week, now
from .shopping import shopping_text, split_messages

log = logging.getLogger(__name__)


async def send_with_retry(bot, chat_id: int, text: str) -> None:
    """At most three attempts, bounded backoff; never retry definitive errors."""
    for attempt in range(3):
        try:
            await bot.send_message(
                chat_id=chat_id,
                text=text,
                connect_timeout=5,
                read_timeout=15,
                write_timeout=15,
                pool_timeout=5,
            )
            return
        except BadRequest:
            # BadRequest inherits NetworkError in PTB; catch it first.
            raise
        except RetryAfter as error:
            delay = error.retry_after
            seconds = delay.total_seconds() if isinstance(delay, timedelta) else delay
            if attempt == 2 or seconds > 30:
                raise
            await asyncio.sleep(seconds)
        except NetworkError:
            if attempt == 2:
                raise
            await asyncio.sleep(2**attempt)


class BotService:
    def __init__(self, settings: Settings, catalog: Catalog, clock: Clock = now):
        settings.require_bot()
        self.settings, self.catalog, self.clock = settings, catalog, clock

    async def help(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        if update.effective_chat is None or update.effective_chat.id not in self.settings.chat_ids:
            return
        await send_with_retry(
            context.bot,
            update.effective_chat.id,
            "Your meal planner has a three-week Sunday-Thursday cycle. "
            "Friday and Saturday are rest days. A shopping list arrives Saturday "
            "when ingredient data is ready. Today's meal: " + self.settings.lan_url,
        )

    async def shopping(self, context: ContextTypes.DEFAULT_TYPE) -> None:
        days = next_week(local_date(self.clock), self.settings.anchor)
        try:
            text = shopping_text(self.catalog, days)
        except IngredientDataError as error:
            log.error("Shopping send skipped: %s", error)
            return
        parts = split_messages(text)
        for recipient, chat_id in enumerate(self.settings.chat_ids, 1):
            try:
                for part in parts:
                    await send_with_retry(context.bot, chat_id, part)
            except TelegramError as error:
                # A failed recipient must not prevent delivery to the remaining chats.
                # Log an ordinal, not the private ID or token-bearing exception text.
                log.error(
                    "Shopping send stopped for recipient %d (%s)", recipient, type(error).__name__
                )


async def error_handler(update: object, context: ContextTypes.DEFAULT_TYPE) -> None:
    log.error("Telegram operation failed (%s)", type(context.error).__name__)


def build_application(settings: Settings, clock: Clock = now) -> Application:
    service = BotService(settings, Catalog(settings.content_dir), clock)
    app = (
        Application.builder()
        .token(settings.token)
        .connect_timeout(5)
        .read_timeout(15)
        .write_timeout(15)
        .pool_timeout(5)
        .get_updates_connect_timeout(5)
        .get_updates_read_timeout(35)
        .get_updates_write_timeout(15)
        .get_updates_pool_timeout(5)
        .build()
    )
    app.add_handler(CommandHandler("help", service.help))
    app.add_error_handler(error_handler)
    # PTB 22.x uses Sunday=0 ... Saturday=6, unlike datetime.weekday().
    app.job_queue.run_daily(
        service.shopping,
        time=settings.shopping_time,
        days=(6,),
        name="saturday-shopping",
        job_kwargs={"misfire_grace_time": 60, "coalesce": True, "max_instances": 1},
    )
    return app


def run_bot(settings: Settings) -> None:
    app = build_application(settings)
    app.run_polling(allowed_updates=["message"], drop_pending_updates=True, bootstrap_retries=0)
