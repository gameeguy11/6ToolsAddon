import { DurableObject } from "cloudflare:workers";

const MAX_LENGTH = 256;
const BURST = 5;
const KEY_RE = /^[A-Za-z0-9+\/=]{100,600}$/;
const ENC_RE = /^[A-Za-z0-9+\/=.:_-]{1,1800}$/;

function clean(value) {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f\u00a7]/g, "").trim().slice(0, MAX_LENGTH);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== "/ws") {
      return new Response("not found", { status: 404 });
    }
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("expected websocket", { status: 426 });
    }
    const stub = env.ROOM.get(env.ROOM.idFromName("main"));
    return stub.fetch(request);
  },
};

export class ChatRoom extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair('{"t":"ping"}', '{"t":"pong"}'));
  }

  async fetch() {
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    const nonce = crypto.randomUUID().replaceAll("-", "");
    server.serializeAttachment({ nonce, authed: false, name: null, uuid: null, tokens: BURST, last: Date.now() });
    server.send(JSON.stringify({ t: "challenge", nonce }));
    return new Response(null, { status: 101, webSocket: client });
  }

  members(exclude) {
    const result = [];
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === exclude) continue;
      const att = ws.deserializeAttachment();
      if (att && att.authed) result.push({ ws, att });
    }
    return result;
  }

  names(exclude) {
    return [...new Set(this.members(exclude).map((m) => m.att.name))];
  }

  keys(exclude) {
    const result = {};
    for (const m of this.members(exclude)) if (m.att.key) result[m.att.name.toLowerCase()] = m.att.key;
    return result;
  }

  broadcastPresence(exclude) {
    const payload = JSON.stringify({ t: "presence", online: this.names(exclude), keys: this.keys(exclude) });
    for (const m of this.members(exclude)) m.ws.send(payload);
  }

  list(value) {
    return String(value || "")
        .split(",")
        .map((s) => s.trim().toLowerCase().replaceAll("-", ""))
        .filter(Boolean);
  }

  isAdmin(uuid) {
    return Boolean(uuid) && this.list(this.env.ADMINS).includes(String(uuid).toLowerCase());
  }

  info(ws, msg) {
    ws.send(JSON.stringify({ t: "info", msg }));
  }

  async resolve(name) {
    for (const m of this.members()) {
      if (m.att.name.toLowerCase() === name.toLowerCase()) return String(m.att.uuid).toLowerCase();
    }
    try {
      const res = await fetch(`https://api.minecraftservices.com/minecraft/profile/lookup/name/${encodeURIComponent(name)}`);
      if (res.status !== 200) return null;
      return String((await res.json()).id).toLowerCase();
    } catch {
      return null;
    }
  }

  async admin(ws, att, data) {
    if (!this.isAdmin(att.uuid)) {
      this.fail(ws, "Not allowed.");
      return;
    }
    const bans = (await this.ctx.storage.get("bans")) || {};
    if (data.op === "bans") {
      this.info(ws, Object.values(bans).join(", ") || "No bans.");
      return;
    }
    const name = String(data.name ?? "");
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) {
      this.fail(ws, "Bad name.");
      return;
    }
    const uuid = await this.resolve(name);
    if (!uuid) {
      this.fail(ws, "Unknown player.");
      return;
    }
    if (this.isAdmin(uuid)) {
      this.fail(ws, "Cannot ban an admin.");
      return;
    }
    if (data.op === "ban") {
      bans[uuid] = name;
      await this.ctx.storage.put("bans", bans);
      for (const m of this.members()) {
        if (m.att.uuid === uuid) m.ws.close(1008, "banned");
      }
      this.info(ws, `Banned ${name}.`);
    } else if (data.op === "unban") {
      delete bans[uuid];
      await this.ctx.storage.put("bans", bans);
      this.info(ws, `Unbanned ${name}.`);
    }
  }

  fail(ws, msg) {
    ws.send(JSON.stringify({ t: "error", msg }));
  }

  async authenticate(ws, att, data) {
    if (att.authed || !att.nonce) return;
    const name = String(data.name ?? "");
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) {
      ws.close(1008, "bad name");
      return;
    }
    let profile;
    try {
      const res = await fetch(
          `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${att.nonce}`
      );
      if (res.status === 200) {
        profile = await res.json();
      } else if (res.status === 204 || res.status === 403) {
        // not a premium/verified session: accept as unverified
        profile = { id: "offline:" + name.toLowerCase(), name, offline: true };
      } else {
        ws.close(1011, "auth unavailable");
        return;
      }
    } catch {
      ws.close(1011, "auth unavailable");
      return;
    }
    const uuid = String(profile.id).toLowerCase();
    const isOffline = Boolean(profile.offline);
    if (isOffline && this.members(ws).some((m) => !String(m.att.uuid).startsWith("offline:") && String(m.att.name).toLowerCase() === name.toLowerCase())) {
      ws.close(1008, "name in use by a verified player");
      return;
    }
    const bans = (await this.ctx.storage.get("bans")) || {};
    if (bans[uuid]) {
      ws.close(1008, "banned");
      return;
    }
    for (const m of this.members(ws)) {
      if (m.att.uuid === profile.id || (!isOffline && String(m.att.uuid).startsWith("offline:") && String(m.att.name).toLowerCase() === name.toLowerCase())) m.ws.close(1000, "replaced");
    }
    const key = typeof data.key === "string" && KEY_RE.test(data.key) ? data.key : null;
    ws.serializeAttachment({ nonce: null, authed: true, name: profile.name, uuid: profile.id, key, tokens: BURST, last: Date.now() });
    ws.send(JSON.stringify({ t: "ready", name: profile.name, online: this.names(), keys: this.keys() }));
    this.broadcastPresence();
  }

  async webSocketMessage(ws, raw) {
    if (typeof raw !== "string" || raw.length > 2048) return;
    let data;
    try {
      data = JSON.parse(raw);
    } catch {
      return;
    }
    const att = ws.deserializeAttachment();
    if (!att) return;
    if (data.t === "auth") {
      await this.authenticate(ws, att, data);
      return;
    }
    if (!att.authed) return;
    if (data.t === "admin") {
      await this.admin(ws, att, data);
      return;
    }
    if (data.t === "who") {
      ws.send(JSON.stringify({ t: "presence", online: this.names(), keys: this.keys() }));
      return;
    }
    if (data.t !== "msg") return;

    const now = Date.now();
    const tokens = Math.min(BURST, att.tokens + (now - att.last) / 1000);
    if (tokens < 1) {
      ws.serializeAttachment({ ...att, tokens, last: now });
      this.fail(ws, "Slow down.");
      return;
    }
    const isDm = data.ch === "dm";
    const enc = isDm ? String(data.enc ?? "") : "";
    if (isDm && !ENC_RE.test(enc)) return;
    const text = isDm ? enc : clean(data.text);
    const dedupe = isDm ? enc.slice(0, 32) : text.toLowerCase();
    if (!text) return;

    const key = `${data.ch}|${String(data.to ?? "").toLowerCase()}|${dedupe}`;
    if (att.lastKey === key && now - att.lastKeyAt < 5000) {
      ws.serializeAttachment({ ...att, tokens, last: now });
      this.fail(ws, "Don't repeat the same message within 5 seconds.");
      return;
    }
    ws.serializeAttachment({ ...att, tokens: tokens - 1, last: now, lastKey: key, lastKeyAt: now });

    if (data.ch === "general") {
      const payload = JSON.stringify({ t: "msg", ch: "general", from: att.name, text, ts: now });
      for (const m of this.members()) m.ws.send(payload);
      return;
    }

    if (data.ch === "dm") {
      const to = String(data.to ?? "").toLowerCase();
      const targets = this.members().filter((m) => m.att.name.toLowerCase() === to);
      if (targets.length === 0) {
        this.fail(ws, "That player is offline or not using 6ToolsChat.");
        return;
      }
      const payload = JSON.stringify({ t: "msg", ch: "dm", from: att.name, to: targets[0].att.name, enc, ts: now });
      const recipients = new Set(targets.map((m) => m.ws));
      for (const m of this.members()) {
        if (m.att.uuid === att.uuid) recipients.add(m.ws);
      }
      for (const target of recipients) target.send(payload);
    }
  }

  async webSocketClose(ws, code, reason) {
    try {
      ws.close(code, reason);
    } catch {}
    this.broadcastPresence(ws);
  }

  async webSocketError(ws) {
    this.broadcastPresence(ws);
  }
}