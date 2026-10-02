import { Router } from "express";
import { v4 as uuid } from "uuid";
import { AuthedRequest, requireAuth } from "../auth";
import { pool } from "../db";
import { GroupMessageRow, GroupRow } from "../types";
import { broadcastToGroup } from "../ws";

export const groupsRouter = Router();
groupsRouter.use(requireAuth);

async function isMember(groupId: string, userId: string): Promise<boolean> {
  const { rows } = await pool.query("SELECT 1 FROM group_members WHERE group_id = $1 AND user_id = $2", [
    groupId,
    userId,
  ]);
  return rows.length > 0;
}

groupsRouter.get("/", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<GroupRow>(
    `SELECT g.* FROM groups g
     JOIN group_members m ON m.group_id = g.id
     WHERE m.user_id = $1
     ORDER BY g.created_at DESC`,
    [req.auth!.userId]
  );
  res.json(rows);
});

/** Groups the caller hasn't joined yet - without this, there's no way to find a group
 * that already exists other than already knowing its id, which makes group chat
 * untestable with more than one account. */
groupsRouter.get("/discover", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<GroupRow>(
    `SELECT g.* FROM groups g
     WHERE g.id NOT IN (SELECT group_id FROM group_members WHERE user_id = $1)
     ORDER BY g.created_at DESC`,
    [req.auth!.userId]
  );
  res.json(rows);
});

/** withUserId (optional) - lets a profile's "CREATE GROUP" button start a group with
 * both people already members, instead of the creator having to share the group id
 * separately for the other person to discover and join. */
groupsRouter.post("/", async (req: AuthedRequest, res) => {
  const { name, withUserId } = req.body ?? {};
  if (typeof name !== "string" || !name.trim()) {
    res.status(400).json({ error: "name required" });
    return;
  }
  const id = uuid();
  const now = Date.now();
  await pool.query("INSERT INTO groups (id, name, owner_id, created_at) VALUES ($1, $2, $3, $4)", [
    id,
    name.trim(),
    req.auth!.userId,
    now,
  ]);
  await pool.query("INSERT INTO group_members (group_id, user_id, joined_at) VALUES ($1, $2, $3)", [
    id,
    req.auth!.userId,
    now,
  ]);
  if (typeof withUserId === "string" && withUserId !== req.auth!.userId) {
    const exists = await pool.query("SELECT 1 FROM users WHERE id = $1", [withUserId]);
    if (exists.rows.length > 0) {
      await pool.query(
        "INSERT INTO group_members (group_id, user_id, joined_at) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING",
        [id, withUserId, now]
      );
    }
  }
  const { rows } = await pool.query<GroupRow>("SELECT * FROM groups WHERE id = $1", [id]);
  res.status(201).json(rows[0]);
});

groupsRouter.post("/:id/join", async (req: AuthedRequest, res) => {
  const { rows } = await pool.query<GroupRow>("SELECT * FROM groups WHERE id = $1", [req.params.id]);
  const group = rows[0];
  if (!group) {
    res.status(404).json({ error: "not found" });
    return;
  }
  if (await isMember(group.id, req.auth!.userId)) {
    res.json(group);
    return;
  }
  await pool.query("INSERT INTO group_members (group_id, user_id, joined_at) VALUES ($1, $2, $3)", [
    group.id,
    req.auth!.userId,
    Date.now(),
  ]);
  res.json(group);
});

groupsRouter.get("/:id/messages", async (req: AuthedRequest, res) => {
  if (!(await isMember(req.params.id, req.auth!.userId))) {
    res.status(403).json({ error: "not a member" });
    return;
  }
  const limit = Math.min(Number(req.query.limit ?? 100), 200);
  const { rows } = await pool.query<GroupMessageRow>(
    `SELECT gm.*, u.username FROM group_messages gm
     JOIN users u ON u.id = gm.user_id
     WHERE gm.group_id = $1
     ORDER BY gm.created_at DESC LIMIT $2`,
    [req.params.id, limit]
  );
  res.json(rows.reverse());
});

groupsRouter.post("/:id/messages", async (req: AuthedRequest, res) => {
  if (!(await isMember(req.params.id, req.auth!.userId))) {
    res.status(403).json({ error: "not a member" });
    return;
  }
  const { text } = req.body ?? {};
  if (typeof text !== "string" || !text.trim()) {
    res.status(400).json({ error: "text required" });
    return;
  }
  const id = uuid();
  const now = Date.now();
  await pool.query(
    "INSERT INTO group_messages (id, group_id, user_id, text, created_at) VALUES ($1, $2, $3, $4, $5)",
    [id, req.params.id, req.auth!.userId, text.trim(), now]
  );
  const { rows } = await pool.query<GroupMessageRow>(
    `SELECT gm.*, u.username FROM group_messages gm
     JOIN users u ON u.id = gm.user_id
     WHERE gm.id = $1`,
    [id]
  );
  const row = rows[0];
  await broadcastToGroup(req.params.id, { type: "group_message", message: row });
  res.status(201).json(row);
});
