import admin from 'firebase-admin';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const args = Object.fromEntries(process.argv.slice(2).map(value => {
  const [key, ...rest] = value.replace(/^--/, '').split('=');
  return [key, rest.join('=') || true];
}));
const required = {
  project: 'hv1-platform', uid: 'wCyOGolCFUULPNVXtWHk5lXPHKc2',
  human: 'human_7fe94474616b0611e19217c7565d529d', product: 'WORKOUT_STUDIO',
  reason: 'WORKOUT_STUDIO_PRODUCTION_ACCEPTANCE', days: '14',
  idempotency: 'workout-studio-production-acceptance-2026-09-01'
};
for (const [key, value] of Object.entries(required)) {
  if (String(args[key]) !== value) throw new Error(`Exact --${key}=${value} is required`);
}

const gcloudExecutable = process.platform === 'win32' ? 'gcloud.cmd' : 'gcloud';
const operator = execFileSync(gcloudExecutable, ['auth', 'list', '--filter=status:ACTIVE', '--format=value(account)', '--project', required.project], { encoding: 'utf8', shell: process.platform === 'win32' }).trim();
if (!operator) throw new Error('Authenticated operator unavailable');
if (!admin.apps.length) admin.initializeApp({ projectId: required.project });
const db = admin.firestore();
const hash = crypto.createHash('sha256').update(Object.values(required).join('|')).digest('hex').slice(0, 32);
const grantId = `support_${hash}`;
const eventId = `grant_${hash}`;
const account = db.doc(`accounts/${required.uid}`);
const root = db.doc(`users/${required.human}`);
const introductory = db.doc(`accounts/${required.uid}/entitlements/human_v1`);
const strength = db.doc(`accounts/${required.uid}/entitlements/human_strength`);
const current = db.doc(`accounts/${required.uid}/entitlements/current`);
const grant = db.doc(`accounts/${required.uid}/entitlementGrants/${grantId}`);
const event = db.doc(`accounts/${required.uid}/entitlementEvents/${eventId}`);
const expectedStart = 1785331832887;
const expectedEnd = 1787923832887;

const validateHistory = (introData, strengthData) => {
  if (introData?.trialStartedAt?.toMillis() !== expectedStart || introData?.trialEndsAt?.toMillis() !== expectedEnd) throw new Error('human_v1 historical precondition failed');
  if (strengthData?.trialStartedAt?.toMillis() !== expectedStart || strengthData?.trialEndsAt?.toMillis() !== expectedEnd || strengthData?.migratedFrom !== 'human_v1') throw new Error('human_strength historical precondition failed');
};

const [accountSnap, rootSnap, introSnap, strengthSnap, currentSnap, grantSnap, eventSnap] = await Promise.all([
  account.get(), root.get(), introductory.get(), strength.get(), current.get(), grant.get(), event.get()
]);
if (accountSnap.data()?.humanUserId !== required.human || accountSnap.data()?.status !== 'ACTIVE' || rootSnap.data()?.ownerFirebaseUid !== required.uid) throw new Error('Identity precondition failed');
validateHistory(introSnap.data(), strengthSnap.data());

const receipt = {
  dryRun: Boolean(args['dry-run']), operator, project: required.project, uid: required.uid,
  humanUserId: required.human, productScope: required.product, reasonCode: required.reason,
  durationDays: 14, idempotencyKey: required.idempotency, grantId, eventId,
  previousState: currentSnap.data()?.normalizedState ?? null, replay: grantSnap.exists,
  before: { introductoryStartedAtMillis: expectedStart, introductoryEndsAtMillis: expectedEnd,
    strengthStartedAtMillis: expectedStart, strengthEndsAtMillis: expectedEnd,
    currentExists: currentSnap.exists, grantExists: grantSnap.exists, eventExists: eventSnap.exists }
};
if (args['dry-run']) { console.log(JSON.stringify(receipt, null, 2)); process.exit(0); }

const outcome = await db.runTransaction(async tx => {
  const [accountTx, rootTx, introTx, strengthTx, currentTx, grantTx, eventTx] = await Promise.all([
    tx.get(account), tx.get(root), tx.get(introductory), tx.get(strength), tx.get(current), tx.get(grant), tx.get(event)
  ]);
  if (accountTx.data()?.humanUserId !== required.human || rootTx.data()?.ownerFirebaseUid !== required.uid) throw new Error('Identity changed during grant');
  validateHistory(introTx.data(), strengthTx.data());
  if (grantTx.exists) {
    const data = grantTx.data();
    if (data?.idempotencyKey !== required.idempotency || data?.productScope !== required.product || data?.reasonCode !== required.reason || !eventTx.exists) throw new Error('Conflicting replay');
    return { replay: true, effectiveAt: data.effectiveAt.toDate().toISOString(), expiryAt: data.expiryAt.toDate().toISOString(), revision: currentTx.data()?.revision };
  }
  if (eventTx.exists) throw new Error('Conflicting audit event');
  const now = admin.firestore.Timestamp.now();
  const expiry = admin.firestore.Timestamp.fromMillis(now.toMillis() + 14 * 86_400_000);
  const revision = (currentTx.data()?.revision ?? 0) + 1;
  tx.create(grant, { schemaVersion: 1, grantId, firebaseUid: required.uid, humanUserId: required.human,
    productScope: required.product, reasonCode: required.reason, effectiveAt: now, expiryAt: expiry,
    status: 'ACTIVE', authorizingPrincipal: operator, authorizationSource: 'EXPLICIT_USER_AUTHORIZATION',
    authorizationReference: required.idempotency, idempotencyKey: required.idempotency,
    createdAt: now, updatedAt: now, revokedAt: null, revokedBy: null, revocationReason: null });
  tx.create(event, { schemaVersion: 1, eventId, operator, targetFirebaseUid: required.uid,
    targetHumanUserId: required.human, action: 'SUPPORT_GRANT_CREATED',
    previousNormalizedState: currentTx.data()?.normalizedState ?? null,
    resultingNormalizedState: 'ACTIVE_UNTIL_EXPIRY', reason: required.reason, grantId,
    serverTimestamp: now, correlationId: required.idempotency });
  tx.set(current, { schemaVersion: 1, firebaseUid: required.uid, humanUserId: required.human,
    normalizedState: 'ACTIVE_UNTIL_EXPIRY', productScope: required.product, source: 'SUPPORT',
    effectiveAt: now, expiryAt: expiry, verificationTimestamp: now, revision,
    introductoryState: 'EXPIRED', introductoryStartedAt: introTx.data().trialStartedAt,
    introductoryExpiredAt: introTx.data().trialEndsAt,
    sourceReferences: { introductory: introductory.path, strength: strength.path, supportGrant: grant.path },
    supportGrantId: grantId, offlineReceiptValidUntil: admin.firestore.Timestamp.fromMillis(Math.min(expiry.toMillis(), now.toMillis() + 86_400_000)),
    updatedByBackend: true, updatedByPrincipal: operator, updatedAt: now });
  return { replay: false, effectiveAt: now.toDate().toISOString(), expiryAt: expiry.toDate().toISOString(), revision };
});
console.log(JSON.stringify({ ...receipt, dryRun: false, ...outcome }, null, 2));
