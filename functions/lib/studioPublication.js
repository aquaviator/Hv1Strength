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
exports.canonicalHash = exports.MAX_PLAN_PLACEMENTS = exports.MAX_PLAN_WEEKS = exports.CANONICAL_PLAN_SCHEMA = exports.CANONICAL_WORKOUT_SCHEMA = exports.STUDIO_DEPENDENCY_SCHEMA = exports.STUDIO_DRAFT_SCHEMA = void 0;
exports.publishStudioWorkoutForUid = publishStudioWorkoutForUid;
exports.publishStudioPlanForUid = publishStudioPlanForUid;
exports.publishStudioPlanCallable = publishStudioPlanCallable;
exports.publishStudioWorkoutCallable = publishStudioWorkoutCallable;
exports.acknowledgeStudioDeliveryForUid = acknowledgeStudioDeliveryForUid;
exports.acknowledgeStudioDeliveryCallable = acknowledgeStudioDeliveryCallable;
const admin = __importStar(require("firebase-admin"));
const crypto = __importStar(require("crypto"));
const https_1 = require("firebase-functions/v2/https");
const identity_1 = require("./identity");
exports.STUDIO_DRAFT_SCHEMA = "humanv1.studio-plan-draft/1";
exports.STUDIO_DEPENDENCY_SCHEMA = "humanv1.studio-plan-draft-dependency/1";
exports.CANONICAL_WORKOUT_SCHEMA = "humanv1.canonical-workout/1";
exports.CANONICAL_PLAN_SCHEMA = "humanv1.canonical-plan/1";
exports.MAX_PLAN_WEEKS = 52;
exports.MAX_PLAN_PLACEMENTS = 200;
const canonical = (value) => {
    if (value === null || typeof value !== "object")
        return JSON.stringify(value);
    if (Array.isArray(value))
        return `[${value.map(canonical).join(",")}]`;
    return `{${Object.keys(value).filter(key => value[key] !== undefined && key !== "checksum").sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(",")}}`;
};
const canonicalHash = (value) => crypto.createHash("sha256").update(canonical(value)).digest("hex");
exports.canonicalHash = canonicalHash;
const text = (value, code) => {
    if (typeof value !== "string" || !value.trim())
        throw new https_1.HttpsError("failed-precondition", code);
    return value;
};
const integer = (value, code) => {
    if (!Number.isInteger(value))
        throw new https_1.HttpsError("failed-precondition", code);
    return value;
};
function canonicalSet(effort, order) {
    const set = { setId: text(effort.effortId, "SET_ID_REQUIRED"), order, restAfterSeconds: effort.restAfterSeconds };
    for (const item of Array.isArray(effort.prescriptions) ? effort.prescriptions : []) {
        if (["repetitions", "repetition_count"].includes(item.metricKey))
            set.repetitions = { minimum: item.minimumValue, target: item.targetValue, maximum: item.maximumValue };
        else if (item.metricKey === "duration")
            set.durationSeconds = item.targetValue;
        else if (item.metricKey === "distance")
            set.distanceMetres = item.targetValue;
        else if (["load", "external_load"].includes(item.metricKey))
            set.load = { value: item.targetValue, unit: item.canonicalUnit === "lb" ? "lb" : "kg" };
        else if (item.metricKey === "rpe")
            set.intensity = { scale: "RPE", target: String(item.targetValue ?? item.textValue ?? "") };
        else if (item.metricKey === "rir")
            set.intensity = { scale: "RIR", target: String(item.targetValue ?? item.textValue ?? "") };
    }
    if (!set.repetitions && set.durationSeconds === undefined && set.distanceMetres === undefined && !set.load && !set.intensity)
        throw new https_1.HttpsError("failed-precondition", "EXECUTION_TARGET_REQUIRED");
    return set;
}
function canonicalWorkout(payload, owner, revision, now) {
    const workoutGlobalId = text(payload.workoutId, "WORKOUT_ID_REQUIRED");
    const blocks = (Array.isArray(payload.blocks) ? payload.blocks : []).flatMap((block, blockOrder) => {
        if (block.type === "REST" || block.type === "NOTE")
            return [];
        if (block.type === "TRANSITION")
            return [{ blockId: text(block.blockId, "BLOCK_ID_REQUIRED"), order: blockOrder, structure: "TRANSITION", placements: [], transition: { from: "MULTISPORT", to: "MULTISPORT", durationSeconds: block.durationSeconds } }];
        const exercises = block.type === "EXERCISE" ? [block] : Array.isArray(block.exercises) ? block.exercises : [];
        if (!exercises.length)
            throw new https_1.HttpsError("failed-precondition", "EXECUTABLE_BLOCK_REQUIRED");
        return [{ blockId: text(block.blockId, "BLOCK_ID_REQUIRED"), order: blockOrder, structure: block.type === "SUPERSET" ? "SUPERSET" : block.type === "CIRCUIT" ? "CIRCUIT" : "STRAIGHT_SETS",
                rounds: block.rounds, placements: exercises.map((exercise, order) => ({ placementId: `${block.blockId}__${text(exercise.exerciseId, "EXERCISE_REFERENCE_REQUIRED")}`,
                    order, exerciseReference: { kind: String(exercise.exerciseId).startsWith("custom_") ? "PRIVATE" : "GOVERNED", exerciseId: exercise.exerciseId },
                    sets: (Array.isArray(exercise.efforts) ? exercise.efforts : []).map(canonicalSet), equipment: [], instructions: exercise.notes })) }];
    });
    if (!blocks.length)
        throw new https_1.HttpsError("failed-precondition", "EXECUTABLE_BLOCK_REQUIRED");
    const discipline = ["STRENGTH", "MOBILITY"].includes(payload.discipline) ? payload.discipline : payload.discipline === "CARDIO" ? "MULTISPORT" : "HYROX";
    const result = { schemaVersion: exports.CANONICAL_WORKOUT_SCHEMA, workoutGlobalId, revision, checksum: "", owner: { humanUserId: owner },
        provenance: { contentClass: "USER_AUTHORED", originApplication: "WORKOUT_STUDIO", sourceId: workoutGlobalId }, discipline,
        workoutType: String(payload.discipline || "WORKOUT"), title: text(payload.title, "WORKOUT_TITLE_REQUIRED"), description: payload.description || "",
        blocks, validationStatus: "VALID", createdAt: now, updatedAt: now, tombstoneState: "ACTIVE", publicationEligibility: "ELIGIBLE" };
    const firestoreSafe = JSON.parse(JSON.stringify(result));
    firestoreSafe.checksum = (0, exports.canonicalHash)(firestoreSafe);
    return firestoreSafe;
}
async function publishStudioWorkoutForUid(db, uid, input) {
    const workoutId = text(input?.workoutId, "WORKOUT_ID_REQUIRED");
    const expectedRevision = integer(input?.expectedRevision, "EXPECTED_REVISION_REQUIRED");
    const idempotencyKey = text(input?.idempotencyKey, "IDEMPOTENCY_KEY_REQUIRED");
    const owner = await (0, identity_1.trustedHumanIdForUid)(db, uid);
    const root = db.collection("users").doc(owner);
    const draftRef = root.collection("workoutDrafts").doc(workoutId);
    const draft = await draftRef.get();
    const envelope = draft.data();
    if (!draft.exists || envelope?.humanUserId !== owner || envelope?.globalId !== workoutId || envelope?.revision !== expectedRevision || envelope?.deletedAt != null)
        throw new https_1.HttpsError("aborted", "WORKOUT_CHANGED_WHILE_PREPARING");
    const payload = canonicalWorkout(envelope.payload, owner, expectedRevision, envelope.updatedAt);
    const checksum = payload.checksum;
    const versions = await root.collection("publishedWorkouts").where("globalId", "==", workoutId).get();
    const existing = versions.docs.find(item => item.data().contentChecksum === checksum && item.data().tombstoneState === "ACTIVE");
    const versionId = existing?.id ?? `${workoutId}_r${expectedRevision}_${checksum.slice(0, 12)}`;
    const now = envelope.updatedAt;
    const receiptRef = root.collection("publicationAudits").doc(idempotencyKey);
    await db.runTransaction(async (transaction) => {
        const [receipt, frozen] = await Promise.all([transaction.get(receiptRef), transaction.get(draftRef)]);
        if (receipt.exists)
            return;
        if (!frozen.exists || (0, exports.canonicalHash)(frozen.data()) !== (0, exports.canonicalHash)(envelope))
            throw new https_1.HttpsError("aborted", "WORKOUT_CHANGED_WHILE_PREPARING");
        if (!existing)
            transaction.create(root.collection("publishedWorkouts").doc(versionId), { schemaVersion: exports.CANONICAL_WORKOUT_SCHEMA, globalId: workoutId, humanUserId: owner,
                revision: expectedRevision, publicationState: "PUBLISHED", tombstoneState: "ACTIVE", sourceDraftId: workoutId, payload, createdAt: now, updatedAt: now, publishedAt: now,
                contentChecksum: checksum, versionId, contentType: "workout", compatibleTags: [payload.discipline] });
        transaction.create(receiptRef, { schemaVersion: 1, humanUserId: owner, workoutId, versionId, checksum, expectedRevision, idempotencyKey, createdAt: admin.firestore.FieldValue.serverTimestamp() });
    });
    return { workoutId, versionId, revision: expectedRevision, checksum, reused: Boolean(existing) };
}
async function publishStudioPlanForUid(db, uid, input, hooks = {}) {
    const planId = text(input?.planId, "PLAN_ID_REQUIRED");
    const expectedRevision = integer(input?.expectedRevision, "EXPECTED_REVISION_REQUIRED");
    const idempotencyKey = text(input?.idempotencyKey, "IDEMPOTENCY_KEY_REQUIRED");
    const owner = await (0, identity_1.trustedHumanIdForUid)(db, uid);
    const root = db.collection("users").doc(owner);
    const [draftSnapshot, dependencySnapshot] = await Promise.all([
        root.collection("planDrafts").doc(planId).get(),
        root.collection("planDraftDependencies").where("planId", "==", planId).get()
    ]);
    if (!draftSnapshot.exists)
        throw new https_1.HttpsError("not-found", "PLAN_DRAFT_NOT_FOUND");
    const envelope = draftSnapshot.data();
    const plan = envelope.payload;
    if (envelope.humanUserId !== owner || envelope.globalId !== planId || envelope.revision !== expectedRevision || plan.schemaVersion !== exports.STUDIO_DRAFT_SCHEMA)
        throw new https_1.HttpsError("aborted", "PLAN_CHANGED_WHILE_PREPARING");
    const weeks = Array.isArray(plan.weeks) ? plan.weeks : [];
    const placements = weeks.flatMap((week) => Array.isArray(week.placements) ? week.placements : []);
    if (!weeks.length || weeks.length > exports.MAX_PLAN_WEEKS || placements.length > exports.MAX_PLAN_PLACEMENTS)
        throw new https_1.HttpsError("failed-precondition", "PLAN_SIZE_INVALID");
    const placementIds = placements.map((item) => text(item.placementId, "PLACEMENT_ID_REQUIRED"));
    if (new Set(placementIds).size !== placementIds.length)
        throw new https_1.HttpsError("failed-precondition", "DUPLICATE_PLACEMENT_ID");
    const dependencies = dependencySnapshot.docs.map(item => item.data()).filter(item => item.deletedAt == null);
    if (dependencies.length !== placements.length || new Set(dependencies.map(item => item.placementId)).size !== placements.length)
        throw new https_1.HttpsError("failed-precondition", "DEPENDENCY_SET_INCOMPLETE");
    const byPlacement = new Map(dependencies.map(item => [item.placementId, item]));
    const resolved = new Map();
    const pending = new Map();
    const frozenSources = [];
    const now = new Date().toISOString();
    for (const placement of placements) {
        const dependency = byPlacement.get(placement.placementId);
        if (!dependency || dependency.humanUserId !== owner || dependency.planId !== planId || dependency.schemaVersion !== exports.STUDIO_DEPENDENCY_SCHEMA)
            throw new https_1.HttpsError("permission-denied", "DEPENDENCY_OWNERSHIP_INVALID");
        if (dependency.dependencyKind === "WORKOUT_DRAFT") {
            const source = await root.collection("workoutDrafts").doc(dependency.referencedStableId).get();
            frozenSources.push(source);
            const draft = source.data();
            if (!source.exists || draft?.humanUserId !== owner || draft?.deletedAt != null || draft?.revision !== dependency.expectedRevision)
                throw new https_1.HttpsError("aborted", "WORKOUT_CHANGED_WHILE_PREPARING");
            const existing = await root.collection("publishedWorkouts").where("globalId", "==", dependency.referencedStableId).get();
            const nextRevision = draft.revision;
            const immutablePayload = canonicalWorkout(draft.payload, owner, nextRevision, draft.updatedAt);
            const checksum = immutablePayload.checksum;
            const match = existing.docs.find(item => item.data().contentChecksum === checksum && item.data().tombstoneState === "ACTIVE");
            const versionId = match?.id ?? `${dependency.referencedStableId}_r${nextRevision}_${checksum.slice(0, 12)}`;
            const publication = match?.data() ?? { schemaVersion: exports.CANONICAL_WORKOUT_SCHEMA, globalId: dependency.referencedStableId, humanUserId: owner, revision: nextRevision,
                publicationState: "PUBLISHED", tombstoneState: "ACTIVE", sourceDraftId: dependency.referencedStableId, payload: immutablePayload, createdAt: now, updatedAt: now,
                publishedAt: now, contentChecksum: checksum, versionId, contentType: "workout", compatibleTags: [draft.payload.discipline] };
            resolved.set(placement.placementId, publication);
            if (!match)
                pending.set(versionId, publication);
        }
        else {
            if (!["PUBLISHED_WORKOUT_VERSION", "GOVERNED_TEMPLATE"].includes(dependency.dependencyKind))
                throw new https_1.HttpsError("failed-precondition", "UNKNOWN_DEPENDENCY_KIND");
            const versionId = text(dependency.immutableVersionId, "IMMUTABLE_VERSION_REQUIRED");
            const snapshot = await root.collection("publishedWorkouts").doc(versionId).get();
            const publication = snapshot.data();
            if (!snapshot.exists || publication?.humanUserId !== owner || publication?.schemaVersion !== exports.CANONICAL_WORKOUT_SCHEMA || publication?.payload?.schemaVersion !== exports.CANONICAL_WORKOUT_SCHEMA || publication?.tombstoneState !== "ACTIVE" || (dependency.immutableChecksum && publication.contentChecksum !== dependency.immutableChecksum))
                throw new https_1.HttpsError("failed-precondition", "IMMUTABLE_DEPENDENCY_INVALID");
            resolved.set(placement.placementId, publication);
        }
    }
    const planRevisionQuery = await root.collection("publishedPlans").where("globalId", "==", planId).get();
    const planRevision = envelope.revision;
    const immutablePlan = { schemaVersion: exports.CANONICAL_PLAN_SCHEMA, planGlobalId: planId, revision: planRevision, checksum: "", owner: { humanUserId: owner },
        provenance: { contentClass: "USER_AUTHORED", originApplication: "WORKOUT_STUDIO", sourceId: planId }, title: text(plan.title, "PLAN_TITLE_REQUIRED"),
        goal: plan.goal ?? plan.description ?? "", athleteLevel: plan.athleteLevel ?? "ALL", durationWeeks: weeks.length,
        timezone: text(plan.timezone, "TIMEZONE_REQUIRED"), startDate: text(plan.startDate, "START_DATE_REQUIRED"), phases: Array.isArray(plan.phases) ? plan.phases : [],
        cycles: Array.isArray(plan.cycles) ? plan.cycles : [], scheduleSchemaVersion: "1.2", publicationEligibility: "ELIGIBLE", validationStatus: "VALID",
        createdAt: envelope.createdAt, updatedAt: envelope.updatedAt, tombstoneState: "ACTIVE",
        weeks: weeks.map((week, weekIndex) => ({ weekId: text(week.weekId, "WEEK_ID_REQUIRED"), order: weekIndex,
            recoveryWeek: Boolean(week.recoveryWeek), placements: week.placements.map((placement, order) => {
                const workout = resolved.get(placement.placementId);
                return {
                    placementId: placement.placementId, order, daySlot: integer(placement.dayOfWeek, "DAY_SLOT_REQUIRED"), workoutGlobalId: workout.globalId,
                    workoutVersionId: workout.versionId, workoutRevision: workout.revision, workoutChecksum: workout.contentChecksum,
                    workoutOwnerHumanUserId: owner, destinationApplication: "HUMAN_STRENGTH",
                    required: placement.required !== false, priority: Boolean(placement.priority), adaptation: placement.adaptation ?? "FIXED",
                    ...(placement.scheduledEpochDay == null ? {} : { scheduledEpochDay: placement.scheduledEpochDay }),
                    preferredMinuteOfDay: placement.preferredMinuteOfDay ?? null, reminderEnabled: Boolean(placement.reminderEnabled), notes: placement.notes ?? ""
                };
            }),
            days: Array.isArray(week.days) ? week.days : [] })),
    };
    immutablePlan.checksum = (0, exports.canonicalHash)(immutablePlan);
    const planChecksum = immutablePlan.checksum;
    const existingPlan = planRevisionQuery.docs.find(item => item.data().contentChecksum === planChecksum && item.data().tombstoneState === "ACTIVE");
    const planVersionId = existingPlan?.id ?? `${planId}_r${planRevision}_${planChecksum.slice(0, 12)}`;
    const receiptRef = root.collection("publicationAudits").doc(idempotencyKey);
    await hooks.beforeCommit?.();
    await db.runTransaction(async (transaction) => {
        const trainingPlanRef = root.collection("trainingPlans").doc(planId);
        const occurrenceRefs = placements.map((placement) => root.collection("plannedWorkouts").doc(`${planId}:${placement.placementId}`));
        const dependencyRefs = dependencySnapshot.docs.map(item => item.ref);
        const [prior, frozenDraft, trainingPlan, ...rest] = await Promise.all([transaction.get(receiptRef), transaction.get(draftSnapshot.ref), transaction.get(trainingPlanRef),
            ...dependencyRefs.map(ref => transaction.get(ref)), ...frozenSources.map(item => transaction.get(item.ref)), ...occurrenceRefs.map(ref => transaction.get(ref))]);
        if (prior.exists)
            return;
        if (!frozenDraft.exists || frozenDraft.data()?.revision !== expectedRevision || (0, exports.canonicalHash)(frozenDraft.data()) !== (0, exports.canonicalHash)(envelope))
            throw new https_1.HttpsError("aborted", "PLAN_CHANGED_WHILE_PREPARING");
        const frozenDependencies = rest.slice(0, dependencyRefs.length);
        const frozenWorkoutDrafts = rest.slice(dependencyRefs.length, dependencyRefs.length + frozenSources.length);
        const occurrences = rest.slice(dependencyRefs.length + frozenSources.length);
        if (frozenDependencies.some((item, index) => !item.exists || (0, exports.canonicalHash)(item.data()) !== (0, exports.canonicalHash)(dependencySnapshot.docs[index].data())))
            throw new https_1.HttpsError("aborted", "DEPENDENCY_CHANGED_WHILE_PREPARING");
        if (frozenWorkoutDrafts.some((item, index) => !item.exists || (0, exports.canonicalHash)(item.data()) !== (0, exports.canonicalHash)(frozenSources[index].data())))
            throw new https_1.HttpsError("aborted", "WORKOUT_CHANGED_WHILE_PREPARING");
        for (const [versionId, publication] of pending)
            transaction.create(root.collection("publishedWorkouts").doc(versionId), publication);
        if (!existingPlan)
            transaction.create(root.collection("publishedPlans").doc(planVersionId), { schemaVersion: exports.CANONICAL_PLAN_SCHEMA, globalId: planId, humanUserId: owner, revision: planRevision,
                publicationState: "PUBLISHED", tombstoneState: "ACTIVE", sourceDraftId: planId, payload: immutablePlan, createdAt: now, updatedAt: now, publishedAt: now,
                contentChecksum: planChecksum, versionId: planVersionId, contentType: "plan", compatibleTags: ["PLAN"] });
        const epoch = Math.floor(Date.parse(`${immutablePlan.startDate}T00:00:00Z`) / 86400000);
        transaction.set(trainingPlanRef, { schemaVersion: 1, globalId: planId, humanUserId: owner, templateGlobalId: resolved.get(placements[0].placementId).globalId,
            routineName: immutablePlan.title, firstEpochDay: epoch, preferredMinuteOfDay: placements[0].preferredMinuteOfDay ?? null,
            weekdaysMask: placements.reduce((mask, item) => mask | (1 << Math.max(0, item.dayOfWeek - 1)), 0), recurrenceEndEpochDay: epoch + weeks.length * 7 - 1,
            createdAt: trainingPlan.data()?.createdAt ?? Date.now(), updatedAt: Date.now(), revision: Number(trainingPlan.data()?.revision ?? 0) + 1, deletedAt: null,
            originDeviceId: "WORKOUT_STUDIO_GOVERNED_PUBLISHER", originApplication: "WORKOUT_STUDIO", extensions: { canonicalPlan: immutablePlan, planVersionId, planChecksum,
                planRevision, workoutVersionIds: [...resolved.values()].map(item => item.versionId).sort(), timezone: immutablePlan.timezone, destinationApplication: "HUMAN_STRENGTH" } });
        placements.forEach((placement, index) => {
            const priorOccurrence = occurrences[index].data();
            if (priorOccurrence && (["COMPLETED", "SKIPPED"].includes(priorOccurrence.status) || priorOccurrence.detachedFromSeries === true))
                return;
            const weekIndex = weeks.findIndex((week) => week.placements.some((item) => item.placementId === placement.placementId));
            const workout = resolved.get(placement.placementId);
            transaction.set(occurrenceRefs[index], { schemaVersion: 1, globalId: `${planId}:${placement.placementId}`, humanUserId: owner, seriesId: planId,
                templateGlobalId: workout.globalId, routineName: immutablePlan.title, scheduledEpochDay: epoch + weekIndex * 7 + Math.max(0, placement.dayOfWeek - 1),
                originalEpochDay: priorOccurrence?.originalEpochDay ?? epoch + weekIndex * 7 + Math.max(0, placement.dayOfWeek - 1), preferredMinuteOfDay: placement.preferredMinuteOfDay ?? null,
                status: "PLANNED", completedAt: null, linkedSessionId: null, reminderEnabled: Boolean(placement.reminderEnabled), detachedFromSeries: false,
                createdAt: priorOccurrence?.createdAt ?? Date.now(), updatedAt: Date.now(), revision: Number(priorOccurrence?.revision ?? 0) + 1, deletedAt: null,
                originDeviceId: "WORKOUT_STUDIO_GOVERNED_PUBLISHER", originApplication: "WORKOUT_STUDIO", extensions: { workoutVersionId: workout.versionId, planVersionId,
                    planChecksum, planRevision, placementId: placement.placementId, notes: placement.notes ?? "", timezone: immutablePlan.timezone } });
        });
        transaction.create(receiptRef, { schemaVersion: 1, humanUserId: owner, planId, planVersionId, planChecksum, expectedRevision, idempotencyKey, createdAt: admin.firestore.FieldValue.serverTimestamp() });
    });
    return { planId, planVersionId, planRevision, planChecksum, workoutVersionIds: [...resolved.values()].map(item => item.versionId), reusedPlan: Boolean(existingPlan) };
}
async function publishStudioPlanCallable(db, request) {
    if (!request.auth?.uid)
        throw new https_1.HttpsError("unauthenticated", "Firebase authentication is required");
    return publishStudioPlanForUid(db, request.auth.uid, request.data);
}
async function publishStudioWorkoutCallable(db, request) {
    if (!request.auth?.uid)
        throw new https_1.HttpsError("unauthenticated", "Firebase authentication is required");
    return publishStudioWorkoutForUid(db, request.auth.uid, request.data);
}
async function acknowledgeStudioDeliveryForUid(db, uid, input) {
    const owner = await (0, identity_1.trustedHumanIdForUid)(db, uid);
    if (!input || !["workout", "plan"].includes(input.entityType) || !["APPLIED", "CONFLICT", "REJECTED"].includes(input.state))
        throw new https_1.HttpsError("invalid-argument", "ACKNOWLEDGEMENT_INVALID");
    const collection = input.entityType === "plan" ? "publishedPlans" : "publishedWorkouts";
    const publication = await db.collection("users").doc(owner).collection(collection).doc(text(input.versionId, "VERSION_ID_REQUIRED")).get();
    const data = publication.data();
    if (!publication.exists || data?.humanUserId !== owner || data?.globalId !== input.globalId || data?.revision !== input.sourceRevision || data?.contentChecksum !== input.checksum || data?.tombstoneState !== "ACTIVE")
        throw new https_1.HttpsError("failed-precondition", "PUBLICATION_MISMATCH");
    const expectedVersions = input.entityType === "plan" ? [...new Set(data.payload?.schemaVersion === exports.CANONICAL_PLAN_SCHEMA
            ? (data.payload?.weeks ?? []).flatMap((week) => (week.placements ?? []).map((placement) => placement.workoutVersionId))
            : (data.payload?.workoutVersionIds ?? []))].sort() : [];
    const receivedVersions = input.entityType === "plan" ? [...new Set(input.workoutVersionIds ?? [])].sort() : [];
    if (canonical(expectedVersions) !== canonical(receivedVersions))
        throw new https_1.HttpsError("failed-precondition", "DEPENDENCY_SET_MISMATCH");
    const targetCollection = input.entityType === "plan" ? "planDeliveryAcks" : "workoutDeliveryAcks";
    const ref = db.collection("users").doc(owner).collection(targetCollection).doc(text(input.acknowledgementId, "ACKNOWLEDGEMENT_ID_REQUIRED"));
    const record = input.entityType === "plan"
        ? { schemaVersion: 1, acknowledgementId: input.acknowledgementId, humanUserId: owner, planGlobalId: input.globalId, planVersionId: input.versionId,
            planChecksum: input.checksum, applicationId: "HUMAN_STRENGTH", sourceRevision: input.sourceRevision, workoutVersionIds: receivedVersions, state: input.state,
            reasonCode: input.reasonCode ?? null, clientAppliedAtMillis: input.clientAppliedAtMillis, createdAt: admin.firestore.FieldValue.serverTimestamp() }
        : { schemaVersion: 1, acknowledgementId: input.acknowledgementId, humanUserId: owner, workoutGlobalId: input.globalId, versionId: input.versionId,
            applicationId: "HUMAN_STRENGTH", appliedChecksum: input.checksum, sourceRevision: input.sourceRevision, state: input.state,
            reasonCode: input.reasonCode ?? null, clientAppliedAtMillis: input.clientAppliedAtMillis, createdAt: admin.firestore.FieldValue.serverTimestamp() };
    await db.runTransaction(async (transaction) => {
        const existing = await transaction.get(ref);
        if (existing.exists) {
            const prior = existing.data();
            if (prior.humanUserId !== owner || (prior.planVersionId ?? prior.versionId) !== input.versionId || (prior.planChecksum ?? prior.appliedChecksum) !== input.checksum || prior.state !== input.state)
                throw new https_1.HttpsError("already-exists", "ACKNOWLEDGEMENT_CONFLICT");
            return;
        }
        transaction.create(ref, record);
    });
    return { acknowledgementId: input.acknowledgementId, state: input.state };
}
async function acknowledgeStudioDeliveryCallable(db, request) {
    if (!request.auth?.uid)
        throw new https_1.HttpsError("unauthenticated", "Firebase authentication is required");
    return acknowledgeStudioDeliveryForUid(db, request.auth.uid, request.data);
}
//# sourceMappingURL=studioPublication.js.map