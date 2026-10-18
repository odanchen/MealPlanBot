CREATE TABLE user_schedules (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL REFERENCES users(id),
 template_id TEXT NOT NULL,
 anchor_date TEXT NOT NULL,
 created_at INTEGER NOT NULL,
 updated_at INTEGER NOT NULL,
 version INTEGER NOT NULL DEFAULT 0 CHECK(version >= 0)
);
CREATE INDEX user_schedules_owner ON user_schedules(user_id);
