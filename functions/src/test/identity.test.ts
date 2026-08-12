import assert from "assert";
import * as admin from "firebase-admin";
import { ACTIVE, allocateHumanUserId, assertNoClientDeletionTarget, ensureHumanIdentityForUid,
  IdentityError, isValidHumanUserId, trustedHumanIdForUid } from "../identity";

type Stored = Record<string, unknown>;
class MemoryFirestore {
  readonly documents = new Map<string, Stored>();
  private tail: Promise<void> = Promise.resolve();
  collection(name: string): any { return { doc: (id: string) => this.ref(`${name}/${id}`) }; }
  private ref(path: string): any { return {
    path,
    get: async () => { const data = this.documents.get(path); return { exists: data !== undefined, data: () => data }; },
    collection: (name: string) => ({ doc: (id: string) => this.ref(`${path}/${name}/${id}`) })
  }; }
  async runTransaction<T>(work: (transaction: any) => Promise<T>): Promise<T> {
    let release: () => void = () => undefined;
    const previous = this.tail;
    this.tail = new Promise<void>(resolve => { release = resolve; });
    await previous;
    const writes: Array<() => void> = [];
    const transaction = {
      get: async (ref: any) => { const data = this.documents.get(ref.path); return { exists: data !== undefined, data: () => data }; },
      create: (ref: any, data: Stored) => writes.push(() => { if (this.documents.has(ref.path)) throw new Error("exists"); this.documents.set(ref.path, data); }),
      set: (ref: any, data: Stored, options?: { merge?: boolean }) => writes.push(() => this.documents.set(ref.path,
        options?.merge ? { ...(this.documents.get(ref.path) ?? {}), ...data } : data))
    };
    try { const result = await work(transaction); writes.forEach(write => write()); return result; } finally { release(); }
  }
}
const asDb = (db: MemoryFirestore) => db as unknown as admin.firestore.Firestore;

describe("trusted Human identity contract", () => {
  it("rejects unauthenticated ensureHumanIdentity", async () => {
    await assert.rejects(ensureHumanIdentityForUid(asDb(new MemoryFirestore()), ""),
      (error: IdentityError) => error.code === "UNAUTHENTICATED");
  });
  it("allocates a valid server ID unrelated to Firebase UID", () => {
    const id = allocateHumanUserId(() => Buffer.from("00112233445566778899aabbccddeeff", "hex"));
    assert.strictEqual(id, "human_00112233445566778899aabbccddeeff");
    assert.strictEqual(isValidHumanUserId(id), true);
    assert.strictEqual(id.includes("firebase"), false);
  });
  it("first request creates agreeing forward and reverse schema-1 bindings", async () => {
    const db = new MemoryFirestore();
    const result = await ensureHumanIdentityForUid(asDb(db), "uid-a", null, () => Buffer.alloc(16, 1));
    assert.deepStrictEqual({ humanUserId: result.humanUserId, status: result.status, schemaVersion: result.schemaVersion },
      { humanUserId: "human_01010101010101010101010101010101", status: ACTIVE, schemaVersion: 1 });
    assert.strictEqual(db.documents.get("accounts/uid-a")?.humanUserId, result.humanUserId);
    assert.strictEqual(db.documents.get(`users/${result.humanUserId}`)?.ownerFirebaseUid, "uid-a");
  });
  it("repeated and concurrent requests are idempotent", async () => {
    const db = new MemoryFirestore();
    const [first, second] = await Promise.all([
      ensureHumanIdentityForUid(asDb(db), "uid-a", null, () => Buffer.alloc(16, 2)),
      ensureHumanIdentityForUid(asDb(db), "uid-a", null, () => Buffer.alloc(16, 3))]);
    assert.strictEqual(first.humanUserId, second.humanUserId);
    assert.strictEqual([...db.documents.keys()].filter(key => key.startsWith("users/")).length, 1);
  });
  it("two accounts cannot receive the same Human binding", async () => {
    const db = new MemoryFirestore();
    const same = () => Buffer.alloc(16, 4);
    await ensureHumanIdentityForUid(asDb(db), "uid-a", null, same);
    await assert.rejects(ensureHumanIdentityForUid(asDb(db), "uid-b", null, same),
      (error: IdentityError) => error.code === "HUMAN_ID_COLLISION");
    assert.strictEqual(db.documents.has("accounts/uid-b"), false);
  });
  it("conflicting existing bindings fail closed", async () => {
    const db = new MemoryFirestore();
    const id = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    db.documents.set("accounts/uid-a", { humanUserId: id, status: ACTIVE, schemaVersion: 1 });
    db.documents.set(`users/${id}`, { ownerFirebaseUid: "uid-b", status: ACTIVE, schemaVersion: 1 });
    await assert.rejects(ensureHumanIdentityForUid(asDb(db), "uid-a"),
      (error: IdentityError) => error.code === "BINDING_CONFLICT");
  });
  it("request payload cannot select a deletion Human ID", () => {
    assert.throws(() => assertNoClientDeletionTarget({ humanUserId: "human_aaaaaaaa" }),
      (error: IdentityError) => error.code === "CLIENT_DELETION_TARGET_FORBIDDEN");
    assert.doesNotThrow(() => assertNoClientDeletionTarget({}));
  });
  it("deletion authority derives the target from an authenticated binding", async () => {
    const db = new MemoryFirestore();
    const id = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    db.documents.set("accounts/uid-a", { humanUserId: id, status: ACTIVE, schemaVersion: 1 });
    db.documents.set(`users/${id}`, { ownerFirebaseUid: "uid-a", status: ACTIVE, schemaVersion: 1 });
    assert.strictEqual(await trustedHumanIdForUid(asDb(db), "uid-a"), id);
  });
  it("broken forward or reverse deletion authority is rejected", async () => {
    const id = "human_cccccccccccccccccccccccccccccccc";
    const missing = new MemoryFirestore();
    await assert.rejects(trustedHumanIdForUid(asDb(missing), "uid-a"));
    const conflict = new MemoryFirestore();
    conflict.documents.set("accounts/uid-a", { humanUserId: id, status: ACTIVE, schemaVersion: 1 });
    conflict.documents.set(`users/${id}`, { ownerFirebaseUid: "uid-b", status: ACTIVE, schemaVersion: 1 });
    await assert.rejects(trustedHumanIdForUid(asDb(conflict), "uid-a"),
      (error: IdentityError) => error.code === "BINDING_CONFLICT");
  });
  it("rejects malformed IDs", () => {
    assert.strictEqual(isValidHumanUserId("human_bad!"), false);
    assert.strictEqual(isValidHumanUserId("firebase-uid"), false);
  });
});
