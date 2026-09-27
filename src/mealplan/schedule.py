from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime, timedelta

from .config import TORONTO

Clock = Callable[[], datetime]
DAYS = ("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")


def now() -> datetime:
    return datetime.now(TORONTO)


def local_date(clock: Clock = now) -> date:
    instant = clock()
    if instant.tzinfo is None:
        raise ValueError("Clock must return a timezone-aware datetime")
    return instant.astimezone(TORONTO).date()


@dataclass(frozen=True)
class Day:
    date: date
    day_of_week: str
    cycle_week: int
    recipe_id: int | None


def scheduled_day(day: date, anchor: date) -> Day:
    """Calendar arithmetic, including dates before the cycle anchor."""
    if anchor.weekday() != 6:
        raise ValueError("Cycle anchor must be a Sunday")
    offset = (day - anchor).days % 21
    week, weekday = divmod(offset, 7)
    return Day(day, DAYS[weekday], week + 1, week * 5 + weekday + 1 if weekday < 5 else None)


def next_week(day: date, anchor: date) -> list[Day]:
    """The strictly next Sunday (even when today is Sunday), through Thursday."""
    sunday = day + timedelta(days=7 - (day.weekday() + 1) % 7)
    return [scheduled_day(sunday + timedelta(days=i), anchor) for i in range(5)]
