from datetime import UTC, date, datetime, timedelta

import pytest

from mealplan.config import TORONTO, Settings
from mealplan.schedule import local_date, next_week, scheduled_day

ANCHOR = date(2026, 9, 27)


def test_entire_cycle_wraparound_and_before_anchor():
    expected = [
        1,
        2,
        3,
        4,
        5,
        None,
        None,
        6,
        7,
        8,
        9,
        10,
        None,
        None,
        11,
        12,
        13,
        14,
        15,
        None,
        None,
    ]
    for offset in range(-42, 64):
        result = scheduled_day(ANCHOR + timedelta(days=offset), ANCHOR)
        assert result.recipe_id == expected[offset % 21]
        assert result.cycle_week == (offset % 21) // 7 + 1


def test_anchor_must_be_sunday():
    with pytest.raises(ValueError):
        Settings(anchor=date(2026, 9, 28))


@pytest.mark.parametrize(
    "instant,expected",
    [
        ("2026-03-08T04:59:00+00:00", "2026-03-07"),
        ("2026-03-08T05:00:00+00:00", "2026-03-08"),
        ("2026-03-08T07:01:00+00:00", "2026-03-08"),
        ("2026-11-01T05:30:00+00:00", "2026-11-01"),
        ("2026-11-01T06:30:00+00:00", "2026-11-01"),
    ],
)
def test_toronto_dst_dates(instant, expected):
    day = local_date(lambda: datetime.fromisoformat(instant))
    assert day == date.fromisoformat(expected)
    assert scheduled_day(day, ANCHOR).day_of_week in {"Saturday", "Sunday"}


@pytest.mark.parametrize("saturday", [date(2026, 3, 7), date(2026, 10, 31), date(2026, 10, 17)])
def test_saturday_next_sunday(saturday):
    days = next_week(saturday, ANCHOR)
    assert [day.date for day in days] == [saturday + timedelta(days=i) for i in range(1, 6)]
    assert days[0].day_of_week == "Sunday"
    assert days[-1].day_of_week == "Thursday"
    assert all(day.recipe_id for day in days)


def test_sunday_means_following_week():
    assert next_week(ANCHOR, ANCHOR)[0].date == ANCHOR + timedelta(days=7)


def test_clock_requires_timezone():
    with pytest.raises(ValueError):
        local_date(lambda: datetime(2026, 9, 27))  # noqa: DTZ001 — exercise rejection of naive clocks


def test_local_morning_changes_utc_with_dst():
    winter = datetime.combine(date(2026, 3, 7), Settings().shopping_time)
    summer = datetime.combine(date(2026, 3, 14), Settings().shopping_time)
    assert winter.hour == summer.hour == 9
    assert winter.astimezone(UTC).hour == 14
    assert summer.astimezone(UTC).hour == 13
    assert winter.tzinfo == TORONTO
