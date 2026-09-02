import admin from 'firebase-admin';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const args = Object.fromEntries(process.argv.slice(2).map(value => {
  const [key, ...rest] = value.replace(/^--/, '').split('=');
  return [key, rest.join('=') || true];
}));
const required = {
  project: 'hv1-platform', uid: 'wCyOGolCFUULPNVXtWHk5lXPHKc2',
  human: 'human_7fe94474616b0611e19217c7565d529d', product: 'HUMAN_STRENGTH',
  reason: 'WORKOUT_STUDIO_ANDROID_DELIVERY_ACCEPTANCE', days: '14',
  idempotency: 'workout-studio-android-delivery-acceptance-2026-09-02'
};
for (const [key, value] of Object.entries(required)) {
  if (String(args[key]) !== value) throw new Error(`Exact --${key}=${value} is required`);
}
const executable = process.platform === 'win32' ? 'gcloud.cmd' : 'gcloud';
const operator = execFileSync(executable, ['auth', 'list', '--filter=status:ACTIVE', '--format=value(account)', '--project', required.project],
  { encoding: 'utf8', shell: process.platform === 'win32' }).trim();
if (!operator) throw new Error('Authenticated operator unavailable');
if (!admin.apps.length) admin.initializeApp({ projectId: required.project });
const db = admin.firestore();
const hash = crypto.createHash('sha256').update(Object.values(required).join('|')).digest('hex').slice(0, 32);
const grantId = `support_${hash}`;
const eventId = `grant_${hash}`;
const refs = {
  account: db.doc(`accounts/${required.uid}`), root: db.doc(`users/${required.human}`),
  intro: db.doc(`accounts/${required.uid}/entitlements/human_v1`),
  strength: db.doc(`accounts/${required.uid}/entitlements/human_strength`),
  current: db.doc(`accounts/${required.uid}/entitlements/current`),
  grant: db.doc(`accounts/${required.uid}/entitlementGrants/${grantId}`),
  event: db.doc(`accounts/${required.uid}/entitlementEvents/${eventId}`)
};
const expectedStart = 1785331832887;
const expectedEnd = 1787923832887;
const validateHistory = (intro, strength) => {
  if (intro?.trialStartedAt?.toMillis() !== expectedStart || intro?.trialEndsAt?.toMillis() !== expectedEnd)
    throw new Error('human_v1 historical precondition failed');
  if (strength?.trialStartedAt?.toMillis() !== expectedStart || strength?.trialEndsAt?.toMillis() !== expectedEnd || strength?.migratedFrom !== 'human_v1')
    throw new Error('human_strength historical precondition failed');
};
const snaps = await Promise.all(Object.values(refs).map(ref => ref.get()));
const [accountSnap, rootSnap, introSnap, strengthSnap, currentSnap, grantSnap, eventSnap] = snaps;
if (accountSnap.data()?.humanUserId !== required.human || accountSnap.data()?.status !== 'ACTIVE' || rootSnap.data()?.ownerFirebaseUid !== required.uid)
  throw new Error('Identity precondition failed');
validateHistory(introSnap.data(), strengthSnap.data());
const currentBefore = currentSnap.data() ?? {};
if (currentBefore.productScope !== 'WORKOUT_STUDIO' || currentBefore.supportGrantId !== 'support_260d2e4ed2db038180de9e1dbce6a30e')
  throw new Error('Existing Workout Studio projection precondition failed');
const receipt = { dryRun: Boolean(args['dry-run']), project: required.project, operator,
  uid: required.uid, humanUserId: required.human, productScope: required.product,
  reasonCode: required.reason, durationDays: 14, idempotencyKey: required.idempotency,
  grantId, eventId, replay: grantSnap.exists, existingStudioGrantId: currentBefore.supportGrantId,
  historicalTrialStartMillis: expectedStart, historicalTrialEndMillis: expectedEnd };
if (args['dry-run']) { console.log(JSON.stringify(receipt, null, 2)); process.exit(0); }

const outcome = await db.runTransaction(async tx => {
  const [account, root, intro, strength, current, grant, event] = await Promise.all([
    tx.get(refs.account), tx.get(refs.root), tx.get(refs.intro), tx.get(refs.strength),
    tx.get(refs.current), tx.get(refs.grant), tx.get(refs.event)
  ]);
  if (account.data()?.humanUserId !== required.human || root.data()?.ownerFirebaseUid !== required.uid) throw new Error('Identity changed');
  validateHistory(intro.data(), strength.data());
  if (grant.exists) {
    const data = grant.data();
    if (data?.idempotencyKey !== required.idempotency || data?.productScope !== required.product ||
        data?.reasonCode !== required.reason || !event.exists) throw new Error('Conflicting replay');
    return { replay: true, effectiveAt: data.effectiveAt.toDate().toISOString(), expiryAt: data.expiryAt.toDate().toISOString() };
  }
  if (event.exists) throw new Error('Conflicting event');
  const previous = current.data() ?? {};
  if (previous.productScope !== 'WORKOUT_STUDIO' || previous.supportGrantId !== 'support_260d2e4ed2db038180de9e1dbce6a30e')
    throw new Error('Workout Studio scope changed');
  const now = admin.firestore.Timestamp.now();
  const expiry = admin.firestore.Timestamp.fromMillis(now.toMillis() + 14 * 86_400_000);
  const previousProducts = previous.products ?? {};
  const studioProduct = previousProducts.WORKOUT_STUDIO ?? {
    normalizedState: previous.normalizedState, source: previous.source, effectiveAt: previous.effectiveAt,
    expiryAt: previous.expiryAt, offlineReceiptValidUntil: previous.offlineReceiptValidUntil,
    supportGrantId: previous.supportGrantId
  };
  tx.create(refs.grant, { schemaVersion: 1, grantId, firebaseUid: required.uid, humanUserId: required.human,
    productScope: required.product, reasonCode: required.reason, effectiveAt: now, expiryAt: expiry,
    status: 'ACTIVE', authorizingPrincipal: operator, authorizationSource: 'EXPLICIT_USER_AUTHORIZATION',
    authorizationReference: required.idempotency, idempotencyKey: required.idempotency,
    createdAt: now, updatedAt: now, revokedAt: null, revokedBy: null, revocationReason: null });
  tx.create(refs.event, { schemaVersion: 1, eventId, operator, targetFirebaseUid: required.uid,
    targetHumanUserId: required.human, action: 'SUPPORT_GRANT_CREATED', productScope: required.product,
    previousNormalizedState: previousProducts.HUMAN_STRENGTH?.normalizedState ?? 'EXPIRED',
    resultingNormalizedState: 'ACTIVE_UNTIL_EXPIRY', reason: required.reason, grantId,
    serverTimestamp: now, correlationId: required.idempotency });
  tx.set(refs.current, { ...previous, revision: (previous.revision ?? 0) + 1,
    products: { ...previousProducts, WORKOUT_STUDIO: studioProduct, HUMAN_STRENGTH: {
      normalizedState: 'ACTIVE_UNTIL_EXPIRY', source: 'SUPPORT', effectiveAt: now, expiryAt: expiry,
      offlineReceiptValidUntil: admin.firestore.Timestamp.fromMillis(Math.min(expiry.toMillis(), now.toMillis() + 86_400_000)),
      supportGrantId: grantId, reasonCode: required.reason
    } }, updatedByBackend: true, updatedByPrincipal: operator, updatedAt: now });
  return { replay: false, effectiveAt: now.toDate().toISOString(), expiryAt: expiry.toDate().toISOString() };
});
console.log(JSON.stringify({ ...receipt, dryRun: false, ...outcome }, null, 2));
