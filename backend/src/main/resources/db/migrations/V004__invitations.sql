CREATE TABLE invitations (
 id TEXT PRIMARY KEY,
 list_id TEXT NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
 token_hash TEXT NOT NULL UNIQUE,
 created_by TEXT NOT NULL REFERENCES users(id),
 created_at INTEGER NOT NULL,
 expires_at INTEGER NOT NULL CHECK(expires_at = created_at + 259200000),
 withdrawn_at INTEGER
);
CREATE INDEX invitations_list ON invitations(list_id,created_at);
