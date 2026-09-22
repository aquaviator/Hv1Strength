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
exports.saveStudioPlanDraftForUid = saveStudioPlanDraftForUid;
exports.saveStudioPlanDraftCallable = saveStudioPlanDraftCallable;
const firestore_1 = require("firebase-admin/firestore");
const crypto = __importStar(require("crypto"));
const https_1 = require("firebase-functions/v2/https");
const identity_1 = require("./identity");
const studioPublication_1 = require("./studioPublication");
const stringValue = (value, code, max = 256) => {
    if (typeof value !== "string" || !value.trim() || value.length > max || value.includes("/"))
        throw new https_1.HttpsError("invalid-argument", code);
    return value;
};
const optionalInteger = (value, code) => {
    if (value === null)
        return null;
    if (!Number.isInteger(value) || Number(value) < 1)
        throw new https_1.HttpsError("invalid-argument", code);
    return Number(value);
};
const fail = (code, message = code, details = {}) => {
    throw new https_1.HttpsError("failed-precondition", message, { reason: code, ...details });
};
const dependencyFail = (code, dependency) => fail(code, code, {
    placementId: dependency.placementId, displayName: dependency.displayName,
    explanation: `${dependency.displayName} changed or is not available. Review this workout in the plan and save again.`,
    correctiveAction: "Review or replace the workout, then save the plan again.", contentPreserved: true
});
const receiptId = (owner, planId, requestKey) => crypto.createHash("sha256").update(`${owner}\n${planId}\n${requestKey}`).digest("hex");
function validateRequest(raw) {
    if (!raw || typeof raw !== "object")
        throw new https_1.HttpsError("invalid-argument", "PLAN_SAVE_REQUIRED");
    const planId = stringValue(raw.planId, "PLAN_ID_INVALID");
    const requestKey = stringValue(raw.requestKey, "REQUEST_KEY_INVALID", 128);
    const clientOperationId = stringValue(raw.clientOperationId, "CLIENT_OPERATION_ID_INVALID", 128);
    if (raw.schemaVersion !== studioPublication_1.STUDIO_DRAFT_SCHEMA || !raw.plan || typeof raw.plan !== "object" || Array.isArray(raw.plan))
        fail("PLAN_SCHEMA_INVALID");
    if (raw.plan.schemaVersion !== studioPublication_1.STUDIO_DRAFT_SCHEMA || raw.plan.planId !== planId)
        fail("PLAN_ID_MISMATCH");
    const weeks = raw.plan.weeks;
    if (!Array.isArray(weeks) || weeks.length < 1 || weeks.length > studioPublication_1.MAX_PLAN_WEEKS)
        fail("PLAN_SIZE_INVALID");
    const placements = weeks.flatMap((week) => Array.isArray(week?.placements) ? week.placements : fail("PLAN_WEEK_INVALID"));
    if (placements.length > studioPublication_1.MAX_PLAN_PLACEMENTS)
        fail("PLAN_SIZE_INVALID");
    const placementIds = placements.map((placement) => stringValue(placement?.placementId, "PLACEMENT_ID_INVALID"));
    if (new Set(placementIds).size !== placementIds.length)
        fail("DUPLICATE_PLACEMENT_ID");
    if (!Array.isArray(raw.dependencies) || raw.dependencies.length !== placements.length || raw.dependencies.length > studioPublication_1.MAX_PLAN_PLACEMENTS)
        fail("DEPENDENCY_SET_INCOMPLETE");
    const dependencyIds = new Set();
    const dependencyPlacements = new Set();
    for (const dependency of raw.dependencies) {
        if (!dependency || typeof dependency !== "object")
            fail("DEPENDENCY_INVALID");
        const placementId = stringValue(dependency.placementId, "DEPENDENCY_PLACEMENT_INVALID");
        const dependencyId = stringValue(dependency.dependencyId, "DEPENDENCY_ID_INVALID");
        if (dependencyId !== `${planId}__${placementId}` || !placementIds.includes(placementId))
            fail("DEPENDENCY_ID_INVALID");
        if (dependencyIds.has(dependencyId) || dependencyPlacements.has(placementId))
            fail("DUPLICATE_DEPENDENCY");
        dependencyIds.add(dependencyId);
        dependencyPlacements.add(placementId);
        if (!["WORKOUT_DRAFT", "PUBLISHED_WORKOUT_VERSION", "GOVERNED_TEMPLATE"].includes(dependency.dependencyKind))
            fail("UNKNOWN_DEPENDENCY_KIND");
        stringValue(dependency.referencedStableId, "DEPENDENCY_REFERENCE_INVALID");
        stringValue(dependency.displayName, "DEPENDENCY_DISPLAY_NAME_INVALID", 200);
        stringValue(dependency.provenance, "DEPENDENCY_PROVENANCE_INVALID", 80);
        const placement = placements.find((item) => item.placementId === placementId);
        if (!placement || stringValue(placement.workoutId, "PLACEMENT_WORKOUT_INVALID") !== dependency.referencedStableId ||
            !Number.isInteger(placement.dayOfWeek) || placement.dayOfWeek < 1 || placement.dayOfWeek > 7)
            fail("PLACEMENT_DEPENDENCY_MISMATCH");
        if (dependency.dependencyKind === "WORKOUT_DRAFT" && (!Number.isInteger(dependency.expectedRevision) || Number(dependency.expectedRevision) < 1 ||
            dependency.provenance !== "WORKOUT_STUDIO" || dependency.immutableVersionId !== null || dependency.immutableRevision !== null || dependency.immutableChecksum !== null || dependency.immutableSchemaVersion !== null))
            fail("WORKOUT_DEPENDENCY_INVALID");
        if (dependency.dependencyKind === "PUBLISHED_WORKOUT_VERSION" && (dependency.expectedRevision !== null || !dependency.immutableVersionId ||
            !Number.isInteger(dependency.immutableRevision) || !/^[0-9a-f]{64}$/.test(dependency.immutableChecksum ?? "") || dependency.immutableSchemaVersion !== "humanv1.canonical-workout/1"))
            fail("PUBLISHED_DEPENDENCY_INVALID");
        if (dependency.dependencyKind === "GOVERNED_TEMPLATE" && (dependency.expectedRevision !== null || !dependency.immutableVersionId ||
            !["RESEARCH_CANDIDATE", "GOVERNED_LIBRARY"].includes(dependency.provenance)))
            fail("GOVERNED_DEPENDENCY_INVALID");
    }
    if (placementIds.some((id) => !dependencyPlacements.has(id)))
        fail("DEPENDENCY_SET_INCOMPLETE");
    const expectedRevision = optionalInteger(raw.expectedRevision, "EXPECTED_REVISION_INVALID");
    if ((raw.create && expectedRevision !== null) || (!raw.create && expectedRevision === null))
        fail("SAVE_INTENT_INVALID");
    const canonicalContent = { schemaVersion: raw.schemaVersion, planId, create: raw.create, expectedRevision, plan: raw.plan,
        dependencies: [...raw.dependencies].sort((a, b) => a.dependencyId.localeCompare(b.dependencyId)) };
    const checksum = (0, studioPublication_1.canonicalHash)(canonicalContent);
    if (raw.expectedContentChecksum !== undefined && raw.expectedContentChecksum !== checksum)
        fail("CONTENT_CHECKSUM_MISMATCH");
    const encodedBytes = Buffer.byteLength(JSON.stringify(canonicalContent), "utf8");
    if (encodedBytes > 800_000)
        throw new https_1.HttpsError("invalid-argument", "PLAN_SAVE_TOO_LARGE");
    return { input: { ...raw, planId, requestKey, clientOperationId, expectedRevision }, placements, checksum };
}
function assertExecutableWorkout(envelope, owner, dependency) {
    if (!envelope || envelope.humanUserId !== owner || envelope.globalId !== dependency.referencedStableId)
        dependencyFail("WORKOUT_DEPENDENCY_UNAVAILABLE", dependency);
    if (envelope.deletedAt != null || envelope.status !== "DRAFT")
        dependencyFail("WORKOUT_DEPENDENCY_ARCHIVED", dependency);
    if (envelope.revision !== dependency.expectedRevision)
        dependencyFail("WORKOUT_REVISION_STALE", dependency);
    const blocks = envelope.payload?.blocks;
    const executable = Array.isArray(blocks) && blocks.some((block) => block?.type === "TRANSITION" || (block?.type === "EXERCISE" && Array.isArray(block.efforts) && block.efforts.length > 0) ||
        (["SUPERSET", "CIRCUIT"].includes(block?.type) && Array.isArray(block.exercises) && block.exercises.some((item) => Array.isArray(item.efforts) && item.efforts.length > 0)));
    if (!executable || (Array.isArray(envelope.payload?.reconstructionDiagnostics) && envelope.payload.reconstructionDiagnostics.some((item) => item?.severity === "BLOCKING")))
        dependencyFail("WORKOUT_CONTENT_INVALID", dependency);
}
async function saveStudioPlanDraftForUid(db, uid, raw, hooks = {}) {
    if (!uid)
        throw new https_1.HttpsError("unauthenticated", "Firebase authentication is required");
    const { input, checksum } = validateRequest(raw);
    const owner = await (0, identity_1.trustedHumanIdForUid)(db, uid);
    const root = db.collection("users").doc(owner);
    const planRef = root.collection("planDrafts").doc(input.planId);
    const auditRef = root.collection("planDraftSaveAudits").doc(receiptId(owner, input.planId, input.requestKey));
    const dependencyRefs = input.dependencies.map(item => root.collection("planDraftDependencies").doc(item.dependencyId));
    const targetRefs = input.dependencies.map(item => item.dependencyKind === "WORKOUT_DRAFT"
        ? root.collection("workoutDrafts").doc(item.referencedStableId)
        : item.dependencyKind === "PUBLISHED_WORKOUT_VERSION"
            ? root.collection("publishedWorkouts").doc(stringValue(item.immutableVersionId, "IMMUTABLE_VERSION_REQUIRED"))
            : db.collection("governedWorkoutTemplates").doc(stringValue(item.immutableVersionId, "GOVERNED_VERSION_REQUIRED")));
    return db.runTransaction(async (transaction) => {
        const accountRef = db.collection("accounts").doc(uid);
        const entitlementRef = accountRef.collection("entitlements").doc("current");
        const existingDependenciesQuery = root.collection("planDraftDependencies").where("planId", "==", input.planId);
        const [account, rootSnapshot, entitlement, planSnapshot, auditSnapshot, oldDependencies, ...targets] = await Promise.all([
            transaction.get(accountRef), transaction.get(root), transaction.get(entitlementRef), transaction.get(planRef), transaction.get(auditRef),
            transaction.get(existingDependenciesQuery), ...targetRefs.map(ref => transaction.get(ref))
        ]);
        if (!account.exists || account.data()?.humanUserId !== owner || account.data()?.status !== identity_1.ACTIVE || account.data()?.schemaVersion !== identity_1.IDENTITY_SCHEMA_VERSION ||
            !rootSnapshot.exists || rootSnapshot.data()?.ownerFirebaseUid !== uid || rootSnapshot.data()?.status !== identity_1.ACTIVE || rootSnapshot.data()?.schemaVersion !== identity_1.IDENTITY_SCHEMA_VERSION) {
            fail("OWNER_BINDING_INVALID");
        }
        const access = entitlement.data();
        const expiryMillis = access?.expiryAt?.toMillis?.() ?? Date.parse(access?.expiryAt ?? "");
        if (!entitlement.exists || access?.humanUserId !== owner || access?.firebaseUid !== uid || access?.productScope !== "WORKOUT_STUDIO" ||
            !["ACTIVE", "ACTIVE_UNTIL_EXPIRY"].includes(access?.normalizedState) || (access.normalizedState === "ACTIVE_UNTIL_EXPIRY" && (!Number.isFinite(expiryMillis) || expiryMillis <= Date.now())))
            fail("STUDIO_ACCESS_REQUIRED");
        if (auditSnapshot.exists) {
            const receipt = auditSnapshot.data();
            if (receipt.contentChecksum !== checksum || receipt.expectedRevision !== input.expectedRevision)
                fail("IDEMPOTENCY_KEY_REUSED");
            return { planId: input.planId, revision: receipt.resultRevision, contentChecksum: checksum, status: "SAVED", idempotent: true,
                dependencyCount: receipt.dependencyCount, updatedAt: receipt.updatedAt };
        }
        if (input.create) {
            if (planSnapshot.exists)
                fail("PLAN_ALREADY_EXISTS");
        }
        else if (!planSnapshot.exists || planSnapshot.data()?.humanUserId !== owner || planSnapshot.data()?.globalId !== input.planId || planSnapshot.data()?.deletedAt != null ||
            planSnapshot.data()?.revision !== input.expectedRevision)
            fail("PLAN_REVISION_STALE");
        input.dependencies.forEach((dependency, index) => {
            const target = targets[index];
            const data = target.data();
            if (dependency.dependencyKind === "WORKOUT_DRAFT") {
                if (dependency.expectedRevision === null || dependency.immutableVersionId !== null || dependency.immutableRevision !== null || dependency.immutableChecksum !== null)
                    fail("WORKOUT_DEPENDENCY_INVALID");
                assertExecutableWorkout(data, owner, dependency);
            }
            else if (dependency.dependencyKind === "PUBLISHED_WORKOUT_VERSION") {
                if (!target.exists || data?.humanUserId !== owner || data?.globalId !== dependency.referencedStableId || data?.versionId !== dependency.immutableVersionId ||
                    data?.revision !== dependency.immutableRevision || data?.contentChecksum !== dependency.immutableChecksum || data?.schemaVersion !== dependency.immutableSchemaVersion || data?.tombstoneState !== "ACTIVE")
                    dependencyFail("PUBLISHED_DEPENDENCY_UNAVAILABLE", dependency);
            }
            else if (!target.exists || data?.templateId !== dependency.referencedStableId || data?.versionId !== dependency.immutableVersionId || data?.status !== "ACTIVE" ||
                data?.schemaVersion !== "humanv1.governed-workout-template/1")
                dependencyFail("GOVERNED_DEPENDENCY_UNAVAILABLE", dependency);
        });
        const now = new Date().toISOString();
        const nextRevision = input.create ? 1 : Number(input.expectedRevision) + 1;
        const createdAt = input.create ? now : planSnapshot.data().createdAt;
        const normalizedPlan = JSON.parse(JSON.stringify(input.plan));
        normalizedPlan.dependencyOwnerHumanUserId = owner;
        normalizedPlan.dependencyCount = input.dependencies.length;
        normalizedPlan.dependencyStorageVersion = 1;
        normalizedPlan.dependencyKinds = [...new Set(input.dependencies.map(item => item.dependencyKind))].sort();
        const byPlacement = new Map(input.dependencies.map(item => [item.placementId, item]));
        for (const week of normalizedPlan.weeks)
            for (const placement of week.placements) {
                const dependency = byPlacement.get(placement.placementId);
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
            ...dependency, schemaVersion: studioPublication_1.STUDIO_DEPENDENCY_SCHEMA, humanUserId: owner, planId: input.planId, revision: nextRevision,
            createdAt: oldDependencies.docs.find(item => item.id === dependency.dependencyId)?.data().createdAt ?? now, updatedAt: now, deletedAt: null
        }));
        transaction.set(planRef, { schemaVersion: 1, globalId: input.planId, humanUserId: owner, revision: nextRevision, status: "DRAFT", payload: normalizedPlan,
            contentChecksum: checksum, createdAt, updatedAt: now, deletedAt: null, originClientId: input.clientOperationId });
        transaction.create(auditRef, { schemaVersion: 1, humanUserId: owner, planId: input.planId, requestKey: input.requestKey, expectedRevision: input.expectedRevision,
            resultRevision: nextRevision, contentChecksum: checksum, dependencyCount: input.dependencies.length, clientOperationId: input.clientOperationId, updatedAt: now,
            createdAt: firestore_1.FieldValue.serverTimestamp() });
        return { planId: input.planId, revision: nextRevision, contentChecksum: checksum, status: "SAVED", idempotent: false,
            dependencyCount: input.dependencies.length, updatedAt: now };
    });
}
async function saveStudioPlanDraftCallable(db, request) {
    if (!request.auth?.uid)
        throw new https_1.HttpsError("unauthenticated", "Firebase authentication is required");
    return saveStudioPlanDraftForUid(db, request.auth.uid, request.data);
}
//# sourceMappingURL=studioPlanDraft.js.map