CREATE TABLE shopping_lists (
 id TEXT PRIMARY KEY, name TEXT NOT NULL CHECK(length(trim(name)) > 0),
 owner_user_id TEXT NOT NULL REFERENCES users(id), created_at INTEGER NOT NULL,
 item_age_limit_seconds INTEGER NOT NULL DEFAULT 604800 CHECK(item_age_limit_seconds > 0),
 version INTEGER NOT NULL DEFAULT 0 CHECK(version >= 0)
);
CREATE TABLE memberships (
 list_id TEXT NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
 user_id TEXT NOT NULL REFERENCES users(id), joined_at INTEGER NOT NULL,
 PRIMARY KEY(list_id,user_id)
);
CREATE INDEX memberships_user ON memberships(user_id,list_id);
CREATE TABLE shopping_entries (
 id TEXT PRIMARY KEY, list_id TEXT NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
 display_name TEXT NOT NULL CHECK(length(trim(display_name)) > 0), ingredient_id TEXT,
 quantity_decimal TEXT, unit TEXT, created_at INTEGER NOT NULL, bought_at INTEGER, archived_at INTEGER,
 created_by TEXT REFERENCES users(id) ON DELETE SET NULL,
 occurrence_id TEXT, source_content_revision TEXT, version INTEGER NOT NULL DEFAULT 0 CHECK(version >= 0),
 CHECK((quantity_decimal IS NULL AND unit IS NULL) OR
       (quantity_decimal IS NOT NULL AND unit IS NOT NULL AND length(trim(unit)) > 0 AND length(quantity_decimal) > 0))
);
CREATE INDEX entries_list_state ON shopping_entries(list_id,archived_at,created_at,id);
CREATE TABLE request_deduplication (
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, operation TEXT NOT NULL, request_id TEXT NOT NULL,
 payload_hash TEXT NOT NULL, result_json TEXT NOT NULL, created_at INTEGER NOT NULL,
 PRIMARY KEY(user_id,operation,request_id)
);
CREATE INDEX request_deduplication_age ON request_deduplication(created_at);
