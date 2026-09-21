import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { ACTIVE, IDENTITY_SCHEMA_VERSION, trustedHumanIdForUid } from "./identity";
import { canonicalHash, MAX_PLAN_PLACEMENTS, MAX_PLAN_WEEKS, STUDIO_DEPENDENCY_SCHEMA, STUDIO_DRAFT_SCHEMA } from "./studioPublication";

type DependencyKind = "WORKOUT_DRAFT" | "PUBLISHED_WORKOUT_VERSION" | "GOVERNED_TEMPLATE";
interface SaveDependency {
  dependencyId: string; placementId: string; dependencyKind: DependencyKind;
  referencedStableId: string; expectedRevision: number | null; expectedUpdatedAt: string | null;
  immutableVersionId: string | null; immutableRevision: number | null; immutableChecksum: string | null;
  immutableSchemaVersion: string | null; displayName: string; provenance: string;
}
export interface SaveStudioPlanDraftRequest {
  planId: string; schemaVersion: typeof STUDIO_DRAFT_SCHEMA; create: boolean; expectedRevision: number | null;
  plan: Record<string, unknown>; dependencies: SaveDependency[]; requestKey: string; clientOperationId: string;
  expectedContentChecksum?: string;
}
export interface SaveStudioPlanDraftResult {
  planId: string; revision: number; contentChecksum: string; status: "SAVED"; idempotent: boolean;
  dependencyCount: number; updatedAt: string;
}
export interface SaveStudioPlanDraftHooks { beforeWrites?: () => void }

const stringValue = (value: unknown, code: string, max = 256): string => {
  if (typeof value !== "string" || !value.trim() || value.length > max || value.includes("/")) throw new HttpsError("invalid-argument", code);
  return value;
};
const optionalInteger = (value: unknown, code: string): number | null => {
  if (value === null) return null;
  if (!Number.isInteger(value) || Number(value) < 1) throw new HttpsError("invalid-argument", code);
  return Number(value);
};
const fail = (code: string, message = code, details: Record<string, unknown> = {}): never => {
  throw new HttpsError("failed-precondition", message, { reason: code, ...details });
};
const dependencyFail = (code: string, dependency: SaveDependency): never => fail(code, code, {
  placementId: dependency.placementId, displayName: dependency.displayName,
  explanation: `${dependency.displayName} changed or is not available. Review this workout in the plan and save again.`,
  correctiveAction: "Review or replace the workout, then save the plan again.", contentPreserved: true
});
const receiptId = (owner: string, planId: string, requestKey: string) =>
  crypto.createHash("sha256").update(`${owner}\n${planId}\n${requestKey}`).digest("hex");

