import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { FieldValue } from "firebase-admin/firestore";

export const IDENTITY_SCHEMA_VERSION = 1;
export const ACTIVE = "ACTIVE";
const HUMAN_ID_PATTERN = /^human_[a-z0-9]{8,64}$/;

export class IdentityError extends Error {
  constructor(public readonly code: string, message: string) { super(message); }
}

export function isValidHumanUserId(value: unknown): value is string {
  return typeof value === "string" && HUMAN_ID_PATTERN.test(value);
}

export function allocateHumanUserId(randomBytes: (size: number) => Buffer = crypto.randomBytes): string {
  return `human_${randomBytes(16).toString("hex")}`;
}

export function assertNoClientDeletionTarget(body: unknown): void {
  if (body && typeof body === "object" && Object.prototype.hasOwnProperty.call(body, "humanUserId")) {
    throw new IdentityError("CLIENT_DELETION_TARGET_FORBIDDEN", "Deletion identity is resolved by the trusted backend");
  }
}

export async function resolveVerifiedLegacyHumanId(
  firestore: admin.firestore.Firestore, uid: string
): Promise<string | null> {
  const evidence = await firestore.collection("legacyIdentityEvidence").doc(uid).get();
  if (!evidence.exists) return null;
  const data = evidence.data();
  if (data?.approved !== true || !isValidHumanUserId(data.humanUserId)) {
    throw new IdentityError("AMBIGUOUS_LEGACY_EVIDENCE", "Legacy identity evidence is not approved or is malformed");
  }
  return data.humanUserId;
}

export async function ensureHumanIdentityForUid(
  firestore: admin.firestore.Firestore, uid: string, legacyHumanUserId: string | null = null,
  randomBytes: (size: number) => Buffer = crypto.randomBytes
): Promise<{ humanUserId: string; status: string; schemaVersion: number; created: boolean }> {
  if (!uid) throw new IdentityError("UNAUTHENTICATED", "Firebase authentication is required");
  if (legacyHumanUserId !== null && !isValidHumanUserId(legacyHumanUserId)) {
    throw new IdentityError("AMBIGUOUS_LEGACY_EVIDENCE", "Verified legacy Human identity is malformed");
  }
  const candidate = legacyHumanUserId ?? allocateHumanUserId(randomBytes);
  const accountRef = firestore.collection("accounts").doc(uid);
  return firestore.runTransaction(async transaction => {
    const account = await transaction.get(accountRef);
    if (account.exists) {
      const data = account.data();
      const humanUserId = data?.humanUserId;
      if (!isValidHumanUserId(humanUserId) || data?.status !== ACTIVE || data?.schemaVersion !== IDENTITY_SCHEMA_VERSION) {
        throw new IdentityError("MALFORMED_BINDING", "Account identity binding is malformed or unsupported");
      }
      const root = await transaction.get(firestore.collection("users").doc(humanUserId));
      const rootData = root.data();
      if (!root.exists || rootData?.ownerFirebaseUid !== uid || rootData?.status !== ACTIVE ||
          rootData?.schemaVersion !== IDENTITY_SCHEMA_VERSION) {
        throw new IdentityError("BINDING_CONFLICT", "Account and Human identity records do not agree");
      }
      return { humanUserId, status: ACTIVE, schemaVersion: IDENTITY_SCHEMA_VERSION, created: false };
    }
    const rootRef = firestore.collection("users").doc(candidate);
    const root = await transaction.get(rootRef);
    if (root.exists) {
      const data = root.data();
      if (legacyHumanUserId === null || (data?.ownerFirebaseUid && data.ownerFirebaseUid !== uid) ||
          (data?.status && data.status !== ACTIVE)) throw new IdentityError("HUMAN_ID_COLLISION", "Human identity is allocated");
    }
    const now = FieldValue.serverTimestamp();
    const binding = { schemaVersion: IDENTITY_SCHEMA_VERSION, status: ACTIVE, createdAt: now, updatedAt: now };
    transaction.create(accountRef, { ...binding, humanUserId: candidate });
    const rootBinding = { ...binding, ownerFirebaseUid: uid };
    if (root.exists) transaction.set(rootRef, rootBinding, { merge: true }); else transaction.create(rootRef, rootBinding);
    return { humanUserId: candidate, status: ACTIVE, schemaVersion: IDENTITY_SCHEMA_VERSION, created: true };
  });
}

export async function trustedHumanIdForUid(firestore: admin.firestore.Firestore, uid: string): Promise<string> {
  const account = await firestore.collection("accounts").doc(uid).get();
  const data = account.data();
  if (!account.exists || !isValidHumanUserId(data?.humanUserId) || data?.status !== ACTIVE ||
      data?.schemaVersion !== IDENTITY_SCHEMA_VERSION) throw new IdentityError("MISSING_BINDING", "Trusted binding is unavailable");
  const root = await firestore.collection("users").doc(data.humanUserId).get();
  if (!root.exists || root.data()?.ownerFirebaseUid !== uid || root.data()?.status !== ACTIVE ||
      root.data()?.schemaVersion !== IDENTITY_SCHEMA_VERSION) throw new IdentityError("BINDING_CONFLICT", "Binding records disagree");
  return data.humanUserId;
}
