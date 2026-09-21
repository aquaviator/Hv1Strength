import { strict as assert } from "assert";
import * as admin from "firebase-admin";
import { HttpsError } from "firebase-functions/v2/https";
import { saveStudioPlanDraftCallable, saveStudioPlanDraftForUid, SaveStudioPlanDraftRequest } from "../studioPlanDraft";

const projectId = "demo-hv1-strength-local";
const uid = "atomic-owner"; const owner = "human_atomicowner12345678"; const other = "human_atomicother12345678";
const now = "2026-09-21T10:00:00.000Z";
const workout = (id: string, humanUserId = owner, revision = 1, deletedAt: string | null = null) => ({ schemaVersion: 1, globalId: id, humanUserId,
  revision, status: "DRAFT", createdAt: now, updatedAt: now, deletedAt, originClientId: "fixture", payload: { schemaVersion: "humanv1.workout/1", workoutId: id,
    title: id, discipline: "STRENGTH", blocks: [{ blockId: `${id}-block`, type: "EXERCISE", exerciseId: "squat", efforts: [{ effortId: "set-1", prescriptions: [{ metricKey: "repetitions", targetValue: 5 }] }] }] } });

function request(count = 8, weeks = 10, requestKey = "atomic-create"): SaveStudioPlanDraftRequest {
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

describe("atomic Studio plan-draft save", function() {
  this.timeout(45_000); let db: admin.firestore.Firestore;
  before(async () => { if (!admin.apps.length) admin.initializeApp({ projectId }); db = admin.firestore(); });
  beforeEach(async () => {
    const roots = await db.listCollections(); for (const root of roots) for (const item of (await root.get()).docs) {
      const children = await item.ref.listCollections(); for (const child of children) for (const nested of (await child.get()).docs) await nested.ref.delete();
      await item.ref.delete();
    }
    await db.collection("accounts").doc(uid).set({ humanUserId: owner, status: "ACTIVE", schemaVersion: 1 });
    await db.collection("users").doc(owner).set({ ownerFirebaseUid: uid, status: "ACTIVE", schemaVersion: 1 });
    await db.doc(`accounts/${uid}/entitlements/current`).set({ schemaVersion: 1, firebaseUid: uid, humanUserId: owner, productScope: "WORKOUT_STUDIO",
      normalizedState: "ACTIVE_UNTIL_EXPIRY", expiryAt: admin.firestore.Timestamp.fromDate(new Date("2099-01-01T00:00:00Z")) });
  });
  async function seedWorkouts(count = 8) { const batch = db.batch(); for (let i = 0; i < count; i++) batch.set(db.doc(`users/${owner}/workoutDrafts/workout-${i}`), workout(`workout-${i}`)); await batch.commit(); }

  it("denies unauthenticated and missing owner bindings", async () => {
    await assert.rejects(saveStudioPlanDraftCallable(db, { auth: undefined, data: request() } as any), (error: HttpsError) => error.code === "unauthenticated");
    await assert.rejects(saveStudioPlanDraftForUid(db, "missing", request()));
  });
  it("rejects malformed, unknown, missing and oversized manifests before any plan write", async () => {
    const malformed = request(); (malformed.plan as any).weeks = [];
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, malformed));
    const unknown = request(); (unknown.dependencies[0] as any).dependencyKind = "UNKNOWN";
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, unknown));
    const missing = request(); missing.dependencies.pop();
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, missing));
    const oversized = request(0, 53);
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, oversized));
    assert.equal((await db.collection(`users/${owner}/planDrafts`).get()).size, 0);
  });
  it("rejects cross-owner, stale and archived workout targets without revealing foreign existence", async () => {
    await db.doc(`users/${other}/workoutDrafts/workout-0`).set(workout("workout-0", other));
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, request(1, 1, "foreign")), /WORKOUT_DEPENDENCY_UNAVAILABLE/);
    await db.doc(`users/${owner}/workoutDrafts/workout-0`).set(workout("workout-0", owner, 2));
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, request(1, 1, "stale")), /WORKOUT_REVISION_STALE/);
    await db.doc(`users/${owner}/workoutDrafts/workout-0`).set(workout("workout-0", owner, 1, now));
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, request(1, 1, "archived")), /WORKOUT_DEPENDENCY_ARCHIVED/);
  });
  it("atomically saves the eight-workout ten-week fixture and identical retry creates no duplicates", async () => {
    await seedWorkouts(); const input = request();
    const first = await saveStudioPlanDraftForUid(db, uid, input); const second = await saveStudioPlanDraftForUid(db, uid, input);
    assert.equal(first.revision, 1); assert.equal(second.idempotent, true);
    assert.equal((await db.collection(`users/${owner}/planDrafts`).get()).size, 1);
    assert.equal((await db.collection(`users/${owner}/planDraftDependencies`).get()).size, 8);
    assert.equal((await db.collection(`users/${owner}/planDraftSaveAudits`).get()).size, 1);
    for (const name of ["publishedWorkouts", "publishedPlans", "workoutDeliveryAcks", "planDeliveryAcks", "plannedWorkouts", "sessions", "loggedSets"])
      assert.equal((await db.collection(`users/${owner}/${name}`).get()).size, 0);
  });
  it("updates once, removes obsolete dependencies and rejects stale updates", async () => {
    await seedWorkouts(); await saveStudioPlanDraftForUid(db, uid, request());
    const update = request(7, 10, "atomic-update"); update.create = false; update.expectedRevision = 1;
    const result = await saveStudioPlanDraftForUid(db, uid, update); assert.equal(result.revision, 2);
    assert.equal((await db.collection(`users/${owner}/planDraftDependencies`).get()).size, 7);
    const stale = request(7, 10, "atomic-stale"); stale.create = false; stale.expectedRevision = 1;
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, stale), /PLAN_REVISION_STALE/);
  });
  it("fails closed when an idempotency key is reused for changed content", async () => {
    await seedWorkouts(); const first = request(); await saveStudioPlanDraftForUid(db, uid, first);
    const changed = request(); (changed.plan as any).title = "Changed";
    await assert.rejects(saveStudioPlanDraftForUid(db, uid, changed), /IDEMPOTENCY_KEY_REUSED/);
  });
  it("handles concurrent identical creates as one commit", async () => {
    await seedWorkouts(); const [one, two] = await Promise.all([saveStudioPlanDraftForUid(db, uid, request()), saveStudioPlanDraftForUid(db, uid, request())]);
    assert.equal([one, two].filter(item => !item.idempotent).length, 1);
    assert.equal((await db.collection(`users/${owner}/planDraftSaveAudits`).get()).size, 1);
  });
  it("an injected pre-write failure leaves no partial state and audit stores no payload", async () => {
    await seedWorkouts(); await assert.rejects(saveStudioPlanDraftForUid(db, uid, request(), { beforeWrites: () => { throw new Error("INJECTED"); } }));
    assert.equal((await db.collection(`users/${owner}/planDrafts`).get()).size, 0);
    assert.equal((await db.collection(`users/${owner}/planDraftDependencies`).get()).size, 0);
    const result = await saveStudioPlanDraftForUid(db, uid, request());
    const audit = (await db.collection(`users/${owner}/planDraftSaveAudits`).get()).docs[0].data();
    assert.equal(result.revision, 1); assert.equal("plan" in audit, false); assert.equal("dependencies" in audit, false);
  });
});
