# MealPlanBot

A small household meal planner for Raspberry Pi OS Lite (Python 3.11+). FastAPI
serves existing HTML pages and a read-only API. A separate Telegram polling
process replies to `/help` and schedules a Saturday shopping message. No database,
recipe editor, Markdown runtime, external assets or cloud hosting.

## Quick start

Run these commands from this repository's root:

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -e '.[dev]'
cp .env.example .env
chmod 600 .env
mealplan web
```

Open `http://127.0.0.1:8000/`. Bot credentials are unnecessary for the website.
Edit `.env` for your household. To reach the site from another device, set
`WEB_HOST=0.0.0.0` and `LAN_URL` to the Pi's explicit household address, such as
`http://raspberrypi.local:8000/`. `WEB_PORT` defaults to 8000. The name must resolve
on household devices; otherwise configure a stable LAN address. There is no
authentication: keep this on the trusted home network, do not forward ports or
expose the Pi directly to the public internet. The browser can reach the page
only while the Pi and home network are available.

In a separate terminal, with the virtual environment active:

```sh
mealplan bot
```

Set `TELEGRAM_BOT_TOKEN` and comma-separated `TELEGRAM_CHAT_IDS` in `.env` first:

```dotenv
TELEGRAM_CHAT_IDS=123456789,987654321,-1001234567890
```

Use actual numeric chat IDs, without brackets. Negative IDs support group chats.
Whitespace around IDs is allowed; duplicates are removed in first-seen order.
Empty entries, non-integers and zero are rejected. An empty list permits the
website and dry-run commands but prevents the bot from starting.

Only `/help` from an allowed chat is acted on, and the reply goes only to that
requesting chat. Other commands and chats are ignored. The Saturday shopping
list goes to every configured chat; a delivery failure in one chat does not
prevent attempts for the remaining chats. Each private-chat recipient must first
open the bot and send a message; group recipients must have the bot added with
permission to send messages.

The former `TELEGRAM_CHAT_ID` environment variable remains a fallback if
`TELEGRAM_CHAT_IDS` is absent. If both are present, the plural variable wins,
even if empty. Replace the old line with the new one when migrating, then
restart `mealplan-bot`. `/help` includes `LAN_URL`, never a localhost link. Keep
only one polling bot instance per token; stop a local bot before starting its Pi
service. Secrets belong in the environment file, never version control or shell
command arguments. HTTP-client and Telegram-internal logging is suppressed to
avoid token-bearing request URLs; application errors log safe types and IDs.
The Telegram protocol itself necessarily sends its token in the HTTPS API path;
the application never displays that URL or includes secrets in website URLs.

## Content and assumptions

All 15 tutorials and their raw-purchase JSON records are included in
`content/recipes/`, with actual titles and filenames in `content/manifest.json`.
The ingredient quantities shown in the tutorials follow the supplied purchase
records. Bean preparation is explicit and separate from the meal's cooking time;
no cooked-to-dry yield is inferred. See [the content review](docs/content-review.md)
for editorial choices and verification limits.

Files are returned as-is: no runtime conversion, templating or build step. Keep
required assets under `content/static/`, referenced as `/static/...`. The static
menu at `/static/recipes.html` lists the actual dishes. The recipe header's
**Three-Week Kitchen** link opens that menu, **Today’s dish** follows the local
schedule (including rest days), and **Previous / Next** browse recipe order.
Those labels count the 15 cooking slots, not all 21 calendar days. Navigation
uses ordinary links and needs no JavaScript. Open pages through `mealplan web`,
rather than double-clicking HTML files: root-relative links need the app server.
If you edit titles, also update the static menu and neighbouring recipe labels.

Restart both services after changing the manifest; page and ingredient contents
are read from disk without a restart. Deploy content atomically when possible.

The manifest contains exactly IDs 1–15, each with `title`, `html`, and
`ingredients` paths relative to `CONTENT_DIR`. Paths must resolve within its
`recipes/` directory and have the correct extension; even symlink escapes are
rejected. Ingredients are not mounted as static content. `CONTENT_DIR` defaults
to `./content`, relative to the process working directory. This is a repository
deployment: copy the content directory alongside the installed Python package.

The anchor defaults to **Sunday, September 27, 2026**, and is configurable with
`CYCLE_ANCHOR=YYYY-MM-DD`. It must be a Sunday. It marks week 1, recipe 1. Each
week has Sunday–Thursday recipes, followed by Friday/Saturday rest days. The
rotation repeats every 21 calendar days, also for dates before the anchor.
All date selection uses `America/Toronto`, independent of the OS timezone.
“Next week” always starts on the strictly next Sunday, even when queried on a
Sunday; on Saturday this means the following day.

All 15 real ingredient JSON files are mapped and validated. See
[the purchase schema](docs/ingredients.md). The synthetic fixture under
`tests/fixtures/` remains for isolated error-path tests; never deploy it as real data. Missing ingredients do not stop the website or degrade its health.
The shopping job requires all five records to be valid before sending anything.
Names serve as stable normalized ingredient keys; authors must use consistent
raw purchase names. There are no categories or inferred cooking conversions.

## API and operator commands

| Route | Result |
| --- | --- |
| `/` | Uncached redirect to `/today` |
| `/today` | Uncached redirect to today's recipe or bundled `/rest-day` HTML |
| `/recipes/{id}` | Unmodified pre-generated HTML; unknown ID is 404, missing page is 503 |
| `/api/recipes` | IDs, titles, permanent HTML URLs and scheduled cycle days |
| `/api/recipes/{id}` | Metadata and validated raw ingredient record, when available |
| `/api/today` | Toronto date, day, cycle week, recipe or `status: rest_day` |
| `/api/next-week` | Five dated recipe records for the next Sunday–Thursday |
| `/health` | 200 when HTML is ready; 503 with missing page IDs otherwise |

