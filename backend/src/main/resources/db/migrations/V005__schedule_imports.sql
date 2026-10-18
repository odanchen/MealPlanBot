CREATE TABLE schedule_attachments (
 id TEXT PRIMARY KEY, list_id TEXT NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
 source_schedule_id TEXT REFERENCES user_schedules(id), detached_at INTEGER, enabled INTEGER NOT NULL CHECK(enabled IN (0,1)),
 interval_unit TEXT NOT NULL CHECK(interval_unit IN ('day','week')), interval_count INTEGER NOT NULL CHECK(interval_count>0),
 weekday INTEGER CHECK(weekday BETWEEN 1 AND 7), local_time TEXT NOT NULL, time_zone TEXT NOT NULL, recurrence_start_date TEXT NOT NULL,
 next_due_at INTEGER NOT NULL, configuration_version INTEGER NOT NULL DEFAULT 0, version INTEGER NOT NULL DEFAULT 0,
 created_at INTEGER NOT NULL, last_covered_start_date TEXT, last_covered_end_date_exclusive TEXT,
 CHECK(detached_at IS NOT NULL OR source_schedule_id IS NOT NULL),
 CHECK((interval_unit='week' AND weekday IS NOT NULL) OR (interval_unit='day' AND weekday IS NULL)),
 CHECK((last_covered_start_date IS NULL AND last_covered_end_date_exclusive IS NULL) OR
 (last_covered_start_date IS NOT NULL AND last_covered_end_date_exclusive IS NOT NULL AND last_covered_start_date<last_covered_end_date_exclusive))
);
CREATE UNIQUE INDEX one_active_schedule ON schedule_attachments(list_id) WHERE detached_at IS NULL;
CREATE INDEX attachments_due ON schedule_attachments(enabled,detached_at,next_due_at);
CREATE TABLE schedule_occurrences (
 id TEXT PRIMARY KEY, attachment_id TEXT NOT NULL REFERENCES schedule_attachments(id) ON DELETE CASCADE,
 kind TEXT NOT NULL CHECK(kind IN ('initial','recurring')), scheduled_at INTEGER NOT NULL,
 configuration_version INTEGER NOT NULL, coverage_start_date TEXT NOT NULL, coverage_end_date_exclusive TEXT NOT NULL,
 time_zone TEXT NOT NULL, content_revision TEXT, source_schedule_version INTEGER,
 effective_template_id TEXT, effective_anchor_date TEXT,
 status TEXT NOT NULL CHECK(status IN ('pending','failed','completed','skipped')), failure_code TEXT, completed_at INTEGER,
 UNIQUE(attachment_id,kind,scheduled_at), CHECK(coverage_start_date<=coverage_end_date_exclusive),
 CHECK((status IN ('pending','failed') AND completed_at IS NULL) OR (status IN ('completed','skipped') AND completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX one_initial_occurrence ON schedule_occurrences(attachment_id) WHERE kind='initial';
CREATE TABLE shopping_entries_v5 (
 id TEXT PRIMARY KEY, list_id TEXT NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
 display_name TEXT NOT NULL CHECK(length(trim(display_name))>0), ingredient_id TEXT,
 quantity_decimal TEXT, unit TEXT, created_at INTEGER NOT NULL, bought_at INTEGER, archived_at INTEGER,
 created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
 occurrence_id TEXT REFERENCES schedule_occurrences(id) ON DELETE CASCADE, source_content_revision TEXT,
 version INTEGER NOT NULL DEFAULT 0 CHECK(version>=0),
 CHECK((quantity_decimal IS NULL AND unit IS NULL) OR (quantity_decimal IS NOT NULL AND unit IS NOT NULL AND length(trim(unit))>0 AND length(quantity_decimal)>0))
);
INSERT INTO shopping_entries_v5 SELECT * FROM shopping_entries;
DROP TABLE shopping_entries;
ALTER TABLE shopping_entries_v5 RENAME TO shopping_entries;
CREATE INDEX entries_list_state ON shopping_entries(list_id,archived_at,created_at,id);
