import assert from "assert";
import * as fs from "fs";
import * as path from "path";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc } from "firebase/firestore";

const PROJECT_ID = "demo-hv1-strength-local";
const H1 = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
const H2 = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
const COLLECTIONS = ["profile", "weight", "tape", "customExercises", "templates",
  "templateExercises", "templateSets", "sessions", "loggedSets", "processedCommands", "trainingPlans", "plannedWorkouts"];
const publishedVersion = (humanUserId: string, globalId: string, contentType: "workout" | "plan" | "protocol") => ({
  schemaVersion: `humanv1.${contentType}/1`, globalId, humanUserId, revision: 1,
  publicationState: "PUBLISHED", tombstoneState: "ACTIVE", sourceDraftId: globalId,
  payload: contentType === "plan" ? { weeks: [{ placements: [{ workoutVersionId: "workout-1_r1_aaaaaaaaaaaa" }] }] } : {},
  createdAt: "2026-01-01T00:00:00.000Z", updatedAt: "2026-01-01T00:00:00.000Z",
  publishedAt: "2026-01-01T00:00:00.000Z", contentChecksum: "a".repeat(64),
  versionId: `${globalId}_r1_aaaaaaaaaaaa`, contentType, compatibleTags: []
});

describe("Strength Firestore trusted identity rules", function() {
  this.timeout(30_000);
  let env: RulesTestEnvironment;
  before(async () => {
    const rules = fs.readFileSync(path.resolve(__dirname, "../../../firestore.rules"), "utf8");
    env = await initializeTestEnvironment({ projectId: PROJECT_ID, firestore: { rules } });
  });
  after(async () => env.cleanup());
  beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      await setDoc(doc(db, "accounts/uid-a"), { humanUserId: H1, status: "ACTIVE", schemaVersion: 1 });
      await setDoc(doc(db, `users/${H1}`), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 });
      await setDoc(doc(db, "accounts/uid-b"), { humanUserId: H2, status: "ACTIVE", schemaVersion: 1 });
      await setDoc(doc(db, `users/${H2}`), { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 });
      await setDoc(doc(db, "accounts/uid-a/entitlements/current"), {
        schemaVersion: 1, firebaseUid: "uid-a", humanUserId: H1,
        normalizedState: "ACTIVE_UNTIL_EXPIRY", productScope: "WORKOUT_STUDIO",
        expiryAt: new Date("2099-01-01T00:00:00.000Z")
      });
      await setDoc(doc(db, `users/${H1}/profile/main`), { value: "a" });
      await setDoc(doc(db, `users/${H2}/profile/main`), { value: "b" });
      await setDoc(doc(db, "exercise_catalogue/current"), { releaseId: "published-1", status: "published", channel: "production" });
      await setDoc(doc(db, "exercise_catalogue_releases/published-1"), { releaseId: "published-1", status: "published", channel: "production" });
      await setDoc(doc(db, "exercise_catalogue_releases/published-1/exercises/bench_press"), { exerciseId: "bench_press" });
      await setDoc(doc(db, "exercise_catalogue_releases/draft-1"), { releaseId: "draft-1", status: "draft", channel: "production" });
      await setDoc(doc(db, "exercise_catalogue_releases/draft-1/exercises/secret"), { exerciseId: "secret" });
      await setDoc(doc(db, "staging_exercises/candidate"), { name: "Unreviewed" });
    });
  });

  it("denies unauthenticated access", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, `users/${H1}/profile/main`)));
    await assertFails(getDoc(doc(db, "accounts/uid-a")));
  });
  it("allows A to access H1 and B to access H2", async () => {
    await assertSucceeds(getDoc(doc(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
    await assertSucceeds(getDoc(doc(env.authenticatedContext("uid-b").firestore(), `users/${H2}/profile/main`)));
  });
  it("denies A to H2 and B to H1", async () => {
    await assertFails(getDoc(doc(env.authenticatedContext("uid-a").firestore(), `users/${H2}/profile/main`)));
    await assertFails(getDoc(doc(env.authenticatedContext("uid-b").firestore(), `users/${H1}/profile/main`)));
  });
  it("denies missing forward binding", async () => {
    await assertFails(getDoc(doc(env.authenticatedContext("uid-missing").firestore(), `users/${H1}/profile/main`)));
  });
  it("denies inactive, missing-schema, and unsupported-schema forward bindings", async () => {
    for (const binding of [
      { humanUserId: H1, status: "DISABLED", schemaVersion: 1 },
      { humanUserId: H1, status: "ACTIVE" },
      { humanUserId: H1, status: "ACTIVE", schemaVersion: 2 }
    ]) {
      await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), "accounts/uid-a"), binding));
      await assertFails(getDoc(doc(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
    }
  });
  it("denies a mismatched forward Human ID", async () => {
    await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), "accounts/uid-a"),
      { humanUserId: H2, status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(getDoc(doc(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
  });
  it("denies missing, wrong-owner, inactive, and unsupported reverse roots", async () => {
    const variants: Array<Record<string, unknown> | null> = [null,
      { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 },
      { ownerFirebaseUid: "uid-a", status: "DISABLED", schemaVersion: 1 },
      { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 2 }];
    for (const root of variants) {
      await env.withSecurityRulesDisabled(async c => root === null ? deleteDoc(doc(c.firestore(), `users/${H1}`)) : setDoc(doc(c.firestore(), `users/${H1}`), root));
      await assertFails(getDoc(doc(env.authenticatedContext("uid-a").firestore(), `users/${H1}/profile/main`)));
    }
  });
  it("denies legacy IDs without an approved binding and Firebase UID substitution", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    await assertFails(setDoc(doc(db, "users/human_2080278062xx/sessions/x"), { value: true }));
    await assertFails(setDoc(doc(db, "users/uid-a/sessions/x"), { value: true }));
  });
  it("prevents clients from creating, changing, or deleting account bindings", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    await assertFails(setDoc(doc(db, "accounts/uid-a"), { humanUserId: H2, status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(setDoc(doc(db, "accounts/new"), { humanUserId: H1, status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(deleteDoc(doc(db, "accounts/uid-a")));
  });
  it("prevents ownership mutation and arbitrary Human-root claims", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    await assertFails(setDoc(doc(db, `users/${H1}`), { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(setDoc(doc(db, "users/human_cccccccccccccccccccccccccccccccc"), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
  });
  it("allows the owner for every Strength synchronized collection", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    for (const collection of COLLECTIONS) {
      const documentId = collection === "customExercises" ? "exercise_android12" : `synthetic-${collection}`;
      const versioned = { globalId: documentId, humanUserId: H1, createdAt: 1, updatedAt: 1,
        deletedAt: null, revision: 1, originDeviceId: "synthetic-device" };
      const data = collection === "customExercises"
        ? { globalId: documentId, id: "custom_12345678-1234-1234-1234-123456789abc",
            humanUserId: H1, name: "Android private", category: "Chest", isCustom: true,
            createdAt: 1, updatedAt: 1, deletedAt: null, revision: 1,
            originDeviceId: "device_android_1", lastSyncedAt: 2 }
        : collection === "templates"
          ? { ...versioned, name: "Synthetic routine", exerciseIdsJson: "[]" }
        : collection === "templateExercises"
          ? { ...versioned, templateGlobalId: "synthetic-template", exerciseId: "bench_press", position: 0 }
        : collection === "templateSets"
          ? { ...versioned, templateExerciseGlobalId: "synthetic-template-exercise", position: 0, setType: "WORKING" }
        : collection === "trainingPlans" || collection === "plannedWorkouts"
          ? { ...versioned, value: collection } : { value: collection };
      await assertSucceeds(setDoc(doc(db, `users/${H1}/${collection}/${documentId}`), data));
      await assertSucceeds(getDoc(doc(db, `users/${H1}/${collection}/${documentId}`)));
    }
    assert.strictEqual(COLLECTIONS.length, 12);
  });
  it("denies planner ownership forgery on create and update", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    for (const collection of ["trainingPlans", "plannedWorkouts"]) {
      const globalId = `synthetic-${collection}-ownership`;
      const ref = doc(db, `users/${H1}/${collection}/${globalId}`);
      const valid = { globalId, humanUserId: H1, createdAt: 1, updatedAt: 1,
        deletedAt: null, revision: 1, originDeviceId: "synthetic-device" };
      await assertFails(setDoc(ref, { ...valid, humanUserId: H2 }));
      await assertSucceeds(setDoc(ref, valid));
      await assertFails(setDoc(ref, { ...valid, humanUserId: H2, revision: 2, updatedAt: 2 }));
    }
  });
  it("allows only immutable-owner schema-14 measurement records with monotonic revisions", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    const ref = doc(db, `users/${H1}/measurementRecords/measurement-1`);
    const valid = { schemaVersion: 14, globalId: "measurement-1", humanUserId: H1,
      loggedSetGlobalId: "set-1", metricKey: "power", revision: 1 };
    await assertSucceeds(setDoc(ref, valid));
    await assertSucceeds(setDoc(ref, { ...valid, revision: 2 }));
    await assertFails(setDoc(ref, { ...valid, revision: 1 }));
    await assertFails(setDoc(ref, { ...valid, revision: 3, humanUserId: H2 }));
    await assertFails(setDoc(ref, { ...valid, revision: 3, loggedSetGlobalId: "set-2" }));
    await assertFails(setDoc(doc(db, `users/${H1}/measurementRecords/wrong-id`), valid));
    await assertFails(deleteDoc(ref));
  });
  it("denies foreign and malformed measurement records", async () => {
    const ownerDb = env.authenticatedContext("uid-a").firestore();
    const foreignDb = env.authenticatedContext("uid-b").firestore();
    const path = `users/${H1}/measurementRecords/measurement-1`;
    await assertFails(setDoc(doc(ownerDb, path), { schemaVersion: 13, globalId: "measurement-1", humanUserId: H1,
      loggedSetGlobalId: "set-1", metricKey: "power", revision: 1 }));
    await assertFails(setDoc(doc(foreignDb, path), { schemaVersion: 14, globalId: "measurement-1", humanUserId: H1,
      loggedSetGlobalId: "set-1", metricKey: "power", revision: 1 }));
  });
  it("denies direct publication creates and reads only governed immutable versions", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    for (const [collectionName, contentType] of [
      ["publishedWorkouts", "workout"], ["publishedPlans", "plan"]
    ] as const) {
      const value = publishedVersion(H1, `${contentType}-1`, contentType);
      const ref = doc(db, `users/${H1}/${collectionName}/${value.versionId}`);
      await assertFails(setDoc(ref, value));
      await env.withSecurityRulesDisabled(async context => setDoc(doc(context.firestore(), `users/${H1}/${collectionName}/${value.versionId}`), value));
      await assertSucceeds(getDoc(ref));
      await assertFails(setDoc(ref, { ...value, revision: 2 }));
      await assertFails(deleteDoc(ref));
    }
  });
  it("denies cross-owner publication access and owner reassignment", async () => {
    const owner = env.authenticatedContext("uid-a").firestore();
    const foreign = env.authenticatedContext("uid-b").firestore();
    const value = publishedVersion(H1, "workout-1", "workout");
    const path = `users/${H1}/publishedWorkouts/${value.versionId}`;
    await env.withSecurityRulesDisabled(async context => setDoc(doc(context.firestore(), path), value));
    await assertFails(getDoc(doc(foreign, path)));
    await assertFails(setDoc(doc(foreign, `users/${H1}/publishedWorkouts/foreign_r1_aaaaaaaaaaaa`),
      publishedVersion(H1, "foreign", "workout")));
    await assertFails(setDoc(doc(owner, `users/${H1}/publishedWorkouts/forged_r1_aaaaaaaaaaaa`),
      publishedVersion(H2, "forged", "workout")));
  });
  it("allows only deterministic owner-bound immutable Human Strength delivery acknowledgements", async () => {
    const owner = env.authenticatedContext("uid-a").firestore();
    const foreign = env.authenticatedContext("uid-b").firestore();
    const publication = publishedVersion(H1, "workout-1", "workout");
    await env.withSecurityRulesDisabled(async context => setDoc(doc(context.firestore(), `users/${H1}/publishedWorkouts/${publication.versionId}`), publication));
    const ackId = "strength_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    const path = `users/${H1}/workoutDeliveryAcks/${ackId}`;
    const value = { schemaVersion: 1, acknowledgementId: ackId, humanUserId: H1,
      workoutGlobalId: "workout-1", versionId: "workout-1_r1_aaaaaaaaaaaa",
      applicationId: "HUMAN_STRENGTH", appliedChecksum: "a".repeat(64), sourceRevision: 1,
      state: "APPLIED", reasonCode: null, clientAppliedAtMillis: 1, createdAt: serverTimestamp() };
    await assertFails(setDoc(doc(owner, path), value));
    await env.withSecurityRulesDisabled(async context => setDoc(doc(context.firestore(), path), { ...value, createdAt: new Date() }));
    await assertSucceeds(getDoc(doc(owner, path)));
    await assertFails(getDoc(doc(foreign, path)));
    await assertFails(setDoc(doc(owner, path), { ...value, state: "CONFLICT" }));
    await assertFails(deleteDoc(doc(owner, path)));
    await assertFails(setDoc(doc(owner, `users/${H1}/workoutDeliveryAcks/wrong`), value));
    await assertFails(setDoc(doc(owner, `users/${H1}/workoutDeliveryAcks/forged`), { ...value,
      acknowledgementId: "forged", humanUserId: H2 }));
    await assertFails(setDoc(doc(owner, `users/${H1}/workoutDeliveryAcks/wrong-checksum`), { ...value,
      acknowledgementId: "wrong-checksum", appliedChecksum: "b".repeat(64) }));
  });
  it("rejects malformed publication schemas, state pairs, IDs and checksums", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    const base = `users/${H1}/publishedWorkouts`;
    const valid = publishedVersion(H1, "workout-1", "workout");
    await assertFails(setDoc(doc(db, `${base}/wrong-version`), valid));
    await assertFails(setDoc(doc(db, `${base}/bad-checksum`), { ...valid, versionId: "bad-checksum", contentChecksum: "z".repeat(64) }));
    await assertFails(setDoc(doc(db, `${base}/bad-state`), { ...valid, versionId: "bad-state", publicationState: "PUBLISHED", tombstoneState: "SOFT_DELETED" }));
    await assertFails(setDoc(doc(db, `${base}/bad-revision`), { ...valid, versionId: "bad-revision", revision: 0 }));
  });
  it("denies non-owners for every Strength synchronized collection", async () => {
    const db = env.authenticatedContext("uid-b").firestore();
    for (const collection of COLLECTIONS) {
      await assertFails(getDoc(doc(db, `users/${H1}/${collection}/test`)));
      await assertFails(setDoc(doc(db, `users/${H1}/${collection}/test`), { value: "foreign" }));
    }
  });
  it("allows HIIT's bound owner single-document Human-root permission-gate get", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    await assertSucceeds(getDoc(doc(db, `users/${H1}`)));
  });
  it("denies unauthenticated, cross-user, and missing-binding Human-root gets", async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), `users/${H1}`)));
    await assertFails(getDoc(doc(env.authenticatedContext("uid-b").firestore(), `users/${H1}`)));
    await assertFails(getDoc(doc(env.authenticatedContext("uid-missing").firestore(), `users/${H1}`)));
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
      await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), "accounts/uid-a"), binding));
      await assertFails(getDoc(doc(env.authenticatedContext("uid-a").firestore(), `users/${H1}`)));
    }
  });
  it("denies a root get when reverse ownership conflicts", async () => {
    await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(), `users/${H1}`),
      { ownerFirebaseUid: "uid-b", status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(getDoc(doc(env.authenticatedContext("uid-a").firestore(), `users/${H1}`)));
  });
  it("denies every client Human-root write and root collection listing", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    await assertFails(setDoc(doc(db, `users/${H1}`), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(deleteDoc(doc(db, `users/${H1}`)));
    await env.withSecurityRulesDisabled(async c => deleteDoc(doc(c.firestore(), `users/${H1}`)));
    await assertFails(setDoc(doc(db, `users/${H1}`), { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(setDoc(doc(db, "users/human_cccccccccccccccccccccccccccccccc"),
      { ownerFirebaseUid: "uid-a", status: "ACTIVE", schemaVersion: 1 }));
    await assertFails(getDocs(collection(db, "users")));
  });
  it("denies unknown Human child paths", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    await assertFails(getDoc(doc(db, `users/${H1}/unknown/item`)));
    await assertFails(setDoc(doc(db, `users/${H1}/unknown/item`), { value: true }));
  });
  it("allows public reads of the published production catalogue", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertSucceeds(getDoc(doc(db, "exercise_catalogue/current")));
    await assertSucceeds(getDoc(doc(db, "exercise_catalogue_releases/published-1")));
    await assertSucceeds(getDoc(doc(db, "exercise_catalogue_releases/published-1/exercises/bench_press")));
    await assertSucceeds(getDocs(collection(db, "exercise_catalogue_releases/published-1/exercises")));
  });
  it("denies ordinary clients access to draft and staging content", async () => {
    for (const db of [env.unauthenticatedContext().firestore(), env.authenticatedContext("uid-a").firestore()]) {
      await assertFails(getDoc(doc(db, "exercise_catalogue_releases/draft-1")));
      await assertFails(getDoc(doc(db, "exercise_catalogue_releases/draft-1/exercises/secret")));
      await assertFails(getDoc(doc(db, "staging_exercises/candidate")));
    }
  });
  it("denies Android clients every governed catalogue mutation", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    for (const ref of [
      doc(db, "exercise_catalogue/current"),
      doc(db, "exercise_catalogue_releases/published-1"),
      doc(db, "exercise_catalogue_releases/published-1/exercises/bench_press")
    ]) {
      await assertFails(setDoc(ref, { status: "published", channel: "production" }));
      await assertFails(deleteDoc(ref));
    }
    await assertFails(setDoc(doc(db, "exercise_catalogue_releases/new-release"), { status: "published", channel: "production" }));
  });
});