Ingredient metadata has `ingredients_available`, `ingredients_status`
(`available`, `missing`, `invalid`), and nullable `raw_ingredients`. API responses
do not include the HTML body. Cycle days are 1–21, so recipe 6 is cycle day 8.
Invalid configuration or manifest prevents startup; a running health response
reports the validated configuration and manifest loaded at startup, plus current
HTML availability. Restart after changing configuration or the manifest.
Readiness checks file availability. Content integration tests additionally check
all 15 titles, schedules, navigation targets, ingredient amounts and weekly lists.

```sh
mealplan next-week                         # Dates and titles, no JSON required
mealplan next-week --date 2026-09-26        # Deterministic local date override
mealplan dry-run --date 2026-09-26          # Print shopping list; NEVER sends
mealplan validate-ingredients              # Validate every ingredient file
mealplan --env-file /path/to/mealplan.env next-week
pytest -q
ruff check src tests
```

With the supplied content, ingredient validation and all three weekly dry runs
succeed. If files are later missing or invalid, those commands exit 2 and report
the affected recipe IDs. Configuration/runtime failures exit 1. Successful
commands exit 0. Tests use synthetic data in temporary directories and mocked
Telegram calls; they do not contact Telegram.

## Saturday delivery

`SHOPPING_TIME=09:00` configures local Saturday time. The bot uses
`python-telegram-bot[job-queue]` 22.x `Application`, `CommandHandler`,
`Application.run_polling()` and `JobQueue.run_daily()`. Its explicit Toronto time
keeps 09:00 local across DST. PTB uses **Sunday=0, Saturday=6** (not Python's
weekday numbering), as documented in the
[JobQueue reference](https://docs.python-telegram-bot.org/en/stable/telegram.ext.jobqueue.html#telegram.ext.JobQueue.run_daily)
and verified by the tests against the installed library.

Each configured chat receives the same message naming the five dishes and their dates followed by one alphabetically
sorted purchase list. Compatible weights/volumes combine; incompatible units stay
separate. Tomato paste counts whole 156 ml tins. Oversized output splits into
numbered messages within Telegram's limit. Sends have network timeouts and at
most three attempts with bounded delays for transient errors; definitive
validation failures are not retried. Long server rate-limit delays stop the send.

There is no send history, catch-up, replay storage or exactly-once guarantee. If
the Pi is down at send time, the message may be missed. Polling drops old pending
commands at startup. Commands may be missed during downtime or rarely replied
to twice around restarts. Network ambiguity can duplicate a send after retry;
a failure partway through a split list can leave only earlier parts delivered.
Missing or invalid ingredient records always prevent the entire send.

## Install on Raspberry Pi OS Lite

Use a current 64-bit Raspberry Pi OS with Python 3.11 or newer. The user performs
these deployment commands on the Pi; the scaffold does not install services on
the development machine. System packages provide the IANA timezone database.
From a checkout on the Pi:

```sh
sudo apt update
sudo apt install python3 python3-venv tzdata rsync
sudo useradd --system --user-group --home-dir /opt/mealplan --shell /usr/sbin/nologin mealplan
sudo install -d -o root -g mealplan -m 0750 /opt/mealplan /etc/mealplan
sudo rsync -a --exclude='.git' --exclude='.venv' --exclude='.env*' --exclude='__pycache__' --exclude='*.egg-info' ./ /opt/mealplan/
sudo python3 -m venv /opt/mealplan/.venv
sudo /opt/mealplan/.venv/bin/python -m pip install /opt/mealplan
sudo install -o root -g mealplan -m 0640 .env.example /etc/mealplan/mealplan.env
sudoedit /etc/mealplan/mealplan.env
```

Set `CONTENT_DIR=/opt/mealplan/content`, a Sunday anchor, explicit LAN URL, desired
bind address, token and comma-separated allowed chat IDs. The root-owned environment file is
readable by the dedicated `mealplan` service group, not other users. Application
files can remain root-owned. Do not put the environment file in the repository.
Validate content as the service user:

```sh
sudo -u mealplan /opt/mealplan/.venv/bin/mealplan --env-file /etc/mealplan/mealplan.env next-week
sudo -u mealplan /opt/mealplan/.venv/bin/mealplan --env-file /etc/mealplan/mealplan.env validate-ingredients
sudo install -m 0644 systemd/mealplan-web.service systemd/mealplan-bot.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now mealplan-web.service mealplan-bot.service
curl --fail http://127.0.0.1:8000/health
sudo systemctl status mealplan-web mealplan-bot
sudo journalctl -u mealplan-web -u mealplan-bot -f
```

Both units run unprivileged, start at boot, restart on failure and have a read-only
filesystem view. The bot owns the schedule; there is no cron entry or timer unit.
Outbound HTTPS is required for Telegram; no public webhook or inbound Telegram
port is used. Keep the Pi clock synchronized.

To update from a new checkout, retain the environment file and back up any
household content first. Copy application code separately so incoming content
does not overwrite household recipe changes:

```sh
sudo systemctl stop mealplan-web mealplan-bot
sudo rsync -a --delete src/ /opt/mealplan/src/
sudo install -m 0644 pyproject.toml /opt/mealplan/pyproject.toml
sudo /opt/mealplan/.venv/bin/python -m pip install --upgrade /opt/mealplan
sudo install -m 0644 systemd/mealplan-web.service systemd/mealplan-bot.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl restart mealplan-web mealplan-bot
curl --fail http://127.0.0.1:8000/health
```

Deploy intentional content changes separately. Inspect service logs after updates.
For dependency reproducibility on a particular Pi, record the tested environment
with `pip freeze`; project dependencies use bounded compatible ranges.
