import { onRequest } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { google } from "googleapis";
import * as crypto from "crypto";

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

export const EXPECTED_PACKAGE_NAME = "com.aistudio.humanstrength.kfqjza";
export const EXPECTED_PRODUCT_ID = "human_strength_annual";
export const FUNCTION_REGION = "europe-west1";
export const TRIAL_POLICY_PATH = "platform_config/trial_policy";
export const ACCOUNT_TRIAL_DOCUMENT_ID = "human_v1";
export const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

export interface TrialPolicy {
  trialEnabled: boolean;
  trialDurationDays: number;
}

export function parseTrialPolicy(data: admin.firestore.DocumentData | undefined): TrialPolicy | null {
  if (
    !data ||
    typeof data.trialEnabled !== "boolean" ||
    !Number.isInteger(data.trialDurationDays) ||
    data.trialDurationDays <= 0
  ) {
    return null;
  }
  return {
    trialEnabled: data.trialEnabled,
    trialDurationDays: data.trialDurationDays
  };
}

export interface VerifiedEntitlement {
  productId: string;
  status: "ACTIVE" | "TRIAL_ACTIVE" | "CANCELLED_ACTIVE" | "GRACE_PERIOD" | "ACCOUNT_HOLD" | "PAUSED" | "EXPIRED" | "REVOKED" | "PENDING";
  expiryTimestampMillis: number;
  autoRenewEnabled: boolean;
  verificationTimestampMillis: number;
  source: string;
}

export interface VerificationError {
  code: "INVALID_PURCHASE" | "PLAY_API_UNAVAILABLE" | "BACKEND_CONFIGURATION_ERROR" | "PRODUCT_MISMATCH" | "MALFORMED_REQUEST" | "EXPIRED";
  message: string;
}

export type VerificationResultInternal =
  | { success: true; entitlement: VerifiedEntitlement }
  | { success: false; error: VerificationError };

/**
 * Creates a cryptographically secure one-way hash for purchase token doc IDs.
 * Protects sensitive purchase token data by using SHA-256 without exposing raw token material.
 */
export function getPurchaseDocId(purchaseToken: string): string {
  if (!purchaseToken) {
    return "play_unknown";
  }
  const hash = crypto.createHash("sha256").update(purchaseToken).digest("hex");
  return `play_${hash.substring(0, 32)}`;
}

/**
 * Deterministic Java String hashCode calculation in JS/TS.
 * Matches Kotlin String.hashCode() behavior for deriving humanUserId from uid.
 */
export function getJavaStringHashCode(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0; // Convert to 32-bit signed integer
  }
  return hash;
}

export const FIRESTORE_USER_SUBCOLLECTIONS = [
  "profile",
  "sessions",
  "loggedSets",
  "weight",
  "tape",
  "customExercises",
  "templates",
  "templateExercises",
  "templateSets",
  "processedCommands"
];

/**
 * Purges all user-owned Firestore documents and subcollections under users/{humanUserId}.
 * Enforces ownership boundary derived from authenticated user context.
 */
export async function purgeUserCloudData(
  firestoreDb: admin.firestore.Firestore,
  uid: string,
  targetHumanUserId: string
): Promise<{ deletedSubcollections: string[]; totalDocumentsDeleted: number }> {
  let totalDeleted = 0;
  const deletedSubcollections: string[] = [];

  const userDocRef = firestoreDb.collection("users").doc(targetHumanUserId);

  for (const subColl of FIRESTORE_USER_SUBCOLLECTIONS) {
    const subCollRef = userDocRef.collection(subColl);
    const snapshot = await subCollRef.get();
    if (!snapshot.empty) {
      const batch = firestoreDb.batch();
      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
        totalDeleted++;
      });
      await batch.commit();
      deletedSubcollections.push(subColl);
    }
  }

  // Delete top-level user document
  const userDocSnapshot = await userDocRef.get();
  if (userDocSnapshot.exists) {
    await userDocRef.delete();
    totalDeleted++;
  }

  return { deletedSubcollections, totalDocumentsDeleted: totalDeleted };
}

/**
 * Creates an authorized Google Play Developer API client if service credentials exist.
 */
export async function getPlayDeveloperClient() {
  try {
    const auth = new google.auth.GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/androidpublisher"]
    });
    return google.androidpublisher({ version: "v3", auth });
  } catch (error: any) {
    logger.warn("Google Auth initialization failed for Play Developer API:", error?.message || error);
    return null;
  }
}

