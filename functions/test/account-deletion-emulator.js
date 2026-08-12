"use strict";

if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  throw new Error("S8F-B guard: Firestore and Auth emulator hosts are required");
}
if (process.env.GCLOUD_PROJECT === "hv1-platform") throw new Error("S8F-B guard: production project is forbidden");

const assert = require("assert");
const admin = require("firebase-admin");
admin.initializeApp({ projectId: "demo-hv1-data-lifecycle" });
const { deleteAuthorizedUserAccount, purgeUserCloudData, FIRESTORE_USER_SUBCOLLECTIONS } = require("../lib/index.js");

const db = admin.firestore();
const auth = admin.auth();
const humanA = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
const humanB = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

async function identity(uid, human) {
  await auth.createUser({ uid, email: `${uid}@example.invalid`, password: "local-only-password" });
  await db.doc(`accounts/${uid}`).set({ humanUserId: human, status: "ACTIVE", schemaVersion: 1 });
  await db.doc(`users/${human}`).set({ ownerFirebaseUid: uid, status: "ACTIVE", schemaVersion: 1 });
}

async function seedChildren(human, planCount, occurrenceCount) {
  const writer = db.bulkWriter();
  for (const collection of FIRESTORE_USER_SUBCOLLECTIONS) {
    const count = collection === "trainingPlans" ? planCount : collection === "plannedWorkouts" ? occurrenceCount : 1;
    for (let i = 0; i < count; i++) writer.set(db.doc(`users/${human}/${collection}/doc-${i}`), { synthetic: true });
  }
  await writer.close();
}

async function count(path) { return (await db.collection(path).get()).size; }

async function main() {
  await identity("delete-empty", "human_cccccccccccccccccccccccccccccccc");
  await deleteAuthorizedUserAccount(db, auth, "delete-empty");
  await identity("delete-small", "human_dddddddddddddddddddddddddddddddd");
  await db.doc("users/human_dddddddddddddddddddddddddddddddd/trainingPlans/plan").set({ synthetic: true });
  await db.doc("users/human_dddddddddddddddddddddddddddddddd/plannedWorkouts/occurrence").set({ synthetic: true });
  await deleteAuthorizedUserAccount(db, auth, "delete-small");

  await identity("delete-a", humanA);
  await identity("delete-b", humanB);
  await seedChildren(humanA, 405, 805);
  await seedChildren(humanB, 1, 1);

  let interrupted = false;
  await assert.rejects(deleteAuthorizedUserAccount(db, auth, "delete-a", {
    afterBatch: (collection) => {
      if (!interrupted && collection === "plannedWorkouts") {
        interrupted = true;
        throw new Error("synthetic interruption");
      }
    }
  }), /synthetic interruption/);
  assert.strictEqual(await count(`users/${humanA}/trainingPlans`), 0, "completed planner collection reappeared");
  assert.strictEqual((await db.doc(`users/${humanA}`).get()).exists, true, "Human root deleted before children completed");
  assert.strictEqual((await db.doc("accounts/delete-a").get()).exists, true, "forward binding deleted before purge completed");

  const result = await deleteAuthorizedUserAccount(db, auth, "delete-a");
  assert.strictEqual(result.humanUserId, humanA);
  assert.strictEqual(await count(`users/${humanA}/trainingPlans`), 0);
  assert.strictEqual(await count(`users/${humanA}/plannedWorkouts`), 0);
  assert.strictEqual((await db.doc(`users/${humanA}`).get()).exists, false);
  assert.strictEqual((await db.doc("accounts/delete-a").get()).exists, false);
  await assert.rejects(auth.getUser("delete-a"), error => error.code === "auth/user-not-found");

  assert.strictEqual((await db.doc(`users/${humanB}`).get()).exists, true);
  assert.strictEqual(await count(`users/${humanB}/trainingPlans`), 1);
  assert.strictEqual(await count(`users/${humanB}/plannedWorkouts`), 1);
  assert.strictEqual((await auth.getUser("delete-b")).uid, "delete-b");

  await purgeUserCloudData(db, "delete-a", humanA);
  process.stdout.write("S8F-B account deletion emulator harness passed\n");
}

main().then(() => process.exit(0)).catch(error => {
  process.stderr.write(`${error.stack || error}\n`);
  process.exit(1);
});
