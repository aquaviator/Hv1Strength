"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const assert_1 = __importDefault(require("assert"));
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
const rules_unit_testing_1 = require("@firebase/rules-unit-testing");
const firestore_1 = require("firebase/firestore");
const PROJECT_ID = "demo-hv1-strength-local";
const H1 = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
const H2 = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
const COLLECTIONS = ["profile", "weight", "tape", "customExercises", "templates",
    "templateExercises", "templateSets", "sessions", "loggedSets", "processedCommands", "trainingPlans", "plannedWorkouts"];
const publishedVersion = (humanUserId, globalId, contentType) => ({
    schemaVersion: `humanv1.${contentType}/1`, globalId, humanUserId, revision: 1,
    publicationState: "PUBLISHED", tombstoneState: "ACTIVE", sourceDraftId: globalId,
    payload: contentType === "plan" ? { weeks: [{ placements: [{ workoutVersionId: "workout-1_r1_aaaaaaaaaaaa" }] }] } : {},
    createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z",
    publishedAt: "2026-01-01T00:00:00.000Z", contentChecksum: "a".repeat(64),
    versionId: `${globalId}_r1_aaaaaaaaaaaa`, contentType, compatibleTags: []
});
describe("Strength Firestore trusted identity rules", function () {
    this.timeout(30_000);
    let env;
    before(async () => {
        const rules = fs.readFileSync(path.resolve(__dirname, "../../../firestore.rules"), "utf8");
        env = await (0, rules_unit_testing_1.initializeTestEnvironment)({ projectId: PROJECT_ID, firestore: { rules } });
    });
    after(async () => env.cleanup());
    beforeEach(async () => {
        await env.clearFirestore();
        await env.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "accounts/uid-a"), { humanUserId: H1, status: "ACTIVE", schemaVersion: 1 });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}`), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "accounts/uid-b"), { humanUserId: H2, status: "ACTIVE", schemaVersion: 1 });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H2}`), { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "accounts/uid-a/entitlements/current"), {
                schemaVersion: 1, firebaseUid: "uid-a", humanUserId: H1,
                normalizedState: "ACTIVE_UNTIL_EXPIRY", productScope: "WORKOUT_STUDIO",
                expiryAt: new Date("2099-01-01T00:00:00.000Z")
            });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}/profile/main`), { value: "a" });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H2}/profile/main`), { value: "b" });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "exercise_catalogue/current"), { releaseId: "published-1", status: "published", channel: "production" });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/published-1"), { releaseId: "published-1", status: "published", channel: "production" });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/published-1/exercises/bench_press"), { exerciseId: "bench_press" });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/draft-1"), { releaseId: "draft-1", status: "draft", channel: "production" });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/draft-1/exercises/secret"), { exerciseId: "secret" });
            await (0, firestore_1.setDoc)((0, firestore_1.doc)(db, "staging_exercises/candidate"), { name: "Unreviewed" });
        });
    });
    it("denies unauthenticated access", async () => {
        const db = env.unauthenticatedContext().firestore();
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, `users/${H1}/profile/main`)));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, "accounts/uid-a")));
    });
    it("allows A to access H1 and B to access H2", async () => {
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-b").firestore(), `users/${H2}/profile/main`)));
    });
    it("denies A to H2 and B to H1", async () => {
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-a").firestore(), `users/${H2}/profile/main`)));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-b").firestore(), `users/${H1}/profile/main`)));
    });
    it("denies missing forward binding", async () => {
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-missing").firestore(), `users/${H1}/profile/main`)));
    });
    it("denies inactive, missing-schema, and unsupported-schema forward bindings", async () => {
        for (const binding of [
            { humanUserId: H1, status: "DISABLED", schemaVersion: 1 },
            { humanUserId: H1, status: "ACTIVE" },
            { humanUserId: H1, status: "ACTIVE", schemaVersion: 2 }
        ]) {
            await env.withSecurityRulesDisabled(async (c) => (0, firestore_1.setDoc)((0, firestore_1.doc)(c.firestore(), "accounts/uid-a"), binding));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
        }
    });
    it("denies a mismatched forward Human ID", async () => {
        await env.withSecurityRulesDisabled(async (c) => (0, firestore_1.setDoc)((0, firestore_1.doc)(c.firestore(), "accounts/uid-a"), { humanUserId: H2, status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
    });
    it("denies missing, wrong-owner, inactive, and unsupported reverse roots", async () => {
        const variants = [null,
            { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 },
            { ownerFirebaseUid: "uid-a", status: "DISABLED", schemaVersion: 1 },
            { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 2 }];
        for (const root of variants) {
            await env.withSecurityRulesDisabled(async (c) => root === null ? (0, firestore_1.deleteDoc)((0, firestore_1.doc)(c.firestore(), `users/${H1}`)) : (0, firestore_1.setDoc)((0, firestore_1.doc)(c.firestore(), `users/${H1}`), root));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
        }
    });
    it("denies legacy IDs without an approved binding and Firebase UID substitution", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, "users/human_2080278062xx/sessions/x"), { value: true }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, "users/uid-a/sessions/x"), { value: true }));
    });
    it("prevents clients from creating, changing, or deleting account bindings", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, "accounts/uid-a"), { humanUserId: H2, status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, "accounts/new"), { humanUserId: H1, status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.deleteDoc)((0, firestore_1.doc)(db, "accounts/uid-a")));
    });
    it("prevents ownership mutation and arbitrary Human-root claims", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}`), { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, "users/human_cccccccccccccccccccccccccccccccc"), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
    });
    it("allows the owner for every Strength synchronized collection", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        for (const collection of COLLECTIONS) {
            const data = collection === "trainingPlans" || collection === "plannedWorkouts"
                ? { value: collection, humanUserId: H1 } : { value: collection };
            await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}/${collection}/test`), data));
            await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, `users/${H1}/${collection}/test`)));
        }
        assert_1.default.strictEqual(COLLECTIONS.length, 12);
    });
    it("denies planner ownership forgery on create and update", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        for (const collection of ["trainingPlans", "plannedWorkouts"]) {
            const ref = (0, firestore_1.doc)(db, `users/${H1}/${collection}/test`);
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)(ref, { humanUserId: H2 }));
            await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)(ref, { humanUserId: H1 }));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)(ref, { humanUserId: H2 }));
        }
    });
    it("allows only immutable-owner schema-14 measurement records with monotonic revisions", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        const ref = (0, firestore_1.doc)(db, `users/${H1}/measurementRecords/measurement-1`);
        const valid = { schemaVersion: 14, globalId: "measurement-1", humanUserId: H1,
            loggedSetGlobalId: "set-1", metricKey: "power", revision: 1 };
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)(ref, valid));
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)(ref, { ...valid, revision: 2 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)(ref, { ...valid, revision: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)(ref, { ...valid, revision: 3, humanUserId: H2 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)(ref, { ...valid, revision: 3, loggedSetGlobalId: "set-2" }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}/measurementRecords/wrong-id`), valid));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.deleteDoc)(ref));
    });
    it("denies foreign and malformed measurement records", async () => {
        const ownerDb = env.authenticatedContext("uid-a").firestore();
        const foreignDb = env.authenticatedContext("uid-b").firestore();
        const path = `users/${H1}/measurementRecords/measurement-1`;
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(ownerDb, path), { schemaVersion: 13, globalId: "measurement-1", humanUserId: H1,
            loggedSetGlobalId: "set-1", metricKey: "power", revision: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(foreignDb, path), { schemaVersion: 14, globalId: "measurement-1", humanUserId: H1,
            loggedSetGlobalId: "set-1", metricKey: "power", revision: 1 }));
    });
    it("allows owner publication creates and reads while keeping versions immutable", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        for (const [collectionName, contentType] of [
            ["publishedWorkouts", "workout"], ["publishedPlans", "plan"], ["publishedProtocols", "protocol"]
        ]) {
            const value = publishedVersion(H1, `${contentType}-1`, contentType);
            const ref = (0, firestore_1.doc)(db, `users/${H1}/${collectionName}/${value.versionId}`);
            await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)(ref, value));
            await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)(ref));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)(ref, { ...value, revision: 2 }));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.deleteDoc)(ref));
        }
    });
    it("denies cross-owner publication access and owner reassignment", async () => {
        const owner = env.authenticatedContext("uid-a").firestore();
        const foreign = env.authenticatedContext("uid-b").firestore();
        const value = publishedVersion(H1, "workout-1", "workout");
        const path = `users/${H1}/publishedWorkouts/${value.versionId}`;
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.setDoc)((0, firestore_1.doc)(owner, path), value));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(foreign, path)));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(foreign, `users/${H1}/publishedWorkouts/foreign_r1_aaaaaaaaaaaa`), publishedVersion(H1, "foreign", "workout")));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(owner, `users/${H1}/publishedWorkouts/forged_r1_aaaaaaaaaaaa`), publishedVersion(H2, "forged", "workout")));
    });
    it("rejects malformed publication schemas, state pairs, IDs and checksums", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        const base = `users/${H1}/publishedWorkouts`;
        const valid = publishedVersion(H1, "workout-1", "workout");
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `${base}/wrong-version`), valid));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `${base}/bad-checksum`), { ...valid, versionId: "bad-checksum", contentChecksum: "z".repeat(64) }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `${base}/bad-state`), { ...valid, versionId: "bad-state", publicationState: "PUBLISHED", tombstoneState: "SOFT_DELETED" }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `${base}/bad-revision`), { ...valid, versionId: "bad-revision", revision: 0 }));
    });
    it("denies non-owners for every Strength synchronized collection", async () => {
        const db = env.authenticatedContext("uid-b").firestore();
        for (const collection of COLLECTIONS) {
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, `users/${H1}/${collection}/test`)));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}/${collection}/test`), { value: "foreign" }));
        }
    });
    it("allows HIIT's bound owner single-document Human-root permission-gate get", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, `users/${H1}`)));
    });
    it("denies unauthenticated, cross-user, and missing-binding Human-root gets", async () => {
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.unauthenticatedContext().firestore(), `users/${H1}`)));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-b").firestore(), `users/${H1}`)));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-missing").firestore(), `users/${H1}`)));
    });
    it("denies inactive, deleting, deleted, malformed, and unsupported-schema root bindings", async () => {
        for (const binding of [
            { humanUserId: H1, status: "DISABLED", schemaVersion: 1 },
            { humanUserId: H1, status: "DELETING", schemaVersion: 1 },
            { humanUserId: H1, status: "DELETED", schemaVersion: 1 },
            { status: "ACTIVE", schemaVersion: 1 },
            { humanUserId: H1, status: "ACTIVE" },
            { humanUserId: H1, status: "ACTIVE", schemaVersion: 2 }
        ]) {
            await env.withSecurityRulesDisabled(async (c) => (0, firestore_1.setDoc)((0, firestore_1.doc)(c.firestore(), "accounts/uid-a"), binding));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-a").firestore(), `users/${H1}`)));
        }
    });
    it("denies a root get when reverse ownership conflicts", async () => {
        await env.withSecurityRulesDisabled(async (c) => (0, firestore_1.setDoc)((0, firestore_1.doc)(c.firestore(), `users/${H1}`), { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(env.authenticatedContext("uid-a").firestore(), `users/${H1}`)));
    });
    it("denies every client Human-root write and root collection listing", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}`), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.deleteDoc)((0, firestore_1.doc)(db, `users/${H1}`)));
        await env.withSecurityRulesDisabled(async (c) => (0, firestore_1.deleteDoc)((0, firestore_1.doc)(c.firestore(), `users/${H1}`)));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}`), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, "users/human_cccccccccccccccccccccccccccccccc"), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDocs)((0, firestore_1.collection)(db, "users")));
    });
    it("denies unknown Human child paths", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, `users/${H1}/unknown/item`)));
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, `users/${H1}/unknown/item`), { value: true }));
    });
    it("allows public reads of the published production catalogue", async () => {
        const db = env.unauthenticatedContext().firestore();
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, "exercise_catalogue/current")));
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/published-1")));
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/published-1/exercises/bench_press")));
        await (0, rules_unit_testing_1.assertSucceeds)((0, firestore_1.getDocs)((0, firestore_1.collection)(db, "exercise_catalogue_releases/published-1/exercises")));
    });
    it("denies ordinary clients access to draft and staging content", async () => {
        for (const db of [env.unauthenticatedContext().firestore(), env.authenticatedContext("uid-a").firestore()]) {
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/draft-1")));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/draft-1/exercises/secret")));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.getDoc)((0, firestore_1.doc)(db, "staging_exercises/candidate")));
        }
    });
    it("denies Android clients every governed catalogue mutation", async () => {
        const db = env.authenticatedContext("uid-a").firestore();
        for (const ref of [
            (0, firestore_1.doc)(db, "exercise_catalogue/current"),
            (0, firestore_1.doc)(db, "exercise_catalogue_releases/published-1"),
            (0, firestore_1.doc)(db, "exercise_catalogue_releases/published-1/exercises/bench_press")
        ]) {
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)(ref, { status: "published", channel: "production" }));
            await (0, rules_unit_testing_1.assertFails)((0, firestore_1.deleteDoc)(ref));
        }
        await (0, rules_unit_testing_1.assertFails)((0, firestore_1.setDoc)((0, firestore_1.doc)(db, "exercise_catalogue_releases/new-release"), { status: "published", channel: "production" }));
    });
});
//# sourceMappingURL=firestore.rules.test.js.map