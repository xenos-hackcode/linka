import { IncomingMessage } from "http";
import { WebSocket, WebSocketServer } from "ws";
import { verifyToken } from "./auth";
import { pool } from "./db";

interface Connection {
  socket: WebSocket;
  userId: string;
}

const connections = new Set<Connection>();

export function attachWebSocketServer(wss: WebSocketServer) {
  wss.on("connection", (socket: WebSocket, req: IncomingMessage) => {
    const url = new URL(req.url ?? "", "http://localhost");
    const token = url.searchParams.get("token");
    const payload = token ? verifyToken(token) : null;
    if (!payload) {
      socket.close(4001, "unauthorized");
      return;
    }
    const conn: Connection = { socket, userId: payload.userId };
    connections.add(conn);
    socket.on("close", () => connections.delete(conn));
  });
}

function send(conn: Connection, event: unknown) {
  if (conn.socket.readyState === WebSocket.OPEN) {
    conn.socket.send(JSON.stringify(event));
  }
}

/** Pushes to every connected user - global chat and marketplace-wide events
 * (a new request being posted) are visible to everyone. */
export function broadcastAll(event: unknown) {
  for (const conn of connections) send(conn, event);
}

/** Pushes only to one user - used for events specific to them (their request got
 * accepted/denied, someone messaged a group they're in). */
export function sendToUser(userId: string, event: unknown) {
  for (const conn of connections) {
    if (conn.userId === userId) send(conn, event);
  }
}

/** Pushes to every current member of a group - looked up fresh each call rather
 * than cached, so membership changes take effect immediately. */
export async function broadcastToGroup(groupId: string, event: unknown): Promise<void> {
  const { rows } = await pool.query<{ user_id: string }>(
    "SELECT user_id FROM group_members WHERE group_id = $1",
    [groupId]
  );
  const memberIdSet = new Set(rows.map((r) => r.user_id));
  for (const conn of connections) {
    if (memberIdSet.has(conn.userId)) send(conn, event);
  }
}
