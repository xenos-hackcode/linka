import { Pool } from "pg";

// Real Postgres (2026-09-14) - was a single local SQLite file, fine for local
// dev but fatal for a real deploy: Cloud Run containers have no persistent
// local disk, so every redeploy/restart/cold-start would have silently wiped
// the whole database. DB_SOCKET_PATH (set to /cloudsql/<INSTANCE_CONNECTION_NAME>
// when deployed with --add-cloudsql-instances) connects over the real Cloud
// SQL unix socket; unset falls back to a local Postgres for dev (DB_HOST/
// DB_PORT), matching how cedal-server's own DatabaseFactory picks between
// Cloud SQL and local storage.
export const pool = new Pool(
  process.env.DB_SOCKET_PATH
    ? {
        host: process.env.DB_SOCKET_PATH,
        user: process.env.DB_USER ?? "linka",
        password: process.env.DB_PASSWORD,
        database: process.env.DB_NAME ?? "linka",
      }
    : {
        host: process.env.DB_HOST ?? "localhost",
        port: Number(process.env.DB_PORT ?? 5432),
        user: process.env.DB_USER ?? "linka",
        password: process.env.DB_PASSWORD ?? "linka",
        database: process.env.DB_NAME ?? "linka",
      }
);

// created_at/updated_at stay BIGINT (epoch millis, exactly what Date.now()
// already produced under SQLite) rather than a real TIMESTAMP type - every
// route already treats them as plain numbers, so this is the one schema
// choice that keeps types.ts and every route's JS untouched.
export async function initSchema(): Promise<void> {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      display_name TEXT NOT NULL DEFAULT '',
      bio TEXT NOT NULL DEFAULT '',
      created_at BIGINT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS requests (
      id TEXT PRIMARY KEY,
      requester_id TEXT NOT NULL REFERENCES users(id),
      summary TEXT NOT NULL,
      reason TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',
      responder_id TEXT REFERENCES users(id),
      created_at BIGINT NOT NULL,
      updated_at BIGINT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS global_messages (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id),
      text TEXT NOT NULL,
      created_at BIGINT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS groups (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      owner_id TEXT NOT NULL REFERENCES users(id),
      created_at BIGINT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS group_members (
      group_id TEXT NOT NULL REFERENCES groups(id),
      user_id TEXT NOT NULL REFERENCES users(id),
      joined_at BIGINT NOT NULL,
      PRIMARY KEY (group_id, user_id)
    );

    CREATE TABLE IF NOT EXISTS group_messages (
      id TEXT PRIMARY KEY,
      group_id TEXT NOT NULL REFERENCES groups(id),
      user_id TEXT NOT NULL REFERENCES users(id),
      text TEXT NOT NULL,
      created_at BIGINT NOT NULL
    );

    -- Mutual: a block in either direction stops DMs both ways (see routes/dms.ts) -
    -- matches how blocking works on most real chat apps, and avoids a one-sided block
    -- where the blocked person can still reach the blocker.
    CREATE TABLE IF NOT EXISTS blocks (
      blocker_id TEXT NOT NULL REFERENCES users(id),
      blocked_id TEXT NOT NULL REFERENCES users(id),
      created_at BIGINT NOT NULL,
      PRIMARY KEY (blocker_id, blocked_id)
    );

    CREATE TABLE IF NOT EXISTS dm_messages (
      id TEXT PRIMARY KEY,
      sender_id TEXT NOT NULL REFERENCES users(id),
      recipient_id TEXT NOT NULL REFERENCES users(id),
      text TEXT NOT NULL,
      created_at BIGINT NOT NULL
    );
  `);
}
