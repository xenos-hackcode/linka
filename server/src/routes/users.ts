import { Router } from "express";
import { requireAuth } from "../auth";
import { pool } from "../db";
import { toPublicUser, UserRow } from "../types";

export const usersRouter = Router();
usersRouter.use(requireAuth);

/** Looked up when a chat message's sender is tapped (see the client's ChatScreen) -
 * returns the same public shape /auth/me does, just for someone else's id. */
usersRouter.get("/:id", async (req, res) => {
  const { rows } = await pool.query<UserRow>("SELECT * FROM users WHERE id = $1", [req.params.id]);
  const row = rows[0];
  if (!row) {
    res.status(404).json({ error: "not found" });
    return;
  }
  res.json(toPublicUser(row));
});
