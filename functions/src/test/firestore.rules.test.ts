import assert from "assert";
import * as fs from "fs";
import * as path from "path";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc } from "firebase/firestore";

const PROJECT_ID = "demo-hv1-strength-local";
const H1 = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
const H2 = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
const COLLECTIONS = ["profile", "weight", "tape", "customExercises", "templates",
  "templateExercises", "templateSets", "sessions", "loggedSets", "processedCommands", "trainingPlans", "plannedWorkouts"];

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
      await setDoc(doc(db, `users/${H1}/profile/main`), { value: "a" });
      await setDoc(doc(db, `users/${H2}/profile/main`), { value: "b" });
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
      const data = collection === "trainingPlans" || collection === "plannedWorkouts"
        ? { value: collection, humanUserId: H1 } : { value: collection };
      await assertSucceeds(setDoc(doc(db, `users/${H1}/${collection}/test`), data));
      await assertSucceeds(getDoc(doc(db, `users/${H1}/${collection}/test`)));
    }
    assert.strictEqual(COLLECTIONS.length, 12);
  });
  it("denies planner ownership forgery on create and update", async () => {
    const db = env.authenticatedContext("uid-a").firestore();
    for (const collection of ["trainingPlans", "plannedWorkouts"]) {
      const ref = doc(db, `users/${H1}/${collection}/test`);
      await assertFails(setDoc(ref, { humanUserId: H2 }));
      await assertSucceeds(setDoc(ref, { humanUserId: H1 }));
      await assertFails(setDoc(ref, { humanUserId: H2 }));
    }
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
});
