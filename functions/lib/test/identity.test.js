"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const assert_1 = __importDefault(require("assert"));
const identity_1 = require("../identity");
class MemoryFirestore {
    constructor() {
        this.documents = new Map();
        this.tail = Promise.resolve();
    }
    collection(name) { return { doc: (id) => this.ref(`${name}/${id}`) }; }
    ref(path) {
        return {
            path,
            get: async () => { const data = this.documents.get(path); return { exists: data !== undefined, data: () => data }; },
            collection: (name) => ({ doc: (id) => this.ref(`${path}/${name}/${id}`) })
        };
    }
    async runTransaction(work) {
        let release = () => undefined;
        const previous = this.tail;
        this.tail = new Promise(resolve => { release = resolve; });
        await previous;
        const writes = [];
        const transaction = {
            get: async (ref) => { const data = this.documents.get(ref.path); return { exists: data !== undefined, data: () => data }; },
            create: (ref, data) => writes.push(() => { if (this.documents.has(ref.path))
                throw new Error("exists"); this.documents.set(ref.path, data); }),
            set: (ref, data, options) => writes.push(() => this.documents.set(ref.path, options?.merge ? { ...(this.documents.get(ref.path) ?? {}), ...data } : data))
        };
        try {
            const result = await work(transaction);
            writes.forEach(write => write());
            return result;
        }
        finally {
            release();
        }
    }
}
const asDb = (db) => db;
describe("trusted Human identity contract", () => {
    it("rejects unauthenticated ensureHumanIdentity", async () => {
        await assert_1.default.rejects((0, identity_1.ensureHumanIdentityForUid)(asDb(new MemoryFirestore()), ""), (error) => error.code === "UNAUTHENTICATED");
    });
    it("allocates a valid server ID unrelated to Firebase UID", () => {
        const id = (0, identity_1.allocateHumanUserId)(() => Buffer.from("00112233445566778899aabbccddeeff", "hex"));
        assert_1.default.strictEqual(id, "human_00112233445566778899aabbccddeeff");
        assert_1.default.strictEqual((0, identity_1.isValidHumanUserId)(id), true);
        assert_1.default.strictEqual(id.includes("firebase"), false);
    });
    it("first request creates agreeing forward and reverse schema-1 bindings", async () => {
        const db = new MemoryFirestore();
        const result = await (0, identity_1.ensureHumanIdentityForUid)(asDb(db), "uid-a", null, () => Buffer.alloc(16, 1));
        assert_1.default.deepStrictEqual({ humanUserId: result.humanUserId, status: result.status, schemaVersion: result.schemaVersion }, { humanUserId: "human_01010101010101010101010101010101", status: identity_1.ACTIVE, schemaVersion: 1 });
        assert_1.default.strictEqual(db.documents.get("accounts/uid-a")?.humanUserId, result.humanUserId);
        assert_1.default.strictEqual(db.documents.get(`users/${result.humanUserId}`)?.ownerFirebaseUid, "uid-a");
    });
    it("repeated and concurrent requests are idempotent", async () => {
        const db = new MemoryFirestore();
        const [first, second] = await Promise.all([
            (0, identity_1.ensureHumanIdentityForUid)(asDb(db), "uid-a", null, () => Buffer.alloc(16, 2)),
            (0, identity_1.ensureHumanIdentityForUid)(asDb(db), "uid-a", null, () => Buffer.alloc(16, 3))
        ]);
        assert_1.default.strictEqual(first.humanUserId, second.humanUserId);
        assert_1.default.strictEqual([...db.documents.keys()].filter(key => key.startsWith("users/")).length, 1);
    });
    it("two accounts cannot receive the same Human binding", async () => {
        const db = new MemoryFirestore();
        const same = () => Buffer.alloc(16, 4);
        await (0, identity_1.ensureHumanIdentityForUid)(asDb(db), "uid-a", null, same);
        await assert_1.default.rejects((0, identity_1.ensureHumanIdentityForUid)(asDb(db), "uid-b", null, same), (error) => error.code === "HUMAN_ID_COLLISION");
        assert_1.default.strictEqual(db.documents.has("accounts/uid-b"), false);
    });
    it("conflicting existing bindings fail closed", async () => {
        const db = new MemoryFirestore();
        const id = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        db.documents.set("accounts/uid-a", { humanUserId: id, status: identity_1.ACTIVE, schemaVersion: 1 });
        db.documents.set(`users/${id}`, { ownerFirebaseUid: "uid-b", status: identity_1.ACTIVE, schemaVersion: 1 });
        await assert_1.default.rejects((0, identity_1.ensureHumanIdentityForUid)(asDb(db), "uid-a"), (error) => error.code === "BINDING_CONFLICT");
    });
    it("request payload cannot select a deletion Human ID", () => {
        assert_1.default.throws(() => (0, identity_1.assertNoClientDeletionTarget)({ humanUserId: "human_aaaaaaaa" }), (error) => error.code === "CLIENT_DELETION_TARGET_FORBIDDEN");
        assert_1.default.doesNotThrow(() => (0, identity_1.assertNoClientDeletionTarget)({}));
    });
    it("deletion authority derives the target from an authenticated binding", async () => {
        const db = new MemoryFirestore();
        const id = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        db.documents.set("accounts/uid-a", { humanUserId: id, status: identity_1.ACTIVE, schemaVersion: 1 });
        db.documents.set(`users/${id}`, { ownerFirebaseUid: "uid-a", status: identity_1.ACTIVE, schemaVersion: 1 });
        assert_1.default.strictEqual(await (0, identity_1.trustedHumanIdForUid)(asDb(db), "uid-a"), id);
    });
    it("broken forward or reverse deletion authority is rejected", async () => {
        const id = "human_cccccccccccccccccccccccccccccccc";
        const missing = new MemoryFirestore();
        await assert_1.default.rejects((0, identity_1.trustedHumanIdForUid)(asDb(missing), "uid-a"));
        const conflict = new MemoryFirestore();
        conflict.documents.set("accounts/uid-a", { humanUserId: id, status: identity_1.ACTIVE, schemaVersion: 1 });
        conflict.documents.set(`users/${id}`, { ownerFirebaseUid: "uid-b", status: identity_1.ACTIVE, schemaVersion: 1 });
        await assert_1.default.rejects((0, identity_1.trustedHumanIdForUid)(asDb(conflict), "uid-a"), (error) => error.code === "BINDING_CONFLICT");
    });
    it("rejects malformed IDs", () => {
        assert_1.default.strictEqual((0, identity_1.isValidHumanUserId)("human_bad!"), false);
        assert_1.default.strictEqual((0, identity_1.isValidHumanUserId)("firebase-uid"), false);
    });
});
//# sourceMappingURL=identity.test.js.map