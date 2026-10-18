CREATE TABLE users (
 id TEXT PRIMARY KEY NOT NULL CHECK(length(id)=36),
 display_name TEXT,
 current_email TEXT,
 created_at INTEGER NOT NULL,
 last_login_at INTEGER NOT NULL
);
CREATE TABLE external_identities (
 issuer TEXT NOT NULL,
 subject TEXT NOT NULL,
 user_id TEXT NOT NULL REFERENCES users(id),
 created_at INTEGER NOT NULL,
 last_login_at INTEGER NOT NULL,
 PRIMARY KEY(issuer,subject)
);
CREATE INDEX external_identity_user ON external_identities(user_id);
