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
Object.defineProperty(exports, "__esModule", { value: true });
const assert_1 = require("assert");
const admin = __importStar(require("firebase-admin"));
const studioPlanDraft_1 = require("../studioPlanDraft");
const projectId = "demo-hv1-strength-local";
const uid = "atomic-owner";
const owner = "human_atomicowner12345678";
const other = "human_atomicother12345678";
const now = "2026-09-21T10:00:00.000Z";
const workout = (id, humanUserId = owner, revision = 1, deletedAt = null) => ({ schemaVersion: 1, globalId: id, humanUserId,
    revision, status: "DRAFT", createdAt: now, updatedAt: now, deletedAt, originClientId: "fixture", payload: { schemaVersion: "humanv1.workout/1", workoutId: id,
        title: id, discipline: "STRENGTH", blocks: [{ blockId: `${id}-block`, type: "EXERCISE", exerciseId: "squat", efforts: [{ effortId: "set-1", prescriptions: [{ metricKey: "repetitions", targetValue: 5 }] }] }] } });
function request(count = 8, weeks = 10, requestKey = "atomic-create") {
    const placements = Array.from({ length: count }, (_, index) => ({ placementId: `placement-${index}`, dayOfWeek: index % 7 + 1,
        workoutId: `workout-${index}`, preferredMinuteOfDay: null, reminderEnabled: false, notes: "",
        dependency: { kind: "WORKOUT_DRAFT", workoutDraftId: `workout-${index}`, humanUserId: "ignored-client-owner", expectedRevision: 1,
            displayName: `Workout ${index}`, originApplication: "WORKOUT_STUDIO" } }));
    const planWeeks = Array.from({ length: weeks }, (_, index) => ({ weekId: `week-${index + 1}`, weekNumber: index + 1, label: `Week ${index + 1}`,
        placements: index === 0 ? placements : [] }));
    return { planId: "atomic-plan", schemaVersion: "humanv1.studio-plan-draft/1", create: true, expectedRevision: null, requestKey,
        clientOperationId: "studio-test-operation", plan: { schemaVersion: "humanv1.studio-plan-draft/1", planId: "atomic-plan", title: "Ten week plan", description: "", weeks: planWeeks },
        dependencies: placements.map((placement, index) => ({ dependencyId: `atomic-plan__${placement.placementId}`, placementId: placement.placementId,
            dependencyKind: "WORKOUT_DRAFT", referencedStableId: `workout-${index}`, expectedRevision: 1, expectedUpdatedAt: now, immutableVersionId: null,
            immutableRevision: null, immutableChecksum: null, immutableSchemaVersion: null, displayName: `Workout ${index}`, provenance: "WORKOUT_STUDIO" })) };
}
describe("atomic Studio plan-draft save", function () {
    this.timeout(45_000);
    let db;
    before(async () => { if (!admin.apps.length)
        admin.initializeApp({ projectId }); db = admin.firestore(); });
    beforeEach(async () => {
        const roots = await db.listCollections();
        for (const root of roots)
            for (const item of (await root.get()).docs) {
                const children = await item.ref.listCollections();
                for (const child of children)
                    for (const nested of (await child.get()).docs)
                        await nested.ref.delete();
                await item.ref.delete();
            }
        await db.collection("accounts").doc(uid).set({ humanUserId: owner, status: "ACTIVE", schemaVersion: 1 });
        await db.collection("users").doc(owner).set({ ownerFirebaseUid: uid, status: "ACTIVE", schemaVersion: 1 });
        await db.doc(`accounts/${uid}/entitlements/current`).set({ schemaVersion: 1, firebaseUid: uid, humanUserId: owner, productScope: "WORKOUT_STUDIO",
            normalizedState: "ACTIVE_UNTIL_EXPIRY", expiryAt: admin.firestore.Timestamp.fromDate(new Date("2099-01-01T00:00:00Z")) });
    });
    async function seedWorkouts(count = 8) { const batch = db.batch(); for (let i = 0; i < count; i++)
        batch.set(db.doc(`users/${owner}/workoutDrafts/workout-${i}`), workout(`workout-${i}`)); await batch.commit(); }
    async function savedState() {
        const plan = await db.doc(`users/${owner}/planDrafts/atomic-plan`).get();
        const dependencies = await db.collection(`users/${owner}/planDraftDependencies`).orderBy("dependencyId").get();
        const audits = await db.collection(`users/${owner}/planDraftSaveAudits`).get();
        return { revision: plan.data()?.revision, updatedAt: plan.data()?.updatedAt,
            dependencies: dependencies.docs.map(item => ({ id: item.id, revision: item.data().revision, updatedAt: item.data().updatedAt })), audits: audits.size };
    }
    it("denies unauthenticated and missing owner bindings", async () => {
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftCallable)(db, { auth: undefined, data: request() }), (error) => error.code === "unauthenticated");
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, "missing", request()), (error) => error.code === "MISSING_BINDING");
    });
    it("does not let another UID, an inactive binding or client owner data cross the trusted boundary", async () => {
        await db.collection("accounts").doc("other-uid").set({ humanUserId: owner, status: "ACTIVE", schemaVersion: 1 });
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, "other-uid", request()), (error) => error.code === "BINDING_CONFLICT");
        await db.collection("accounts").doc(uid).update({ status: "INACTIVE" });
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request()), (error) => error.code === "MISSING_BINDING");
        await db.collection("accounts").doc(uid).update({ status: "ACTIVE" });
        await seedWorkouts(1);
        const input = request(1, 1, "client-owner-ignored");
        input.humanUserId = other;
        await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, input);
        assert_1.strict.equal((await db.doc(`users/${owner}/planDrafts/atomic-plan`).get()).data()?.humanUserId, owner);
        assert_1.strict.equal((await db.doc(`users/${other}/planDrafts/atomic-plan`).get()).exists, false);
    });
    it("denies missing and expired Studio entitlements", async () => {
        await db.doc(`accounts/${uid}/entitlements/current`).delete();
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request()), /STUDIO_ACCESS_REQUIRED/);
        await db.doc(`accounts/${uid}/entitlements/current`).set({ schemaVersion: 1, firebaseUid: uid, humanUserId: owner, productScope: "WORKOUT_STUDIO",
            normalizedState: "ACTIVE_UNTIL_EXPIRY", expiryAt: admin.firestore.Timestamp.fromDate(new Date("2020-01-01T00:00:00Z")) });
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request()), /STUDIO_ACCESS_REQUIRED/);
    });
    it("rejects malformed, unknown, missing and oversized manifests before any plan write", async () => {
        const malformed = request();
        malformed.plan.weeks = [];
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, malformed));
        const unknown = request();
        unknown.dependencies[0].dependencyKind = "UNKNOWN";
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, unknown));
        const missing = request();
        missing.dependencies.pop();
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, missing));
        const oversized = request(0, 53);
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, oversized));
        assert_1.strict.equal((await db.collection(`users/${owner}/planDrafts`).get()).size, 0);
    });
    it("rejects cross-owner, stale and archived workout targets without revealing foreign existence", async () => {
        await db.doc(`users/${other}/workoutDrafts/workout-0`).set(workout("workout-0", other));
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request(1, 1, "foreign")), /WORKOUT_DEPENDENCY_UNAVAILABLE/);
        await db.doc(`users/${owner}/workoutDrafts/workout-0`).set(workout("workout-0", owner, 2));
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request(1, 1, "stale")), /WORKOUT_REVISION_STALE/);
        await db.doc(`users/${owner}/workoutDrafts/workout-0`).set(workout("workout-0", owner, 1, now));
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request(1, 1, "archived")), /WORKOUT_DEPENDENCY_ARCHIVED/);
    });
    it("atomically saves the eight-workout ten-week fixture and identical retry creates no duplicates", async () => {
        await seedWorkouts();
        const input = request();
        const first = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, input);
        const before = await savedState();
        const second = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, input);
        const after = await savedState();
        assert_1.strict.equal(first.revision, 1);
        assert_1.strict.equal(second.idempotent, true);
        assert_1.strict.deepEqual(after, before);
        assert_1.strict.equal((await db.collection(`users/${owner}/planDrafts`).get()).size, 1);
        assert_1.strict.equal((await db.collection(`users/${owner}/planDraftDependencies`).get()).size, 8);
        assert_1.strict.equal((await db.collection(`users/${owner}/planDraftSaveAudits`).get()).size, 1);
        assert_1.strict.equal((await db.doc(`users/${owner}/planDrafts/atomic-plan`).get()).data()?.humanUserId, owner);
        for (const name of ["publishedWorkouts", "publishedPlans", "workoutDeliveryAcks", "planDeliveryAcks", "plannedWorkouts", "sessions", "loggedSets"])
            assert_1.strict.equal((await db.collection(`users/${owner}/${name}`).get()).size, 0);
    });
    it("returns unchanged for a different key, dependency order and client timestamps", async () => {
        await seedWorkouts();
        await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request());
        const legacyPlanRef = db.doc(`users/${owner}/planDrafts/atomic-plan`);
        const legacyPlan = (await legacyPlanRef.get()).data();
        legacyPlan.payload.weeks[0].placements.forEach((item) => item.dependency.expectedUpdatedAt = now);
        legacyPlan.contentChecksum = "legacy-request-envelope-checksum";
        await legacyPlanRef.set(legacyPlan);
        const before = await savedState();
        const replay = request(8, 10, "different-key");
        replay.create = false;
        replay.expectedRevision = 1;
        replay.dependencies.reverse();
        replay.dependencies.forEach(item => item.expectedUpdatedAt = "2030-01-01T00:00:00.000Z");
        for (const week of replay.plan.weeks)
            for (const placement of week.placements)
                placement.dependency.expectedUpdatedAt = "2030-01-01T00:00:00.000Z";
        const result = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, replay);
        assert_1.strict.equal(result.status, "UNCHANGED");
        assert_1.strict.equal(result.revision, 1);
        assert_1.strict.deepEqual(await savedState(), before);
    });
    it("treats placement order and dependency revision changes as genuine edits", async () => {
        await seedWorkouts();
        await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request());
        const reordered = request(8, 10, "placement-order");
        reordered.create = false;
        reordered.expectedRevision = 1;
        reordered.plan.weeks[0].placements.reverse();
        const first = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, reordered);
        assert_1.strict.equal(first.revision, 2);
        await db.doc(`users/${owner}/workoutDrafts/workout-0`).update({ revision: 2 });
        const revision = request(8, 10, "dependency-revision");
        revision.create = false;
        revision.expectedRevision = 2;
        revision.plan.weeks[0].placements.reverse();
        revision.dependencies[0].expectedRevision = 2;
        revision.plan.weeks[0].placements.find((item) => item.workoutId === "workout-0").dependency.expectedRevision = 2;
        const second = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, revision);
        assert_1.strict.equal(second.revision, 3);
        assert_1.strict.equal((await savedState()).audits, 3);
    });
    it("updates once, removes obsolete dependencies and rejects stale updates", async () => {
        await seedWorkouts();
        await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request());
        const update = request(7, 10, "atomic-update");
        update.create = false;
        update.expectedRevision = 1;
        const result = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, update);
        const beforeReplay = await savedState();
        const replay = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, update);
        assert_1.strict.equal(result.revision, 2);
        assert_1.strict.equal(replay.idempotent, true);
        assert_1.strict.deepEqual(await savedState(), beforeReplay);
        assert_1.strict.equal((await db.collection(`users/${owner}/planDraftDependencies`).get()).size, 7);
        const stale = request(7, 10, "atomic-stale");
        stale.create = false;
        stale.expectedRevision = 1;
        stale.plan.title = "Stale conflicting edit";
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, stale), /PLAN_REVISION_STALE/);
    });
    it("fails closed when an idempotency key is reused for changed content", async () => {
        await seedWorkouts();
        const first = request();
        await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, first);
        const changed = request();
        changed.plan.title = "Changed";
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, changed), /IDEMPOTENCY_KEY_REUSED/);
    });
    it("handles concurrent identical creates as one commit", async () => {
        await seedWorkouts();
        const first = request();
        const secondInput = request(8, 10, "concurrent-create-two");
        const [one, two] = await Promise.all([(0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, first), (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, secondInput)]);
        assert_1.strict.equal([one, two].filter(item => !item.idempotent).length, 1);
        assert_1.strict.equal((await db.collection(`users/${owner}/planDraftSaveAudits`).get()).size, 1);
    });
    it("handles concurrent identical updates as one revision and rejects a conflicting loser", async () => {
        await seedWorkouts();
        await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request());
        const one = request(7, 10, "concurrent-update-one");
        one.create = false;
        one.expectedRevision = 1;
        const two = request(7, 10, "concurrent-update-two");
        two.create = false;
        two.expectedRevision = 1;
        const results = await Promise.all([(0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, one), (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, two)]);
        assert_1.strict.deepEqual(results.map(item => item.revision), [2, 2]);
        assert_1.strict.equal((await savedState()).audits, 2);
        const editA = request(6, 10, "conflict-a");
        editA.create = false;
        editA.expectedRevision = 2;
        const editB = request(7, 10, "conflict-b");
        editB.create = false;
        editB.expectedRevision = 2;
        editB.plan.title = "Conflicting title";
        const settled = await Promise.allSettled([(0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, editA), (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, editB)]);
        assert_1.strict.equal(settled.filter(item => item.status === "fulfilled").length, 1);
        assert_1.strict.equal(settled.filter(item => item.status === "rejected").length, 1);
        assert_1.strict.equal((await savedState()).revision, 3);
        assert_1.strict.equal((await savedState()).audits, 3);
    });
    it("an injected pre-write failure leaves no partial state and audit stores no payload", async () => {
        await seedWorkouts();
        await assert_1.strict.rejects((0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request(), { beforeWrites: () => { throw new Error("INJECTED"); } }));
        assert_1.strict.equal((await db.collection(`users/${owner}/planDrafts`).get()).size, 0);
        assert_1.strict.equal((await db.collection(`users/${owner}/planDraftDependencies`).get()).size, 0);
        const result = await (0, studioPlanDraft_1.saveStudioPlanDraftForUid)(db, uid, request());
        const audit = (await db.collection(`users/${owner}/planDraftSaveAudits`).get()).docs[0].data();
        assert_1.strict.equal(result.revision, 1);
        assert_1.strict.equal("plan" in audit, false);
        assert_1.strict.equal("dependencies" in audit, false);
    });
});
//# sourceMappingURL=studio-plan-draft.test.js.map