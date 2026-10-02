import { NextFunction, Request, Response } from "express";
import jwt from "jsonwebtoken";

// Local-dev-only secret - fine while this only runs on localhost (see the current
// plan's "local only for now" scope); replace with a real secret from the
// environment before this is ever deployed anywhere reachable.
const JWT_SECRET = process.env.JWT_SECRET ?? "linka-dev-secret-change-before-deploying";

export interface AuthPayload {
  userId: string;
  username: string;
}

export function signToken(payload: AuthPayload): string {
  return jwt.sign(payload, JWT_SECRET, { expiresIn: "30d" });
}

export function verifyToken(token: string): AuthPayload | null {
  try {
    return jwt.verify(token, JWT_SECRET) as AuthPayload;
  } catch {
    return null;
  }
}

export interface AuthedRequest extends Request {
  auth?: AuthPayload;
}

/** Express middleware - rejects with 401 rather than letting a route run with a
 * null/undefined user, so every protected route can trust req.auth is present. */
export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  const header = req.header("authorization");
  const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;
  const payload = token ? verifyToken(token) : null;
  if (!payload) {
    res.status(401).json({ error: "unauthorized" });
    return;
  }
  req.auth = payload;
  next();
}
