import cors from "cors";
import express from "express";
import { createServer } from "http";
import { WebSocketServer } from "ws";
import { initSchema } from "./db";
import { authRouter } from "./routes/auth";
import { blocksRouter } from "./routes/blocks";
import { dmsRouter } from "./routes/dms";
import { globalRouter } from "./routes/global";
import { groupsRouter } from "./routes/groups";
import { requestsRouter } from "./routes/requests";
import { usersRouter } from "./routes/users";
import { attachWebSocketServer } from "./ws";

const app = express();
app.use(cors());
app.use(express.json());

app.get("/health", (_req, res) => res.json({ ok: true }));
app.use("/auth", authRouter);
app.use("/requests", requestsRouter);
app.use("/global", globalRouter);
app.use("/groups", groupsRouter);
app.use("/users", usersRouter);
app.use("/blocks", blocksRouter);
app.use("/dms", dmsRouter);

const server = createServer(app);
const wss = new WebSocketServer({ server, path: "/ws" });
attachWebSocketServer(wss);

const PORT = Number(process.env.PORT ?? 4000);
// Postgres needs the schema created before anything can query it - unlike
// the old synchronous SQLite open, this is a real async call, so listen()
// waits on it rather than racing the first request against table creation.
initSchema()
  .then(() => {
    server.listen(PORT, () => {
      console.log(`linka-server listening on http://localhost:${PORT} (ws at /ws)`);
    });
  })
  .catch((err) => {
    console.error("Failed to initialize database schema", err);
    process.exit(1);
  });
