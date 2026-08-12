"use strict";

if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  throw new Error("S8F-A guard: Firestore and Auth emulator hosts are required");
}
if (process.env.GCLOUD_PROJECT === "hv1-platform") {
  throw new Error("S8F-A guard: production Firebase project is forbidden");
}

const admin = require("firebase-admin");
admin.initializeApp({ projectId: "demo-hv1-planner-sync" });

const HUMAN_A = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
const HUMAN_B = "human_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
const users = [
  { uid: "planner-client-owner", email: "owner@example.invalid", human: HUMAN_A },
  { uid: "planner-other-owner", email: "other@example.invalid", human: HUMAN_B }
];

async function main() {
  const auth = admin.auth();
  const db = admin.firestore();
  for (const user of users) {
    await auth.createUser({ uid: user.uid, email: user.email, password: "local-only-password" });
    await db.doc(`accounts/${user.uid}`).set({ humanUserId: user.human, status: "ACTIVE", schemaVersion: 1 });
    await db.doc(`users/${user.human}`).set({ ownerFirebaseUid: user.uid, status: "ACTIVE", schemaVersion: 1 });
  }
  process.stdout.write("S8F-A synthetic emulator identity seeded\n");
}

main().then(() => process.exit(0)).catch(error => {
  process.stderr.write(`${error.stack || error}\n`);
  process.exit(1);
});
