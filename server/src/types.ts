export interface UserRow {
  id: string;
  username: string;
  password_hash: string;
  display_name: string;
  bio: string;
  created_at: number;
}

export interface PublicUser {
  id: string;
  username: string;
  displayName: string;
  bio: string;
}

export function toPublicUser(row: UserRow): PublicUser {
  return { id: row.id, username: row.username, displayName: row.display_name, bio: row.bio };
}

export interface RequestRow {
  id: string;
  requester_id: string;
  summary: string;
  reason: string;
  status: "PENDING" | "ACCEPTED" | "DENIED" | "CANCELED";
  responder_id: string | null;
  created_at: number;
  updated_at: number;
}

export interface GlobalMessageRow {
  id: string;
  user_id: string;
  username: string;
  text: string;
  created_at: number;
}

export interface GroupRow {
  id: string;
  name: string;
  owner_id: string;
  created_at: number;
}

export interface GroupMessageRow {
  id: string;
  group_id: string;
  user_id: string;
  username: string;
  text: string;
  created_at: number;
}

export interface DmMessageRow {
  id: string;
  sender_id: string;
  recipient_id: string;
  text: string;
  created_at: number;
}
