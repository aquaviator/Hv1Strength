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
const studioPublication_1 = require("../studioPublication");
const projectId = "demo-hv1-strength-local";
const uid = "auth_studio_test";
const owner = "human_studiotest12345678";
const now = "2026-01-01T00:00:00.000Z";
function workout(id) {
    return { schemaVersion: "humanv1.workout/1", workoutId: id, title: id, description: "", discipline: "STRENGTH", catalogueReleaseId: "catalogue-v1", tags: [],
        blocks: [{ blockId: `${id}-block`, type: "EXERCISE", exerciseId: "squat", exerciseNameSnapshot: "Squat", efforts: [{ effortId: `${id}-set`, effortType: "WORKING", prescriptions: [{ prescriptionId: `${id}-reps`, metricKey: "repetitions", targetValue: 5 }] }] }] };
}
function plan(ids) {
    return { schemaVersion: "humanv1.studio-plan-draft/1", planId: "plan-secure", title: "Secure plan", description: "", startDate: "2026-01-05", timezone: "Europe/London",
        dependencyOwnerHumanUserId: owner, dependencyStorageVersion: 1, dependencyCount: ids.length, dependencyKinds: ["WORKOUT_DRAFT"],
        weeks: [{ weekId: "week-1", weekNumber: 1, label: "Week 1", placements: ids.map((id, index) => ({ placementId: `placement-${index}`, dayOfWeek: index % 7 + 1,
                    workoutId: id, preferredMinuteOfDay: null, reminderEnabled: false, notes: "" })) }] };
}
describe("governed Studio publication", function () {
    this.timeout(30000);
    let db;
    before(async () => {
        if (!admin.apps.length)
            admin.initializeApp({ projectId });
        db = admin.firestore();
        await db.collection("accounts").doc(uid).set({ humanUserId: owner, status: "ACTIVE", schemaVersion: 1 });
        await db.collection("users").doc(owner).set({ ownerFirebaseUid: uid, status: "ACTIVE", schemaVersion: 1 });
    });
    beforeEach(async () => {
        const collections = await db.collection("users").doc(owner).listCollections();
        for (const collection of collections)
            for (const item of (await collection.get()).docs)
                await item.ref.delete();
    });
    async function seed(count = 8) {
        const ids = Array.from({ length: count }, (_, index) => `workout-${index}`);
        const root = db.collection("users").doc(owner);
        const batch = db.batch();
        ids.forEach((id, index) => {
            batch.set(root.collection("workoutDrafts").doc(id), { schemaVersion: 1, globalId: id, humanUserId: owner, revision: 1, status: "DRAFT", payload: workout(id), createdAt: now, updatedAt: now, deletedAt: null, originClientId: "test" });
            batch.set(root.collection("planDraftDependencies").doc(`plan-secure__placement-${index}`), { schemaVersion: "humanv1.studio-plan-draft-dependency/1", dependencyId: `plan-secure__placement-${index}`,
                humanUserId: owner, planId: "plan-secure", placementId: `placement-${index}`, dependencyKind: "WORKOUT_DRAFT", referencedStableId: id, expectedRevision: 1, expectedUpdatedAt: now,
                immutableVersionId: null, immutableRevision: null, immutableChecksum: null, immutableSchemaVersion: null, displayName: id, provenance: "WORKOUT_STUDIO", revision: 1, createdAt: now, updatedAt: now, deletedAt: null });
        });
        batch.set(root.collection("planDrafts").doc("plan-secure"), { schemaVersion: 1, globalId: "plan-secure", humanUserId: owner, revision: 1, status: "DRAFT", payload: plan(ids), createdAt: now, updatedAt: now, deletedAt: null, originClientId: "test" });
        await batch.commit();
        return ids;
    }
    it("publishes eight dependencies before one exact plan and replays without duplicates", async () => {
        await seed();
        const root = db.collection("users").doc(owner);
        const first = await (0, studioPublication_1.publishStudioPlanForUid)(db, uid, { planId: "plan-secure", expectedRevision: 1, idempotencyKey: "publish-1" });
        assert_1.strict.equal(first.workoutVersionIds.length, 8);
        assert_1.strict.equal((await root.collection("publishedWorkouts").get()).size, 8);
        assert_1.strict.equal((await root.collection("publishedPlans").get()).size, 1);
        assert_1.strict.equal((await root.collection("plannedWorkouts").get()).size, 8);
        const workoutPublication = (await root.collection("publishedWorkouts").doc(first.workoutVersionIds[0]).get()).data();
        assert_1.strict.equal(workoutPublication.schemaVersion, "humanv1.canonical-workout/1");
        assert_1.strict.equal(workoutPublication.payload.schemaVersion, "humanv1.canonical-workout/1");
        const planPublication = (await root.collection("publishedPlans").doc(first.planVersionId).get()).data();
        assert_1.strict.equal(planPublication.schemaVersion, "humanv1.canonical-plan/1");
        assert_1.strict.equal(planPublication.payload.schemaVersion, "humanv1.canonical-plan/1");
        assert_1.strict.equal(planPublication.payload.scheduleSchemaVersion, "1.2");
        assert_1.strict.ok(planPublication.payload.weeks.every((week) => week.placements.every((placement) => typeof placement.workoutGlobalId === "string" && typeof placement.workoutVersionId === "string"
            && Number.isInteger(placement.workoutRevision) && /^[0-9a-f]{64}$/.test(placement.workoutChecksum)
            && placement.workoutOwnerHumanUserId === owner && placement.destinationApplication === "HUMAN_STRENGTH"
            && !Object.prototype.hasOwnProperty.call(placement, "dependency"))));
        const second = await (0, studioPublication_1.publishStudioPlanForUid)(db, uid, { planId: "plan-secure", expectedRevision: 1, idempotencyKey: "publish-2" });
        assert_1.strict.equal(second.planVersionId, first.planVersionId);
        assert_1.strict.equal((await root.collection("publishedWorkouts").get()).size, 8);
        assert_1.strict.equal((await root.collection("publishedPlans").get()).size, 1);
    });
    it("fails a missing dependency and changed-during-preflight without partial publication", async () => {
        await seed(2);
        const root = db.collection("users").doc(owner);
        await root.collection("planDraftDependencies").doc("plan-secure__placement-1").delete();
        await assert_1.strict.rejects((0, studioPublication_1.publishStudioPlanForUid)(db, uid, { planId: "plan-secure", expectedRevision: 1, idempotencyKey: "missing" }));
        assert_1.strict.equal((await root.collection("publishedPlans").get()).size, 0);
        await seed(2);
        await assert_1.strict.rejects((0, studioPublication_1.publishStudioPlanForUid)(db, uid, { planId: "plan-secure", expectedRevision: 1, idempotencyKey: "changed" }, { beforeCommit: async () => {
                await root.collection("workoutDrafts").doc("workout-0").update({ revision: 2, updatedAt: "2026-01-02T00:00:00.000Z" });
            } }));
        assert_1.strict.equal((await root.collection("publishedPlans").get()).size, 0);
    });
    it("creates one new workout and plan version after one draft changes, then accepts an exact governed acknowledgement", async () => {
        await seed(2);
        const root = db.collection("users").doc(owner);
        const first = await (0, studioPublication_1.publishStudioPlanForUid)(db, uid, { planId: "plan-secure", expectedRevision: 1, idempotencyKey: "first" });
        const changed = workout("workout-0");
        changed.title = "Changed workout";
        await root.collection("workoutDrafts").doc("workout-0").update({ revision: 2, updatedAt: "2026-01-02T00:00:00.000Z", payload: changed });
        await root.collection("planDraftDependencies").doc("plan-secure__placement-0").update({ expectedRevision: 2, revision: 2, updatedAt: "2026-01-02T00:00:00.000Z" });
        await root.collection("planDraftDependencies").doc("plan-secure__placement-1").update({ revision: 2, updatedAt: "2026-01-02T00:00:00.000Z" });
        await root.collection("planDrafts").doc("plan-secure").update({ revision: 2, updatedAt: "2026-01-02T00:00:00.000Z" });
        const second = await (0, studioPublication_1.publishStudioPlanForUid)(db, uid, { planId: "plan-secure", expectedRevision: 2, idempotencyKey: "second" });
        assert_1.strict.notEqual(second.planVersionId, first.planVersionId);
        assert_1.strict.equal((await root.collection("publishedWorkouts").get()).size, 3);
        assert_1.strict.equal((await root.collection("publishedPlans").get()).size, 2);
        const ack = await (0, studioPublication_1.acknowledgeStudioDeliveryForUid)(db, uid, { entityType: "plan", acknowledgementId: "ack-plan", globalId: "plan-secure",
            versionId: second.planVersionId, checksum: second.planChecksum, sourceRevision: second.planRevision, workoutVersionIds: second.workoutVersionIds,
            state: "APPLIED", clientAppliedAtMillis: 1 });
        assert_1.strict.equal(ack.state, "APPLIED");
        assert_1.strict.equal((await root.collection("planDeliveryAcks").get()).size, 1);
        await assert_1.strict.rejects((0, studioPublication_1.acknowledgeStudioDeliveryForUid)(db, uid, { entityType: "plan", acknowledgementId: "bad", globalId: "plan-secure",
            versionId: second.planVersionId, checksum: "b".repeat(64), sourceRevision: second.planRevision, workoutVersionIds: second.workoutVersionIds,
            state: "APPLIED", clientAppliedAtMillis: 1 }));
    });
});
//# sourceMappingURL=studio-publication.test.js.map