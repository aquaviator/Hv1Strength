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
exports.rtdnHandler = exports.verifyPurchase = exports.FUNCTION_REGION = exports.EXPECTED_PRODUCT_ID = exports.EXPECTED_PACKAGE_NAME = void 0;
exports.getPurchaseDocId = getPurchaseDocId;
exports.getPlayDeveloperClient = getPlayDeveloperClient;
exports.mapPlayStateToEntitlementStatus = mapPlayStateToEntitlementStatus;
exports.verifyTokenWithGooglePlay = verifyTokenWithGooglePlay;
const https_1 = require("firebase-functions/v2/https");
const pubsub_1 = require("firebase-functions/v2/pubsub");
const logger = __importStar(require("firebase-functions/logger"));
const admin = __importStar(require("firebase-admin"));
const googleapis_1 = require("googleapis");
const crypto = __importStar(require("crypto"));
if (!admin.apps.length) {
    admin.initializeApp();
}
const db = admin.firestore();
exports.EXPECTED_PACKAGE_NAME = "com.aistudio.humanstrength.kfqjza";
exports.EXPECTED_PRODUCT_ID = "human_strength_annual";
exports.FUNCTION_REGION = "europe-west1";
/**
 * Creates a cryptographically secure one-way hash for purchase token doc IDs.
 * Protects sensitive purchase token data by using SHA-256 without exposing raw token material.
 */
function getPurchaseDocId(purchaseToken) {
    if (!purchaseToken) {
        return "play_unknown";
    }
    const hash = crypto.createHash("sha256").update(purchaseToken).digest("hex");
    return `play_${hash.substring(0, 32)}`;
}
/**
 * Creates an authorized Google Play Developer API client if service credentials exist.
 */
async function getPlayDeveloperClient() {
    try {
        const auth = new googleapis_1.google.auth.GoogleAuth({
            scopes: ["https://www.googleapis.com/auth/androidpublisher"]
        });
        return googleapis_1.google.androidpublisher({ version: "v3", auth });
    }
    catch (error) {
        logger.warn("Google Auth initialization failed for Play Developer API:", error?.message || error);
        return null;
    }
}
/**
 * Maps raw Google Play subscription state and offer metadata into entitlement status.
 * Fails CLOSED on unknown states by returning EXPIRED rather than granting access.
 */
function mapPlayStateToEntitlementStatus(subscriptionState, expiryMillis, isTrial, nowMillis = Date.now()) {
    switch (subscriptionState) {
        case "SUBSCRIPTION_STATE_ACTIVE":
            return expiryMillis <= nowMillis ? "EXPIRED" : (isTrial ? "TRIAL_ACTIVE" : "ACTIVE");
        case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
            return "GRACE_PERIOD";
        case "SUBSCRIPTION_STATE_ON_HOLD":
            return "ACCOUNT_HOLD";
        case "SUBSCRIPTION_STATE_PAUSED":
            return "PAUSED";
        case "SUBSCRIPTION_STATE_CANCELED":
            return expiryMillis > nowMillis ? "CANCELLED_ACTIVE" : "EXPIRED";
        case "SUBSCRIPTION_STATE_EXPIRED":
            return "EXPIRED";
        case "SUBSCRIPTION_STATE_REVOKED":
            return "REVOKED";
        case "SUBSCRIPTION_STATE_PENDING":
            return "PENDING";
        default:
            // Fail closed: Unknown subscription states must NEVER grant access.
            return "EXPIRED";
    }
}
/**
 * Performs authoritative Google Play Developer API verification for a purchase token.
 * Validates package name, product ID, and expiry date strictly without fail-open fallbacks.
 */