function validateRequest(raw: SaveStudioPlanDraftRequest): { input: SaveStudioPlanDraftRequest; placements: any[]; checksum: string } {
  if (!raw || typeof raw !== "object") throw new HttpsError("invalid-argument", "PLAN_SAVE_REQUIRED");
  const planId = stringValue(raw.planId, "PLAN_ID_INVALID");
  const requestKey = stringValue(raw.requestKey, "REQUEST_KEY_INVALID", 128);
  const clientOperationId = stringValue(raw.clientOperationId, "CLIENT_OPERATION_ID_INVALID", 128);
  if (raw.schemaVersion !== STUDIO_DRAFT_SCHEMA || !raw.plan || typeof raw.plan !== "object" || Array.isArray(raw.plan)) fail("PLAN_SCHEMA_INVALID");
  if ((raw.plan as any).schemaVersion !== STUDIO_DRAFT_SCHEMA || (raw.plan as any).planId !== planId) fail("PLAN_ID_MISMATCH");
  const weeks = (raw.plan as any).weeks;
  if (!Array.isArray(weeks) || weeks.length < 1 || weeks.length > MAX_PLAN_WEEKS) fail("PLAN_SIZE_INVALID");
  const placements = weeks.flatMap((week: any) => Array.isArray(week?.placements) ? week.placements : fail("PLAN_WEEK_INVALID"));
  if (placements.length > MAX_PLAN_PLACEMENTS) fail("PLAN_SIZE_INVALID");
  const placementIds = placements.map((placement: any) => stringValue(placement?.placementId, "PLACEMENT_ID_INVALID"));
  if (new Set(placementIds).size !== placementIds.length) fail("DUPLICATE_PLACEMENT_ID");
  if (!Array.isArray(raw.dependencies) || raw.dependencies.length !== placements.length || raw.dependencies.length > MAX_PLAN_PLACEMENTS) fail("DEPENDENCY_SET_INCOMPLETE");
  const dependencyIds = new Set<string>(); const dependencyPlacements = new Set<string>();
  for (const dependency of raw.dependencies) {
    if (!dependency || typeof dependency !== "object") fail("DEPENDENCY_INVALID");
    const placementId = stringValue(dependency.placementId, "DEPENDENCY_PLACEMENT_INVALID");
    const dependencyId = stringValue(dependency.dependencyId, "DEPENDENCY_ID_INVALID");
    if (dependencyId !== `${planId}__${placementId}` || !placementIds.includes(placementId)) fail("DEPENDENCY_ID_INVALID");
    if (dependencyIds.has(dependencyId) || dependencyPlacements.has(placementId)) fail("DUPLICATE_DEPENDENCY");
    dependencyIds.add(dependencyId); dependencyPlacements.add(placementId);
    if (!["WORKOUT_DRAFT", "PUBLISHED_WORKOUT_VERSION", "GOVERNED_TEMPLATE"].includes(dependency.dependencyKind)) fail("UNKNOWN_DEPENDENCY_KIND");
    stringValue(dependency.referencedStableId, "DEPENDENCY_REFERENCE_INVALID");
    stringValue(dependency.displayName, "DEPENDENCY_DISPLAY_NAME_INVALID", 200);
    stringValue(dependency.provenance, "DEPENDENCY_PROVENANCE_INVALID", 80);
    const placement = placements.find((item: any) => item.placementId === placementId);
    if (!placement || stringValue(placement.workoutId, "PLACEMENT_WORKOUT_INVALID") !== dependency.referencedStableId ||
        !Number.isInteger(placement.dayOfWeek) || placement.dayOfWeek < 1 || placement.dayOfWeek > 7) fail("PLACEMENT_DEPENDENCY_MISMATCH");
    if (dependency.dependencyKind === "WORKOUT_DRAFT" && (!Number.isInteger(dependency.expectedRevision) || Number(dependency.expectedRevision) < 1 ||
      dependency.provenance !== "WORKOUT_STUDIO" || dependency.immutableVersionId !== null || dependency.immutableRevision !== null || dependency.immutableChecksum !== null || dependency.immutableSchemaVersion !== null)) fail("WORKOUT_DEPENDENCY_INVALID");
    if (dependency.dependencyKind === "PUBLISHED_WORKOUT_VERSION" && (dependency.expectedRevision !== null || !dependency.immutableVersionId ||
      !Number.isInteger(dependency.immutableRevision) || !/^[0-9a-f]{64}$/.test(dependency.immutableChecksum ?? "") || dependency.immutableSchemaVersion !== "humanv1.canonical-workout/1")) fail("PUBLISHED_DEPENDENCY_INVALID");
    if (dependency.dependencyKind === "GOVERNED_TEMPLATE" && (dependency.expectedRevision !== null || !dependency.immutableVersionId ||
      !["RESEARCH_CANDIDATE", "GOVERNED_LIBRARY"].includes(dependency.provenance))) fail("GOVERNED_DEPENDENCY_INVALID");
  }
  if (placementIds.some((id: string) => !dependencyPlacements.has(id))) fail("DEPENDENCY_SET_INCOMPLETE");
  const expectedRevision = optionalInteger(raw.expectedRevision, "EXPECTED_REVISION_INVALID");
  if ((raw.create && expectedRevision !== null) || (!raw.create && expectedRevision === null)) fail("SAVE_INTENT_INVALID");
  const canonicalContent = { schemaVersion: raw.schemaVersion, planId, create: raw.create, expectedRevision, plan: raw.plan,
    dependencies: [...raw.dependencies].sort((a, b) => a.dependencyId.localeCompare(b.dependencyId)) };
  const checksum = canonicalHash(canonicalContent);
  if (raw.expectedContentChecksum !== undefined && raw.expectedContentChecksum !== checksum) fail("CONTENT_CHECKSUM_MISMATCH");
  const encodedBytes = Buffer.byteLength(JSON.stringify(canonicalContent), "utf8");
  if (encodedBytes > 800_000) throw new HttpsError("invalid-argument", "PLAN_SAVE_TOO_LARGE");
  return { input: { ...raw, planId, requestKey, clientOperationId, expectedRevision }, placements, checksum };
}

function assertExecutableWorkout(envelope: any, owner: string, dependency: SaveDependency): void {
  if (!envelope || envelope.humanUserId !== owner || envelope.globalId !== dependency.referencedStableId) dependencyFail("WORKOUT_DEPENDENCY_UNAVAILABLE", dependency);
  if (envelope.deletedAt != null || envelope.status !== "DRAFT") dependencyFail("WORKOUT_DEPENDENCY_ARCHIVED", dependency);
  if (envelope.revision !== dependency.expectedRevision) dependencyFail("WORKOUT_REVISION_STALE", dependency);
  const blocks = envelope.payload?.blocks;
  const executable = Array.isArray(blocks) && blocks.some((block: any) =>
    block?.type === "TRANSITION" || (block?.type === "EXERCISE" && Array.isArray(block.efforts) && block.efforts.length > 0) ||
    (["SUPERSET", "CIRCUIT"].includes(block?.type) && Array.isArray(block.exercises) && block.exercises.some((item: any) => Array.isArray(item.efforts) && item.efforts.length > 0)));
  if (!executable || (Array.isArray(envelope.payload?.reconstructionDiagnostics) && envelope.payload.reconstructionDiagnostics.some((item: any) => item?.severity === "BLOCKING"))) dependencyFail("WORKOUT_CONTENT_INVALID", dependency);
}

