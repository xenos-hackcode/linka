import { Router } from "express";
import { v4 as uuid } from "uuid";
import { AuthedRequest, requireAuth } from "../auth";
import { pool } from "../db";
import { RequestRow } from "../types";
import { broadcastAll, sendToUser } from "../ws";

export const requestsRouter = Router();
requestsRouter.use(requireAuth);

/** Everyone else's pending requests - a requester can't accept/deny their own (see
 * PLANNING.md's domain marketplace), so this excludes the caller's own posts. */
requestsRouter.get("/", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<RequestRow>(
    "SELECT * FROM requests WHERE status = 'PENDING' AND requester_id != $1 ORDER BY created_at DESC",
    [req.auth!.userId]
  );
  res.json(rows);
});

requestsRouter.get("/mine", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<RequestRow>(
    "SELECT * FROM requests WHERE requester_id = $1 ORDER BY created_at DESC",
    [req.auth!.userId]
  );
  res.json(rows);
});

requestsRouter.get("/accepted", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<RequestRow>(
    "SELECT * FROM requests WHERE responder_id = $1 AND status = 'ACCEPTED' ORDER BY updated_at DESC",
    [req.auth!.userId]
  );
  res.json(rows);
});

requestsRouter.post("/", async (req: AuthedRequest, res) => {
  const { summary, reason } = req.body ?? {};
  if (typeof summary !== "string" || !summary.trim() || typeof reason !== "string" || !reason.trim()) {
    res.status(400).json({ error: "summary and reason required" });
    return;
  }
  const id = uuid();
  const now = Date.now();
  await pool.query(
    "INSERT INTO requests (id, requester_id, summary, reason, status, created_at, updated_at) VALUES ($1, $2, $3, $4, 'PENDING', $5, $6)",
    [id, req.auth!.userId, summary.trim(), reason.trim(), now, now]
  );
  const { rows } = await pool.query<RequestRow>("SELECT * FROM requests WHERE id = $1", [id]);
  const row = rows[0];
  broadcastAll({ type: "request_created", request: row });
  res.status(201).json(row);
});

async function respond(id: string, userId: string, status: "ACCEPTED" | "DENIED") {
  const { rows } = await pool.query<RequestRow>("SELECT * FROM requests WHERE id = $1", [id]);
  const row = rows[0];
  if (!row) return { error: 404 as const };
  if (row.status !== "PENDING") return { error: 409 as const };
  // A requester cannot accept/deny their own request - only respond to others' (see
  // the /respond routes below, and PLANNING.md's requester-vs-responder split).
  if (row.requester_id === userId) return { error: 403 as const };
  await pool.query("UPDATE requests SET status = $1, responder_id = $2, updated_at = $3 WHERE id = $4", [
    status,
    userId,
    Date.now(),
    id,
  ]);
  const updatedResult = await pool.query<RequestRow>("SELECT * FROM requests WHERE id = $1", [id]);
  const updated = updatedResult.rows[0];
  sendToUser(row.requester_id, { type: "request_updated", request: updated });
  return { row: updated };
}

requestsRouter.post("/:id/accept", async (req: AuthedRequest, res) => {
  const result = await respond(req.params.id, req.auth!.userId, "ACCEPTED");
  if (result.error) {
    res.status(result.error).json({ error: "cannot accept" });
    return;
  }
  res.json(result.row);
});

requestsRouter.post("/:id/deny", async (req: AuthedRequest, res) => {
  const result = await respond(req.params.id, req.auth!.userId, "DENIED");
  if (result.error) {
    res.status(result.error).json({ error: "cannot deny" });
    return;
  }
  res.json(result.row);
});

requestsRouter.post("/:id/cancel", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<RequestRow>("SELECT * FROM requests WHERE id = $1", [req.params.id]);
  const row = rows[0];
  if (!row) {
    res.status(404).json({ error: "not found" });
    return;
  }
  if (row.requester_id !== req.auth!.userId) {
    res.status(403).json({ error: "only the requester can cancel" });
    return;
  }
  if (row.status !== "PENDING") {
    res.status(409).json({ error: "only a pending request can be canceled" });
    return;
  }
  await pool.query("UPDATE requests SET status = 'CANCELED', updated_at = $1 WHERE id = $2", [Date.now(), row.id]);
  const updatedResult = await pool.query<RequestRow>("SELECT * FROM requests WHERE id = $1", [row.id]);
  res.json(updatedResult.rows[0]);
});
