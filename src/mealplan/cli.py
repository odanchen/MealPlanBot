from __future__ import annotations

import argparse
import json
import logging
from datetime import date

from dotenv import load_dotenv

from .catalog import Catalog
from .config import Settings
from .ingredients import IngredientDataError, load_records
from .schedule import local_date, next_week
from .shopping import shopping_text


def configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s"
    )
    # HTTP clients and PTB internals may print token-bearing Bot API request URLs.
    for name in ("httpx", "httpcore", "telegram", "apscheduler"):
        logger = logging.getLogger(name)
        logger.handlers = [logging.NullHandler()]
        logger.propagate = False
        logger.setLevel(logging.CRITICAL + 1)


def main() -> int:
    parser = argparse.ArgumentParser(description="Local household meal planner")
    parser.add_argument(
        "--env-file", default=".env", help="Environment file (existing env takes precedence)"
    )
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("web", help="Run the local FastAPI server")
    commands.add_parser("bot", help="Run one Telegram polling/scheduling process")
    commands.add_parser("validate-ingredients", help="Validate all 15 ingredient records")
    for name in ("dry-run", "next-week"):
        command = commands.add_parser(name)
        command.add_argument(
            "--date", type=date.fromisoformat, help="Override today's Toronto date"
        )
    args = parser.parse_args()
    load_dotenv(args.env_file)
    configure_logging()
    try:
        settings = Settings.from_env()
        if args.command == "web":
            import uvicorn

            from .web import create_app

            uvicorn.run(create_app(settings), host=settings.web_host, port=settings.web_port)
        elif args.command == "bot":
            from .bot import run_bot

            run_bot(settings)
        else:
            catalog = Catalog(settings.content_dir)
            if args.command == "validate-ingredients":
                load_records(catalog, list(range(1, 16)))
                print("All 15 ingredient records are valid.")
            else:
                days = next_week(args.date or local_date(), settings.anchor)
                if args.command == "dry-run":
                    print(shopping_text(catalog, days))
                else:
                    print(
                        json.dumps(
                            [
                                {
                                    "date": str(day.date),
                                    "recipe_id": day.recipe_id,
                                    "title": catalog.recipes[day.recipe_id].title,
                                }
                                for day in days
                            ],
                            indent=2,
                        )
                    )
        return 0
    except IngredientDataError as error:
        logging.getLogger(__name__).error("%s; nothing sent", error)
        return 2
    except Exception as error:  # noqa: BLE001 — redact third-party exception URLs at CLI boundary
        # Avoid tracebacks from networking libraries that may include token URLs.
        logging.getLogger(__name__).error(
            "Operation failed (%s); check configuration and content", type(error).__name__
        )
        return 1