export async function saveStudioPlanDraftForUid(db: admin.firestore.Firestore, uid: string, raw: SaveStudioPlanDraftRequest,
  hooks: SaveStudioPlanDraftHooks = {}): Promise<SaveStudioPlanDraftResult> {
  if (!uid) throw new HttpsError("unauthenticated", "Firebase authentication is required");
  const { input, checksum } = validateRequest(raw);
  const owner = await trustedHumanIdForUid(db, uid);
  const root = db.collection("users").doc(owner); const planRef = root.collection("planDrafts").doc(input.planId);
  const auditRef = root.collection("planDraftSaveAudits").doc(receiptId(owner, input.planId, input.requestKey));
  const dependencyRefs = input.dependencies.map(item => root.collection("planDraftDependencies").doc(item.dependencyId));
  const targetRefs = input.dependencies.map(item => item.dependencyKind === "WORKOUT_DRAFT"
    ? root.collection("workoutDrafts").doc(item.referencedStableId)
    : item.dependencyKind === "PUBLISHED_WORKOUT_VERSION"
      ? root.collection("publishedWorkouts").doc(stringValue(item.immutableVersionId, "IMMUTABLE_VERSION_REQUIRED"))
      : db.collection("governedWorkoutTemplates").doc(stringValue(item.immutableVersionId, "GOVERNED_VERSION_REQUIRED")));
  return db.runTransaction(async transaction => {
    const accountRef = db.collection("accounts").doc(uid); const entitlementRef = accountRef.collection("entitlements").doc("current");
    const existingDependenciesQuery = root.collection("planDraftDependencies").where("planId", "==", input.planId);
    const [account, rootSnapshot, entitlement, planSnapshot, auditSnapshot, oldDependencies, ...targets] = await Promise.all([
      transaction.get(accountRef), transaction.get(root), transaction.get(entitlementRef), transaction.get(planRef), transaction.get(auditRef),
      transaction.get(existingDependenciesQuery), ...targetRefs.map(ref => transaction.get(ref))
    ]);
    if (!account.exists || account.data()?.humanUserId !== owner || account.data()?.status !== ACTIVE || account.data()?.schemaVersion !== IDENTITY_SCHEMA_VERSION ||
        !rootSnapshot.exists || rootSnapshot.data()?.ownerFirebaseUid !== uid || rootSnapshot.data()?.status !== ACTIVE || rootSnapshot.data()?.schemaVersion !== IDENTITY_SCHEMA_VERSION) {
      fail("OWNER_BINDING_INVALID");
    }
    const access = entitlement.data(); const expiryMillis = access?.expiryAt?.toMillis?.() ?? Date.parse(access?.expiryAt ?? "");
    if (!entitlement.exists || access?.humanUserId !== owner || access?.firebaseUid !== uid || access?.productScope !== "WORKOUT_STUDIO" ||
      !["ACTIVE", "ACTIVE_UNTIL_EXPIRY"].includes(access?.normalizedState) || (access.normalizedState === "ACTIVE_UNTIL_EXPIRY" && (!Number.isFinite(expiryMillis) || expiryMillis <= Date.now()))) fail("STUDIO_ACCESS_REQUIRED");
    if (auditSnapshot.exists) {
      const receipt = auditSnapshot.data()!;
      if (receipt.contentChecksum !== checksum || receipt.expectedRevision !== input.expectedRevision) fail("IDEMPOTENCY_KEY_REUSED");
      return { planId: input.planId, revision: receipt.resultRevision, contentChecksum: checksum, status: "SAVED", idempotent: true,
        dependencyCount: receipt.dependencyCount, updatedAt: receipt.updatedAt };
    }
    if (input.create) { if (planSnapshot.exists) fail("PLAN_ALREADY_EXISTS"); }
    else if (!planSnapshot.exists || planSnapshot.data()?.humanUserId !== owner || planSnapshot.data()?.globalId !== input.planId || planSnapshot.data()?.deletedAt != null ||
      planSnapshot.data()?.revision !== input.expectedRevision) fail("PLAN_REVISION_STALE");
    input.dependencies.forEach((dependency, index) => {
      const target = targets[index]; const data = target.data();
      if (dependency.dependencyKind === "WORKOUT_DRAFT") {
        if (dependency.expectedRevision === null || dependency.immutableVersionId !== null || dependency.immutableRevision !== null || dependency.immutableChecksum !== null) fail("WORKOUT_DEPENDENCY_INVALID");
        assertExecutableWorkout(data, owner, dependency);
      } else if (dependency.dependencyKind === "PUBLISHED_WORKOUT_VERSION") {
        if (!target.exists || data?.humanUserId !== owner || data?.globalId !== dependency.referencedStableId || data?.versionId !== dependency.immutableVersionId ||
          data?.revision !== dependency.immutableRevision || data?.contentChecksum !== dependency.immutableChecksum || data?.schemaVersion !== dependency.immutableSchemaVersion || data?.tombstoneState !== "ACTIVE") dependencyFail("PUBLISHED_DEPENDENCY_UNAVAILABLE", dependency);
      } else if (!target.exists || data?.templateId !== dependency.referencedStableId || data?.versionId !== dependency.immutableVersionId || data?.status !== "ACTIVE" ||
        data?.schemaVersion !== "humanv1.governed-workout-template/1") dependencyFail("GOVERNED_DEPENDENCY_UNAVAILABLE", dependency);
    });
    const now = new Date().toISOString(); const nextRevision = input.create ? 1 : Number(input.expectedRevision) + 1;
    const createdAt = input.create ? now : planSnapshot.data()!.createdAt;
    const normalizedPlan = JSON.parse(JSON.stringify(input.plan));
    normalizedPlan.dependencyOwnerHumanUserId = owner; normalizedPlan.dependencyCount = input.dependencies.length;
    normalizedPlan.dependencyStorageVersion = 1; normalizedPlan.dependencyKinds = [...new Set(input.dependencies.map(item => item.dependencyKind))].sort();
    const byPlacement = new Map(input.dependencies.map(item => [item.placementId, item]));
    for (const week of normalizedPlan.weeks) for (const placement of week.placements) {
      const dependency = byPlacement.get(placement.placementId)!;
      placement.dependency = dependency.dependencyKind === "WORKOUT_DRAFT"
        ? { kind: "WORKOUT_DRAFT", workoutDraftId: dependency.referencedStableId, humanUserId: owner, expectedRevision: dependency.expectedRevision,
            expectedUpdatedAt: dependency.expectedUpdatedAt, displayName: dependency.displayName, originApplication: "WORKOUT_STUDIO" }
        : dependency.dependencyKind === "PUBLISHED_WORKOUT_VERSION"
          ? { kind: "PUBLISHED_WORKOUT_VERSION", workoutGlobalId: dependency.referencedStableId, versionId: dependency.immutableVersionId,
              revision: dependency.immutableRevision, checksum: dependency.immutableChecksum, schemaVersion: dependency.immutableSchemaVersion, displayName: dependency.displayName }
          : { kind: "GOVERNED_TEMPLATE", templateId: dependency.referencedStableId, immutableVersionId: dependency.immutableVersionId,
              displayName: dependency.displayName, provenance: dependency.provenance };
    }
    const newIds = new Set(input.dependencies.map(item => item.dependencyId));
    hooks.beforeWrites?.();
    oldDependencies.docs.filter(item => !newIds.has(item.id)).forEach(item => transaction.delete(item.ref));
    input.dependencies.forEach((dependency, index) => transaction.set(dependencyRefs[index], {
      ...dependency, schemaVersion: STUDIO_DEPENDENCY_SCHEMA, humanUserId: owner, planId: input.planId, revision: nextRevision,
      createdAt: oldDependencies.docs.find(item => item.id === dependency.dependencyId)?.data().createdAt ?? now, updatedAt: now, deletedAt: null
    }));
    transaction.set(planRef, { schemaVersion: 1, globalId: input.planId, humanUserId: owner, revision: nextRevision, status: "DRAFT", payload: normalizedPlan,
      contentChecksum: checksum, createdAt, updatedAt: now, deletedAt: null, originClientId: input.clientOperationId });
    transaction.create(auditRef, { schemaVersion: 1, humanUserId: owner, planId: input.planId, requestKey: input.requestKey, expectedRevision: input.expectedRevision,
      resultRevision: nextRevision, contentChecksum: checksum, dependencyCount: input.dependencies.length, clientOperationId: input.clientOperationId, updatedAt: now,
      createdAt: admin.firestore.FieldValue.serverTimestamp() });
    return { planId: input.planId, revision: nextRevision, contentChecksum: checksum, status: "SAVED", idempotent: false,
      dependencyCount: input.dependencies.length, updatedAt: now };
  });
}

export async function saveStudioPlanDraftCallable(db: admin.firestore.Firestore, request: CallableRequest<SaveStudioPlanDraftRequest>) {
  if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Firebase authentication is required");
  return saveStudioPlanDraftForUid(db, request.auth.uid, request.data);
}
