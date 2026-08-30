import { SignJWT, jwtVerify } from "jose";
import { panelSecret } from "./config";

function key() {
  return new TextEncoder().encode(panelSecret());
}

export type AgentTok = {
  kind: "agent";
  tgId: number;
  hwid: string;
};

export type OpTok = {
  kind: "op";
  tgId: number;
  hwid: string;
};

export async function mintAgentTok(tgId: number, hwid: string) {
  return new SignJWT({ kind: "agent", tgId, hwid })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime("30d")
    .sign(key());
}

export async function mintOpTok(tgId: number, hwid: string) {
  return new SignJWT({ kind: "op", tgId, hwid })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime("12h")
    .sign(key());
}

export async function readTok(raw: string): Promise<AgentTok | OpTok | null> {
  try {
    const { payload } = await jwtVerify(raw, key());
    if (
      (payload.kind === "agent" || payload.kind === "op") &&
      typeof payload.tgId === "number" &&
      typeof payload.hwid === "string"
    ) {
      return { kind: payload.kind, tgId: payload.tgId, hwid: payload.hwid };
    }
    return null;
  } catch {
    return null;
  }
}
