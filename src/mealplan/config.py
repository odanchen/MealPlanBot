from __future__ import annotations

import os
import re
from dataclasses import dataclass, field
from datetime import date, time
from pathlib import Path
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

TORONTO = ZoneInfo("America/Toronto")


@dataclass(frozen=True)
class Settings:
    anchor: date = date(2026, 9, 27)
    content_dir: Path = Path("content")
    web_host: str = "127.0.0.1"
    web_port: int = 8000
    lan_url: str = "http://raspberrypi.local:8000/"
    shopping_time: time = time(9, tzinfo=TORONTO)
    token: str = field(default="", repr=False)
    chat_ids: tuple[int, ...] = field(default=(), repr=False)

    def __post_init__(self) -> None:
        if any(type(chat_id) is not int or chat_id == 0 for chat_id in self.chat_ids):
            raise ValueError("Telegram chat IDs must be nonzero integers")
        # Preserve configured order while preventing duplicate notifications.
        object.__setattr__(self, "chat_ids", tuple(dict.fromkeys(self.chat_ids)))
        if self.anchor.weekday() != 6:
            raise ValueError("CYCLE_ANCHOR must be a Sunday")
        if not 1 <= self.web_port <= 65535:
            raise ValueError("WEB_PORT must be between 1 and 65535")
        if self.web_host not in {"127.0.0.1", "0.0.0.0"}:
            raise ValueError("WEB_HOST must be 127.0.0.1 or 0.0.0.0")
        url = urlsplit(self.lan_url)
        if (
            url.scheme not in {"http", "https"}
            or not url.hostname
            or url.hostname.lower() in {"localhost", "127.0.0.1", "::1", "0.0.0.0"}
            or url.username
            or url.password
            or url.query
            or url.fragment
        ):
            raise ValueError("LAN_URL must be an explicit household URL without credentials")
        try:
            _ = url.port  # Access validates the parsed port.
        except ValueError:
            raise ValueError("LAN_URL has an invalid port") from None
        if self.shopping_time.tzinfo != TORONTO:
            raise ValueError("SHOPPING_TIME must use America/Toronto")

    def require_bot(self) -> None:
        if not self.token or not self.chat_ids:
            raise ValueError("TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_IDS are required for the bot")

    @classmethod
    def from_env(cls) -> Settings:
        try:
            hour, minute = map(int, os.getenv("SHOPPING_TIME", "09:00").split(":"))
            # An explicitly set plural variable takes precedence, including an empty one.
            raw_ids = os.getenv("TELEGRAM_CHAT_IDS", os.getenv("TELEGRAM_CHAT_ID", ""))
            parts = [part.strip() for part in raw_ids.split(",")] if raw_ids.strip() else []
            if any(re.fullmatch(r"-?[0-9]+", part) is None for part in parts):
                raise ValueError("Invalid Telegram chat list")
            return cls(
                anchor=date.fromisoformat(os.getenv("CYCLE_ANCHOR", "2026-09-27")),
                content_dir=Path(os.getenv("CONTENT_DIR", "content")).resolve(),
                web_host=os.getenv("WEB_HOST", "127.0.0.1"),
                web_port=int(os.getenv("WEB_PORT", "8000")),
                lan_url=os.getenv("LAN_URL", "http://raspberrypi.local:8000/"),
                shopping_time=time(hour, minute, tzinfo=TORONTO),
                token=os.getenv("TELEGRAM_BOT_TOKEN", ""),
                chat_ids=tuple(int(part) for part in parts),
            )
        except (ValueError, TypeError):
            # Never echo environment values (including secrets) in startup errors.
            raise ValueError(
                "Invalid configuration; check the environment against .env.example"
            ) from None
