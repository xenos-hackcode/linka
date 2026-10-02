import { Router } from "express";
import { v4 as uuid } from "uuid";
import { AuthedRequest, requireAuth } from "../auth";
import { pool } from "../db";
import { GlobalMessageRow } from "../types";
import { broadcastAll } from "../ws";

export const globalRouter = Router();
globalRouter.use(requireAuth);

globalRouter.get("/messages", async (req, res) => {
  const limit = Math.min(Number(req.query.limit ?? 100), 200);
  const { rows } = await pool.query<GlobalMessageRow>(
    `SELECT gm.*, u.username FROM global_messages gm
     JOIN users u ON u.id = gm.user_id
     ORDER BY gm.created_at DESC LIMIT $1`,
    [limit]
  );
  res.json(rows.reverse());
});

globalRouter.post("/messages", async (req: AuthedRequest, res) => {
  const { text } = req.body ?? {};
  if (typeof text !== "string" || !text.trim()) {
    res.status(400).json({ error: "text required" });
    return;
  }
  const id = uuid();
  const now = Date.now();
  await pool.query("INSERT INTO global_messages (id, user_id, text, created_at) VALUES ($1, $2, $3, $4)", [
    id,
    req.auth!.userId,
    text.trim(),
    now,
  ]);
  const { rows } = await pool.query<GlobalMessageRow>(
    `SELECT gm.*, u.username FROM global_messages gm
     JOIN users u ON u.id = gm.user_id
     WHERE gm.id = $1`,
    [id]
  );
  const row = rows[0];
  broadcastAll({ type: "global_message", message: row });
  res.status(201).json(row);
});
