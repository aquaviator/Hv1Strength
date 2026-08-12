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
exports.IdentityError = exports.ACTIVE = exports.IDENTITY_SCHEMA_VERSION = void 0;
exports.isValidHumanUserId = isValidHumanUserId;
exports.allocateHumanUserId = allocateHumanUserId;
exports.assertNoClientDeletionTarget = assertNoClientDeletionTarget;
exports.resolveVerifiedLegacyHumanId = resolveVerifiedLegacyHumanId;
exports.ensureHumanIdentityForUid = ensureHumanIdentityForUid;
exports.trustedHumanIdForUid = trustedHumanIdForUid;
const crypto = __importStar(require("crypto"));
const firestore_1 = require("firebase-admin/firestore");
exports.IDENTITY_SCHEMA_VERSION = 1;
exports.ACTIVE = "ACTIVE";
const HUMAN_ID_PATTERN = /^human_[a-z0-9]{8,64}$/;
class IdentityError extends Error {
    constructor(code, message) {
        super(message);
        this.code = code;
    }
}
exports.IdentityError = IdentityError;
function isValidHumanUserId(value) {
    return typeof value === "string" && HUMAN_ID_PATTERN.test(value);
}
function allocateHumanUserId(randomBytes = crypto.randomBytes) {
    return `human_${randomBytes(16).toString("hex")}`;
}
function assertNoClientDeletionTarget(body) {
    if (body && typeof body === "object" && Object.prototype.hasOwnProperty.call(body, "humanUserId")) {
        throw new IdentityError("CLIENT_DELETION_TARGET_FORBIDDEN", "Deletion identity is resolved by the trusted backend");
    }
}
async function resolveVerifiedLegacyHumanId(firestore, uid) {
    const evidence = await firestore.collection("legacyIdentityEvidence").doc(uid).get();
    if (!evidence.exists)
        return null;
    const data = evidence.data();
    if (data?.approved !== true || !isValidHumanUserId(data.humanUserId)) {
        throw new IdentityError("AMBIGUOUS_LEGACY_EVIDENCE", "Legacy identity evidence is not approved or is malformed");
    }
    return data.humanUserId;
}
async function ensureHumanIdentityForUid(firestore, uid, legacyHumanUserId = null, randomBytes = crypto.randomBytes) {
    if (!uid)
        throw new IdentityError("UNAUTHENTICATED", "Firebase authentication is required");
    if (legacyHumanUserId !== null && !isValidHumanUserId(legacyHumanUserId)) {
        throw new IdentityError("AMBIGUOUS_LEGACY_EVIDENCE", "Verified legacy Human identity is malformed");
    }
    const candidate = legacyHumanUserId ?? allocateHumanUserId(randomBytes);
    const accountRef = firestore.collection("accounts").doc(uid);
    return firestore.runTransaction(async (transaction) => {
        const account = await transaction.get(accountRef);
        if (account.exists) {
            const data = account.data();
            const humanUserId = data?.humanUserId;
            if (!isValidHumanUserId(humanUserId) || data?.status !== exports.ACTIVE || data?.schemaVersion !== exports.IDENTITY_SCHEMA_VERSION) {
                throw new IdentityError("MALFORMED_BINDING", "Account identity binding is malformed or unsupported");
            }
            const root = await transaction.get(firestore.collection("users").doc(humanUserId));
            const rootData = root.data();
            if (!root.exists || rootData?.ownerFirebaseUid !== uid || rootData?.status !== exports.ACTIVE ||
                rootData?.schemaVersion !== exports.IDENTITY_SCHEMA_VERSION) {
                throw new IdentityError("BINDING_CONFLICT", "Account and Human identity records do not agree");
            }
            return { humanUserId, status: exports.ACTIVE, schemaVersion: exports.IDENTITY_SCHEMA_VERSION, created: false };
        }
        const rootRef = firestore.collection("users").doc(candidate);
        const root = await transaction.get(rootRef);
        if (root.exists) {
            const data = root.data();
            if (legacyHumanUserId === null || (data?.ownerFirebaseUid && data.ownerFirebaseUid !== uid) ||
                (data?.status && data.status !== exports.ACTIVE))
                throw new IdentityError("HUMAN_ID_COLLISION", "Human identity is allocated");
        }
        const now = firestore_1.FieldValue.serverTimestamp();
        const binding = { schemaVersion: exports.IDENTITY_SCHEMA_VERSION, status: exports.ACTIVE, createdAt: now, updatedAt: now };
        transaction.create(accountRef, { ...binding, humanUserId: candidate });
        const rootBinding = { ...binding, ownerFirebaseUid: uid };
        if (root.exists)
            transaction.set(rootRef, rootBinding, { merge: true });
        else
            transaction.create(rootRef, rootBinding);
        return { humanUserId: candidate, status: exports.ACTIVE, schemaVersion: exports.IDENTITY_SCHEMA_VERSION, created: true };
    });
}
async function trustedHumanIdForUid(firestore, uid) {
    const account = await firestore.collection("accounts").doc(uid).get();
    const data = account.data();
    if (!account.exists || !isValidHumanUserId(data?.humanUserId) || data?.status !== exports.ACTIVE ||
        data?.schemaVersion !== exports.IDENTITY_SCHEMA_VERSION)
        throw new IdentityError("MISSING_BINDING", "Trusted binding is unavailable");
    const root = await firestore.collection("users").doc(data.humanUserId).get();
    if (!root.exists || root.data()?.ownerFirebaseUid !== uid || root.data()?.status !== exports.ACTIVE ||
        root.data()?.schemaVersion !== exports.IDENTITY_SCHEMA_VERSION)
        throw new IdentityError("BINDING_CONFLICT", "Binding records disagree");
    return data.humanUserId;
}
//# sourceMappingURL=identity.js.map