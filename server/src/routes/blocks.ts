import { Router } from "express";
import { AuthedRequest, requireAuth } from "../auth";
import { pool } from "../db";

export const blocksRouter = Router();
blocksRouter.use(requireAuth);

/** Who the caller has blocked - just the id list, the client already has profiles for
 * anyone it would ask about (see users.ts) so this stays minimal. */
blocksRouter.get("/", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<{ blocked_id: string }>(
    "SELECT blocked_id FROM blocks WHERE blocker_id = $1",
    [req.auth!.userId]
  );
  res.json(rows.map((r) => r.blocked_id));
});

blocksRouter.post("/:userId", async (req: AuthedRequest, res) => {
  if (req.params.userId === req.auth!.userId) {
    res.status(400).json({ error: "cannot block yourself" });
    return;
  }
  await pool.query(
    "INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING",
    [req.auth!.userId, req.params.userId, Date.now()]
  );
  res.status(201).json({ blocked: req.params.userId });
});

blocksRouter.delete("/:userId", async (req: AuthedRequest, res) => {
  await pool.query("DELETE FROM blocks WHERE blocker_id = $1 AND blocked_id = $2", [
    req.auth!.userId,
    req.params.userId,
  ]);
  res.status(204).send();
});
