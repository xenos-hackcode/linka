import { Router } from "express";
import { v4 as uuid } from "uuid";
import { AuthedRequest, requireAuth } from "../auth";
import { pool } from "../db";
import { DmMessageRow } from "../types";
import { sendToUser } from "../ws";

export const dmsRouter = Router();
dmsRouter.use(requireAuth);

async function isBlockedEitherWay(userA: string, userB: string): Promise<boolean> {
  const { rows } = await pool.query(
    `SELECT 1 FROM blocks
     WHERE (blocker_id = $1 AND blocked_id = $2) OR (blocker_id = $2 AND blocked_id = $1)`,
    [userA, userB]
  );
  return rows.length > 0;
}

dmsRouter.get("/:userId/messages", async (req: AuthedRequest, res) => {
  const me = req.auth!.userId;
  const other = req.params.userId;
  const { rows } = await pool.query<DmMessageRow>(
    `SELECT * FROM dm_messages
     WHERE (sender_id = $1 AND recipient_id = $2) OR (sender_id = $2 AND recipient_id = $1)
     ORDER BY created_at ASC`,
    [me, other]
  );
  res.json(rows);
});

dmsRouter.post("/:userId/messages", async (req: AuthedRequest, res) => {
  const me = req.auth!.userId;
  const other = req.params.userId;
  if (other === me) {
    res.status(400).json({ error: "cannot message yourself" });
    return;
  }
  const { text } = req.body ?? {};
  if (typeof text !== "string" || !text.trim()) {
    res.status(400).json({ error: "text required" });
    return;
  }
  if (await isBlockedEitherWay(me, other)) {
    res.status(403).json({ error: "blocked" });
    return;
  }
  const id = uuid();
  const now = Date.now();
  await pool.query(
    "INSERT INTO dm_messages (id, sender_id, recipient_id, text, created_at) VALUES ($1, $2, $3, $4, $5)",
    [id, me, other, text.trim(), now]
  );
  const { rows } = await pool.query<DmMessageRow>("SELECT * FROM dm_messages WHERE id = $1", [id]);
  const row = rows[0];
  sendToUser(other, { type: "dm_message", message: row });
  res.status(201).json(row);
});