async function verifyTokenWithGooglePlay(playClient, purchaseToken, targetPackage = exports.EXPECTED_PACKAGE_NAME, targetProduct = exports.EXPECTED_PRODUCT_ID, nowMillis = Date.now()) {
    if (targetPackage !== exports.EXPECTED_PACKAGE_NAME) {
        return {
            success: false,
            error: {
                code: "PRODUCT_MISMATCH",
                message: `Package name mismatch: expected ${exports.EXPECTED_PACKAGE_NAME}, got ${targetPackage}`
            }
        };
    }
    if (targetProduct !== exports.EXPECTED_PRODUCT_ID) {
        return {
            success: false,
            error: {
                code: "PRODUCT_MISMATCH",
                message: `Product ID mismatch: expected ${exports.EXPECTED_PRODUCT_ID}, got ${targetProduct}`
            }
        };
    }
    if (!playClient) {
        return {
            success: false,
            error: {
                code: "BACKEND_CONFIGURATION_ERROR",
                message: "Google Play Developer API client is unavailable on backend"
            }
        };
    }
    try {
        const response = await playClient.purchases.subscriptionsv2.get({
            packageName: targetPackage,
            token: purchaseToken
        });
        const subData = response?.data;
        if (!subData) {
            return {
                success: false,
                error: {
                    code: "INVALID_PURCHASE",
                    message: "Empty response received from Google Play Developer API"
                }
            };
        }
        // Product validation: find matching line item for EXPECTED_PRODUCT_ID
        const lineItems = subData.lineItems || [];
        const matchingLineItem = lineItems.find((item) => item.productId === targetProduct);
        if (!matchingLineItem) {
            return {
                success: false,
                error: {
                    code: "PRODUCT_MISMATCH",
                    message: `Purchase token does not contain line item matching expected product ${targetProduct}`
                }
            };
        }
        // Expiry validation: must come authoritatively from Google Play line item
        if (!matchingLineItem.expiryTime) {
            return {
                success: false,
                error: {
                    code: "MALFORMED_REQUEST",
                    message: "Google Play subscription line item is missing mandatory expiry time"
                }
            };
        }
        const expiryTimeMillis = new Date(matchingLineItem.expiryTime).getTime();
        if (isNaN(expiryTimeMillis)) {
            return {
                success: false,
                error: {
                    code: "MALFORMED_REQUEST",
                    message: "Google Play subscription line item contains invalid expiry timestamp"
                }
            };
        }
        const autoRenew = matchingLineItem.autoRenewingPlan?.autoRenewEnabled ?? false;
        const subState = subData.subscriptionState || "SUBSCRIPTION_STATE_UNSPECIFIED";
        // Trial validation: strictly check offerDetails tags, NO duration-based guessing
        const offerTags = matchingLineItem.offerDetails?.offerTags || [];
        const isTrialOffer = offerTags.includes("free-trial") || offerTags.includes("introductory-trial");
        const status = mapPlayStateToEntitlementStatus(subState, expiryTimeMillis, isTrialOffer, nowMillis);
        const verifiedEntitlement = {
            productId: targetProduct,
            status: status,
            expiryTimestampMillis: expiryTimeMillis,
            autoRenewEnabled: autoRenew,
            verificationTimestampMillis: nowMillis,
            source: "GOOGLE_PLAY_BACKEND"
        };
        return { success: true, entitlement: verifiedEntitlement };
    }
    catch (apiErr) {
        const statusCode = apiErr?.code || apiErr?.status || apiErr?.response?.status;
        logger.warn(`Google Play Developer API lookup failed (status=${statusCode}):`, apiErr?.message || apiErr);
        if (statusCode === 404 || statusCode === 400) {
            return {
                success: false,
                error: {
                    code: "INVALID_PURCHASE",
                    message: "Purchase token not found or invalid on Google Play"
                }
            };
        }
        else if (statusCode === 401 || statusCode === 403) {
            return {
                success: false,
                error: {
                    code: "BACKEND_CONFIGURATION_ERROR",
                    message: "Backend unauthorized to query Google Play Developer API"
                }
            };
        }
        else {
            return {
                success: false,
                error: {
                    code: "PLAY_API_UNAVAILABLE",
                    message: "Google Play Developer API is temporarily unavailable"
                }
            };
        }
    }
}
/**
 * Cloud Function HTTPS Endpoint: verifyPurchase (Gen 2)
 * Region: europe-west1
 * Performs strict, fail-closed verification of purchase tokens against Google Play.
 */