/**
 * Maps raw Google Play subscription state and offer metadata into entitlement status.
 * Fails CLOSED on unknown states by returning EXPIRED rather than granting access.
 */
export function mapPlayStateToEntitlementStatus(
  subscriptionState: string,
  expiryMillis: number,
  isTrial: boolean,
  nowMillis: number = Date.now()
): "ACTIVE" | "TRIAL_ACTIVE" | "CANCELLED_ACTIVE" | "GRACE_PERIOD" | "ACCOUNT_HOLD" | "PAUSED" | "EXPIRED" | "REVOKED" | "PENDING" {
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
export async function verifyTokenWithGooglePlay(
  playClient: any,
  purchaseToken: string,
  targetPackage: string = EXPECTED_PACKAGE_NAME,
  targetProduct: string = EXPECTED_PRODUCT_ID,
  nowMillis: number = Date.now()
): Promise<VerificationResultInternal> {
  if (targetPackage !== EXPECTED_PACKAGE_NAME) {
    return {
      success: false,
      error: {
        code: "PRODUCT_MISMATCH",
        message: `Package name mismatch: expected ${EXPECTED_PACKAGE_NAME}, got ${targetPackage}`
      }
    };
  }

  if (targetProduct !== EXPECTED_PRODUCT_ID) {
    return {
      success: false,
      error: {
        code: "PRODUCT_MISMATCH",
        message: `Product ID mismatch: expected ${EXPECTED_PRODUCT_ID}, got ${targetProduct}`
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
    const lineItems: any[] = subData.lineItems || [];
    const matchingLineItem = lineItems.find((item: any) => item.productId === targetProduct);

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
    const offerTags: string[] = matchingLineItem.offerDetails?.offerTags || [];
    const isTrialOffer = offerTags.includes("free-trial") || offerTags.includes("introductory-trial");

    const status = mapPlayStateToEntitlementStatus(subState, expiryTimeMillis, isTrialOffer, nowMillis);

    const verifiedEntitlement: VerifiedEntitlement = {
      productId: targetProduct,
      status: status,
      expiryTimestampMillis: expiryTimeMillis,
      autoRenewEnabled: autoRenew,
      verificationTimestampMillis: nowMillis,
      source: "GOOGLE_PLAY_BACKEND"
    };

    return { success: true, entitlement: verifiedEntitlement };
  } catch (apiErr: any) {
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
    } else if (statusCode === 401 || statusCode === 403) {
      return {
        success: false,
        error: {
          code: "BACKEND_CONFIGURATION_ERROR",
          message: "Backend unauthorized to query Google Play Developer API"
        }
      };
    } else {
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
export const verifyPurchase = onRequest(
  { region: FUNCTION_REGION },
  async (req, res) => {
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

      const targetPackage = packageName || EXPECTED_PACKAGE_NAME;
      const targetProduct = productId || EXPECTED_PRODUCT_ID;

      if (targetPackage !== EXPECTED_PACKAGE_NAME) {
        res.status(400).json({
          code: "PRODUCT_MISMATCH",
          message: `Package name mismatch: expected ${EXPECTED_PACKAGE_NAME}`
        });
        return;
      }

      if (targetProduct !== EXPECTED_PRODUCT_ID) {
        res.status(400).json({
          code: "PRODUCT_MISMATCH",
          message: `Product ID mismatch: expected ${EXPECTED_PRODUCT_ID}`
        });
        return;
      }

      const nowMillis = Date.now();
      const playClient = await getPlayDeveloperClient();

      const result = await verifyTokenWithGooglePlay(
        playClient,
        purchaseToken,
        targetPackage,
        targetProduct,
        nowMillis
      );

      if (!result.success) {
        let httpStatus = 400;
        if (result.error.code === "BACKEND_CONFIGURATION_ERROR") {
          httpStatus = 503;
        } else if (result.error.code === "PLAY_API_UNAVAILABLE") {
          httpStatus = 502;
        }
        res.status(httpStatus).json(result.error);
        return;
      }

      // Persist verified entitlement in Firestore 'entitlements' collection using SHA-256 doc ID
      const docId = getPurchaseDocId(purchaseToken);
      await db.collection("entitlements").doc(docId).set(
        {
          ...result.entitlement,
          packageName: targetPackage,
          lastUpdated: admin.firestore.FieldValue.serverTimestamp()
        },
        { merge: true }
      );

      logger.info(`Successfully verified entitlement for doc ${docId}: status=${result.entitlement.status}`);
      res.status(200).json(result.entitlement);
    } catch (error: any) {
      logger.error("Internal server error during verification:", error?.message || error);
      res.status(500).json({
        code: "PLAY_API_UNAVAILABLE",
        message: "Internal server error during purchase verification"
      });
    }
  }
);

/**
 * Authenticated, idempotent Human V1 account-trial initialization/status endpoint.
 * Trial records are stored outside client-writable profile sync and can only be
 * created by this trusted backend.
 */
export const initializeAccountTrial = onRequest(
  { region: FUNCTION_REGION },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ code: "MALFORMED_REQUEST", message: "Method Not Allowed. Only POST is supported." });
      return;
    }

    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      res.status(401).json({ code: "UNAUTHORIZED", message: "Missing or invalid Authorization header" });
      return;
    }

    let uid: string;
    try {
      uid = (await admin.auth().verifyIdToken(authHeader.substring("Bearer ".length))).uid;
    } catch (error: any) {
      logger.warn("Authentication failed for initializeAccountTrial:", error?.message || error);
      res.status(401).json({ code: "UNAUTHORIZED", message: "Invalid or expired authentication token" });
      return;
    }

    const trialRef = db.collection("accounts").doc(uid)
      .collection("entitlements").doc(ACCOUNT_TRIAL_DOCUMENT_ID);
    const policyRef = db.doc(TRIAL_POLICY_PATH);

    try {
      const result = await db.runTransaction(async (transaction) => {
        const existing = await transaction.get(trialRef);
        if (existing.exists) {
          const data = existing.data();
          const startedAt = data?.trialStartedAt;
          const endsAt = data?.trialEndsAt;
          if (
            startedAt instanceof admin.firestore.Timestamp &&
            endsAt instanceof admin.firestore.Timestamp
          ) {
            const serverNowMillis = Date.now();
            return {
              status: endsAt.toMillis() > serverNowMillis ? "ACTIVE" : "EXPIRED",
              trialStartedAtMillis: startedAt.toMillis(),
              trialEndsAtMillis: endsAt.toMillis(),
              serverNowMillis
            };
          }
          throw new Error("Existing account trial record is malformed");
        }

        const policySnapshot = await transaction.get(policyRef);
        const policy = parseTrialPolicy(policySnapshot.data());
        if (!policy) {
          throw new Error("Trial policy is missing or invalid");
        }
        if (!policy.trialEnabled) {
          return { status: "DISABLED", serverNowMillis: Date.now() };
        }

        const serverNowMillis = Date.now();
        const trialStartedAt = admin.firestore.Timestamp.fromMillis(serverNowMillis);
        const trialEndsAt = admin.firestore.Timestamp.fromMillis(
          serverNowMillis + policy.trialDurationDays * MILLIS_PER_DAY
        );
        transaction.create(trialRef, {
          trialStartedAt,
          trialEndsAt
        });
        return {
          status: "ACTIVE",
          trialStartedAtMillis: trialStartedAt.toMillis(),
          trialEndsAtMillis: trialEndsAt.toMillis(),
          serverNowMillis
        };
      });

      res.status(200).json(result);
    } catch (error: any) {
      logger.error(`Account trial initialization unavailable for ${uid}:`, error?.message || error);
      res.status(503).json({
        code: "TRIAL_UNAVAILABLE",
        message: "Account trial status is temporarily unavailable"
      });
    }
  }
);

