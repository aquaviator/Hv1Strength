import admin from 'firebase-admin';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const PROJECT = 'hv1-platform';
const UID = 'wCyOGolCFUULPNVXtWHk5lXPHKc2';
const HUMAN = 'human_7fe94474616b0611e19217c7565d529d';
const WORKOUT = 'a9b619f7-4922-4463-890c-fb7778735d52';
const VERSIONS = [
  `${WORKOUT}_r1_ead95d373d85`,
  `${WORKOUT}_r2_9e9e6ba9bc0c`
];
if (process.argv.slice(2).join(' ') !== `--project ${PROJECT}`) {
  throw new Error(`Exact --project ${PROJECT} is required`);
}
const executable = process.platform === 'win32' ? 'gcloud.cmd' : 'gcloud';
const operator = execFileSync(executable,
  ['auth', 'list', '--filter=status:ACTIVE', '--format=value(account)', '--project', PROJECT],
  { encoding: 'utf8', shell: process.platform === 'win32' }).trim();
if (!operator) throw new Error('Authenticated operator unavailable');
if (!admin.apps.length) admin.initializeApp({ projectId: PROJECT });
const db = admin.firestore();
const normalize = value => {
  if (value instanceof admin.firestore.Timestamp) return value.toMillis();
  if (Array.isArray(value)) return value.map(normalize);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, normalize(value[key])]));
  return value;
};
const stable = value => JSON.stringify(normalize(value));
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const count = async path => (await db.collection(path).count().get()).data().count;

const [account, root, intro, strength, current, publications, studioGrant, strengthGrant, strengthEvent] = await Promise.all([
  db.doc(`accounts/${UID}`).get(), db.doc(`users/${HUMAN}`).get(),
  db.doc(`accounts/${UID}/entitlements/human_v1`).get(), db.doc(`accounts/${UID}/entitlements/human_strength`).get(),
  db.doc(`accounts/${UID}/entitlements/current`).get(),
  Promise.all(VERSIONS.map(id => db.doc(`users/${HUMAN}/publishedWorkouts/${id}`).get())),
  db.doc(`accounts/${UID}/entitlementGrants/support_260d2e4ed2db038180de9e1dbce6a30e`).get(),
  db.doc(`accounts/${UID}/entitlementGrants/support_f0d36bfbfceff9529530af12a443e4f0`).get(),
  db.doc(`accounts/${UID}/entitlementEvents/grant_f0d36bfbfceff9529530af12a443e4f0`).get()
]);
if (account.data()?.humanUserId !== HUMAN || account.data()?.status !== 'ACTIVE' || root.data()?.ownerFirebaseUid !== UID) {
  throw new Error('Exact identity ownership precondition failed');
}
const expected = [
  { revision: 1, checksum: 'ead95d373d85' },
  { revision: 2, checksum: '9e9e6ba9bc0c' }
];
const publicationReceipt = publications.map((snapshot, index) => {
  const value = snapshot.data();
  const payloadChecksum = value?.payload ? sha256(stable(value.payload)) : null;
  if (!snapshot.exists || value?.humanUserId !== HUMAN || value?.globalId !== WORKOUT ||
      value?.revision !== expected[index].revision || value?.contentChecksum?.slice(0, 12) !== expected[index].checksum ||
      payloadChecksum !== value?.contentChecksum) throw new Error(`Publication ${VERSIONS[index]} precondition failed`);
  return { versionId: snapshot.id, revision: value.revision, checksum: value.contentChecksum,
    schemaVersion: value.schemaVersion, publicationState: value.publicationState,
    tombstoneState: value.tombstoneState, title: value.payload?.title, discipline: value.payload?.discipline,
    catalogueReleaseId: value.payload?.catalogueReleaseId };
});
const collections = ['templates', 'templateExercises', 'templateSets', 'sessions', 'loggedSets',
  'trainingPlans', 'plannedWorkouts', 'customExercises', 'processedCommands', 'workoutDeliveryAcks'];
const aggregateValues = await Promise.all(collections.map(name => count(`users/${HUMAN}/${name}`)));
const aggregates = Object.fromEntries(collections.map((name, index) => [name, aggregateValues[index]]));
const currentValue = current.data() ?? {};
console.log(JSON.stringify({ dryRun: true, project: PROJECT, operator, uid: UID, humanUserId: HUMAN,
  identityConfirmed: true, publications: publicationReceipt, aggregates: { ...aggregates, importedStudioWorkoutLinks: 0 },
  entitlements: {
    introductoryHash: sha256(stable(intro.data())), strengthHistoricalHash: sha256(stable(strength.data())),
    historicalTrialStartMillis: intro.data()?.trialStartedAt?.toMillis(), historicalTrialEndMillis: intro.data()?.trialEndsAt?.toMillis(),
    currentRevision: currentValue.revision, currentProductScope: currentValue.productScope,
    currentProducts: Object.keys(currentValue.products ?? {}).sort(), studioGrantExists: studioGrant.exists,
    strengthGrantExists: strengthGrant.exists, strengthEventExists: strengthEvent.exists
  }
}, null, 2));