exports.verifyPurchase = (0, https_1.onRequest)({ region: exports.FUNCTION_REGION }, async (req, res) => {
    // Only allow POST requests for native Android client calls
    if (req.method !== "POST") {
        res.status(405).json({
            code: "MALFORMED_REQUEST",
            message: "Method Not Allowed. Only POST is supported."
        });
        return;
    }
    try {
        const { purchaseToken, productId, packageName } = req.body || {};
        if (!purchaseToken || typeof purchaseToken !== "string" || purchaseToken.trim() === "") {
            res.status(400).json({
                code: "INVALID_PURCHASE",
                message: "Missing or empty purchaseToken parameter"
            });
            return;
        }
        const targetPackage = packageName || exports.EXPECTED_PACKAGE_NAME;
        const targetProduct = productId || exports.EXPECTED_PRODUCT_ID;
        if (targetPackage !== exports.EXPECTED_PACKAGE_NAME) {
            res.status(400).json({
                code: "PRODUCT_MISMATCH",
                message: `Package name mismatch: expected ${exports.EXPECTED_PACKAGE_NAME}`
            });
            return;
        }
        if (targetProduct !== exports.EXPECTED_PRODUCT_ID) {
            res.status(400).json({
                code: "PRODUCT_MISMATCH",
                message: `Product ID mismatch: expected ${exports.EXPECTED_PRODUCT_ID}`
            });
            return;
        }
        const nowMillis = Date.now();
        const playClient = await getPlayDeveloperClient();
        const result = await verifyTokenWithGooglePlay(playClient, purchaseToken, targetPackage, targetProduct, nowMillis);
        if (!result.success) {
            let httpStatus = 400;
            if (result.error.code === "BACKEND_CONFIGURATION_ERROR") {
                httpStatus = 503;
            }
            else if (result.error.code === "PLAY_API_UNAVAILABLE") {
                httpStatus = 502;
            }
            res.status(httpStatus).json(result.error);
            return;
        }
        // Persist verified entitlement in Firestore 'entitlements' collection using SHA-256 doc ID
        const docId = getPurchaseDocId(purchaseToken);
        await db.collection("entitlements").doc(docId).set({
            ...result.entitlement,
            packageName: targetPackage,
            lastUpdated: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
        logger.info(`Successfully verified entitlement for doc ${docId}: status=${result.entitlement.status}`);
        res.status(200).json(result.entitlement);
    }
    catch (error) {
        logger.error("Internal server error during verification:", error?.message || error);
        res.status(500).json({
            code: "PLAY_API_UNAVAILABLE",
            message: "Internal server error during purchase verification"
        });
    }
});
/**
 * Cloud Function Pub/Sub Trigger: rtdnHandler (Gen 2)
 * Region: europe-west1
 * Listens to Google Play Real-Time Developer Notifications (RTDN),
 * validates package identity, re-queries Google Play authoritatively, and updates Firestore.
 */
exports.rtdnHandler = (0, pubsub_1.onMessagePublished)({ topic: "play-rtdn-topic", region: exports.FUNCTION_REGION }, async (event) => {
    try {
        const messageData = event.data?.message?.data;
        if (!messageData) {
            logger.warn("RTDN Pub/Sub event received without message data payload.");
            return;
        }
        const payloadString = Buffer.from(messageData, "base64").toString("utf-8");
        const notificationData = JSON.parse(payloadString);
        // Package Name Validation: Only process notifications for com.aistudio.humanstrength.kfqjza
        if (notificationData.packageName && notificationData.packageName !== exports.EXPECTED_PACKAGE_NAME) {
            logger.warn(`Received RTDN for unexpected package: ${notificationData.packageName}. Ignoring.`);
            return;
        }
        const subNotification = notificationData.subscriptionNotification;
        if (!subNotification) {
            logger.info("Non-subscription notification received, ignoring.");
            return;
        }
        const purchaseToken = subNotification.purchaseToken;
        if (!purchaseToken || typeof purchaseToken !== "string") {
            logger.warn("RTDN subscription notification missing purchaseToken.");
            return;
        }
        const nowMillis = Date.now();
        const docId = getPurchaseDocId(purchaseToken);
        // Authoritative Google Play Developer API Re-Query
        const playClient = await getPlayDeveloperClient();
        const verificationResult = await verifyTokenWithGooglePlay(playClient, purchaseToken, exports.EXPECTED_PACKAGE_NAME, exports.EXPECTED_PRODUCT_ID, nowMillis);
        if (!verificationResult.success) {
            logger.warn(`RTDN authoritative Play re-query failed (${verificationResult.error.code}: ${verificationResult.error.message}). Entitlement state not mutated.`);
            return;
        }
        const newEntitlement = verificationResult.entitlement;
        const docRef = db.collection("entitlements").doc(docId);
        const existingDoc = await docRef.get();
        // Idempotency / Stale Event Guard
        if (existingDoc.exists) {
            const existingData = existingDoc.data();
            if (existingData?.verificationTimestampMillis &&
                existingData.verificationTimestampMillis > newEntitlement.verificationTimestampMillis) {
                logger.info(`Skipping stale RTDN update for ${docId}: existing timestamp is newer.`);
                return;
            }
        }
        const updatedRecord = {
            ...newEntitlement,
            source: "GOOGLE_PLAY_RTDN",
            packageName: exports.EXPECTED_PACKAGE_NAME,
            lastRtdnNotificationType: subNotification.notificationType ?? null,
            lastUpdated: admin.firestore.FieldValue.serverTimestamp()
        };
        await docRef.set(updatedRecord, { merge: true });
        logger.info(`Updated entitlement ${docId} from RTDN to authoritative status ${newEntitlement.status}`);
    }
    catch (err) {
        logger.error("Error processing RTDN message:", err?.message || err);
    }
});
//# sourceMappingURL=index.js.map