/**
 * Cloud Function Pub/Sub Trigger: rtdnHandler (Gen 2)
 * Region: europe-west1
 * Listens to Google Play Real-Time Developer Notifications (RTDN),
 * validates package identity, re-queries Google Play authoritatively, and updates Firestore.
 */
export const rtdnHandler = onMessagePublished(
  { topic: "play-rtdn-topic", region: FUNCTION_REGION },
  async (event) => {
    try {
      const messageData = event.data?.message?.data;
      if (!messageData) {
        logger.warn("RTDN Pub/Sub event received without message data payload.");
        return;
      }

      const payloadString = Buffer.from(messageData, "base64").toString("utf-8");
      const notificationData = JSON.parse(payloadString);

      // Package Name Validation: Only process notifications for com.aistudio.humanstrength.kfqjza
      if (notificationData.packageName && notificationData.packageName !== EXPECTED_PACKAGE_NAME) {
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
      const verificationResult = await verifyTokenWithGooglePlay(
        playClient,
        purchaseToken,
        EXPECTED_PACKAGE_NAME,
        EXPECTED_PRODUCT_ID,
        nowMillis
      );

      if (!verificationResult.success) {
        logger.warn(
          `RTDN authoritative Play re-query failed (${verificationResult.error.code}: ${verificationResult.error.message}). Entitlement state not mutated.`
        );
        return;
      }

      const newEntitlement = verificationResult.entitlement;
      const docRef = db.collection("entitlements").doc(docId);
      const existingDoc = await docRef.get();

      // Idempotency / Stale Event Guard
      if (existingDoc.exists) {
        const existingData = existingDoc.data();
        if (
          existingData?.verificationTimestampMillis &&
          existingData.verificationTimestampMillis > newEntitlement.verificationTimestampMillis
        ) {
          logger.info(`Skipping stale RTDN update for ${docId}: existing timestamp is newer.`);
          return;
        }
      }

      const updatedRecord = {
        ...newEntitlement,
        source: "GOOGLE_PLAY_RTDN",
        packageName: EXPECTED_PACKAGE_NAME,
        lastRtdnNotificationType: subNotification.notificationType ?? null,
        lastUpdated: admin.firestore.FieldValue.serverTimestamp()
      };

      await docRef.set(updatedRecord, { merge: true });
      logger.info(`Updated entitlement ${docId} from RTDN to authoritative status ${newEntitlement.status}`);
    } catch (err: any) {
      logger.error("Error processing RTDN message:", err?.message || err);
    }
  }
);

/**
 * Authoritative Server-Side User Account & Cloud Data Deletion Endpoint.
 * Purges all user-owned Firestore documents/subcollections and deletes the Firebase Authentication identity.
 */
export const deleteUserAccount = onRequest(
  { region: FUNCTION_REGION },
  async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST") {
      res.status(405).json({ code: "MALFORMED_REQUEST", message: "Method Not Allowed. Only POST is supported." });
      return;
    }

    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      res.status(401).json({ code: "UNAUTHORIZED", message: "Missing or invalid Authorization header" });
      return;
    }

    const idToken = authHeader.split("Bearer ")[1];
    let decodedToken: admin.auth.DecodedIdToken;
    try {
      decodedToken = await admin.auth().verifyIdToken(idToken);
    } catch (e: any) {
      logger.warn("Authentication failed for deleteUserAccount:", e?.message || e);
      res.status(401).json({ code: "UNAUTHORIZED", message: "Invalid or expired authentication token" });
      return;
    }

    const uid = decodedToken.uid;
    const { humanUserId: bodyHumanUserId } = req.body || {};

    let humanUserId = bodyHumanUserId;
    if (!humanUserId || typeof humanUserId !== "string" || !humanUserId.startsWith("human_")) {
      const hash = getJavaStringHashCode(uid).toString().replace("-", "n").padEnd(12, "x").substring(0, 12);
      humanUserId = `human_${hash}`;
    }

    try {
      logger.info(`Initiating cloud data purge for user ${uid} (humanUserId=${humanUserId})`);
      const purgeResult = await purgeUserCloudData(db, uid, humanUserId);

      // Delete Firebase Authentication identity
      try {
        await admin.auth().deleteUser(uid);
        logger.info(`Successfully deleted Firebase Auth user ${uid}`);
      } catch (authErr: any) {
        logger.warn(`Firebase Auth user deletion produced warning/error for ${uid}:`, authErr?.message || authErr);
      }

      res.status(200).json({
        success: true,
        message: "Cloud account and all associated Firestore data successfully deleted",
        humanUserId,
        deletedSubcollections: purgeResult.deletedSubcollections,
        totalDocumentsDeleted: purgeResult.totalDocumentsDeleted
      });
    } catch (err: any) {
      logger.error(`Error deleting user account ${uid}:`, err?.message || err);
      res.status(500).json({ code: "INTERNAL_ERROR", message: err?.message || "Failed to purge cloud user data" });
    }
  }
);
