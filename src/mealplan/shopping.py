from __future__ import annotations

from decimal import Decimal

from .catalog import Catalog
from .ingredients import IngredientRecord, load_records
from .schedule import Day

NORMALIZE = {"kg": ("g", Decimal(1000)), "l": ("ml", Decimal(1000))}


def aggregate(records: list[IngredientRecord]) -> list[tuple[str, Decimal, str]]:
    totals: dict[tuple[str, str], Decimal] = {}
    for record in records:
        for item in record.ingredients:
            unit, factor = NORMALIZE.get(item.unit, (item.unit, Decimal(1)))
            key = (item.name, unit)
            totals[key] = totals.get(key, Decimal(0)) + item.quantity * factor
    return [(name, amount, unit) for (name, unit), amount in sorted(totals.items())]


def shopping_text(catalog: Catalog, days: list[Day]) -> str:
    records = load_records(catalog, [day.recipe_id for day in days if day.recipe_id is not None])
    lines = [f"Shopping: {days[0].date} - {days[-1].date}", ""]
    lines += [
        f"{day.day_of_week} {day.date}: {catalog.recipes[day.recipe_id].title}" for day in days
    ]
    lines += ["", "Purchases:"]
    for name, amount, unit in aggregate(records):
        label = "tin(s), 156 ml each" if unit == "tin_156ml" else unit
        lines.append(f"• {name}: {format(amount.normalize(), 'f')} {label}")
    return "\n".join(lines)


def split_messages(text: str, limit: int = 4096) -> list[str]:
    """Split on lines, counting UTF-16 units conservatively for Telegram."""

    def size(value: str) -> int:
        return len(value.encode("utf-16-le")) // 2

    if size(text) <= limit:
        return [text]
    budget = limit - 32  # Space for numbered prefixes.
    if budget < 2:
        raise ValueError("Message limit is too small")
    chunks, current = [], ""
    for char in text:
        if size(current) + size(char) > budget:
            newline = current.rfind("\n")
            if newline > 0:
                chunks.append(current[: newline + 1])
                current = current[newline + 1 :]
            else:
                chunks.append(current)
                current = ""
        current += char
    if current:
        chunks.append(current)
    return [f"({i}/{len(chunks)})\n{chunk}" for i, chunk in enumerate(chunks, 1)]
