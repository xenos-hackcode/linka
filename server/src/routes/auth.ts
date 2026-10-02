import bcrypt from "bcryptjs";
import { Router } from "express";
import { v4 as uuid } from "uuid";
import { AuthedRequest, requireAuth, signToken } from "../auth";
import { pool } from "../db";
import { toPublicUser, UserRow } from "../types";

export const authRouter = Router();

authRouter.post("/register", async (req, res) => {
  const { username, password, displayName } = req.body ?? {};
  if (typeof username !== "string" || typeof password !== "string" || username.length < 3 || password.length < 6) {
    res.status(400).json({ error: "username must be >=3 chars, password >=6 chars" });
    return;
  }
  const existing = await pool.query("SELECT id FROM users WHERE username = $1", [username]);
  if (existing.rows.length > 0) {
    res.status(409).json({ error: "username taken" });
    return;
  }
  const id = uuid();
  const passwordHash = bcrypt.hashSync(password, 10);
  await pool.query(
    "INSERT INTO users (id, username, password_hash, display_name, bio, created_at) VALUES ($1, $2, $3, $4, '', $5)",
    [id, username, passwordHash, typeof displayName === "string" ? displayName : username, Date.now()]
  );
  const { rows } = await pool.query("SELECT * FROM users WHERE id = $1", [id]);
  const row = rows[0] as UserRow;
  res.status(201).json({ token: signToken({ userId: id, username }), user: toPublicUser(row) });
});

authRouter.post("/login", async (req, res) => {
  const { username, password } = req.body ?? {};
  if (typeof username !== "string" || typeof password !== "string") {
    res.status(400).json({ error: "username and password required" });
    return;
  }
  const { rows } = await pool.query("SELECT * FROM users WHERE username = $1", [username]);
  const row = rows[0] as UserRow | undefined;
  if (!row || !bcrypt.compareSync(password, row.password_hash)) {
    res.status(401).json({ error: "invalid credentials" });
    return;
  }
  res.json({ token: signToken({ userId: row.id, username: row.username }), user: toPublicUser(row) });
});

authRouter.get("/me", requireAuth, async (req: AuthedRequest, res) => {
  const { rows } = await pool.query("SELECT * FROM users WHERE id = $1", [req.auth!.userId]);
  const row = rows[0] as UserRow | undefined;
  if (!row) {
    res.status(404).json({ error: "not found" });
    return;
  }
  res.json(toPublicUser(row));
});

authRouter.patch("/me", requireAuth, async (req: AuthedRequest, res) => {
  const { displayName, bio } = req.body ?? {};
  const { rows } = await pool.query("SELECT * FROM users WHERE id = $1", [req.auth!.userId]);
  const row = rows[0] as UserRow | undefined;
  if (!row) {
    res.status(404).json({ error: "not found" });
    return;
  }
  await pool.query("UPDATE users SET display_name = $1, bio = $2 WHERE id = $3", [
    typeof displayName === "string" ? displayName : row.display_name,
    typeof bio === "string" ? bio : row.bio,
    row.id,
  ]);
  const updated = await pool.query("SELECT * FROM users WHERE id = $1", [row.id]);
  res.json(toPublicUser(updated.rows[0] as UserRow));
});
