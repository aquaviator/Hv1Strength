# HumanV1 entitlement contract reconciliation

## Existing authority

HumanV1 introductory access is owned by `initializeAccountTrial` and stored at
`accounts/{firebaseUid}/entitlements/human_v1`. The immutable grant evidence is
`trialStartedAt` plus `trialEndsAt`; server time determines `ACTIVE` versus
`EXPIRED`. `platform_config/trial_policy` supplies `trialEnabled` and
`trialDurationDays` only when a record is first created. Reinstalling a client
or clearing browser storage therefore cannot issue another introductory period.

Google Play membership is a distinct contract. `verifyPurchase` and
`rtdnHandler` re-query Google Play and store token-hash records at
`entitlements/play_{sha256Prefix}` with `productId`, `status`,
`expiryTimestampMillis`, `autoRenewEnabled`, `verificationTimestampMillis`,
`source`, `packageName`, and server update metadata. These documents are not a
HumanV1 introductory period and must never be described as a Google Play trial.

Android caches the authenticated account-trial receipt in owner-bound private
preferences. It uses the server-observed clock monotonically and never creates a
trial locally. Paid verification is session-only because legacy persisted paid
records cannot be proven backend-issued.

## Current gap and backward-compatible projection

There is no user-addressable aggregate covering introductory access, Google
Play membership, time-limited support access, and audit history. Token-keyed
Play records also do not currently identify a Human owner. A future trusted
backend migration should add, without rewriting the existing records:

- `accounts/{uid}/entitlements/current`: schema version, normalized current
  state, effective/expiry timestamps, source, product, monotonically increasing
  revision, verification timestamp, and a short-lived signed receipt for
  offline clients;
- `accounts/{uid}/entitlementEvents/{eventId}`: append-only grant,
  verification, expiry, revocation, and projection events;
- explicit introductory evidence: `grantedAt`, `consumedAt`, `expiredAt`, and
  the existing `human_v1` source reference;
- support grant provenance: authorizing principal, reason code, effective and
  expiry timestamps, ticket/reference, and revocation metadata;
- a trusted purchase-token-to-UID/Human-ID link created only during
  authenticated purchase verification.

The projection must be derived transactionally by backend code. Browsers and
Android clients may read a signed/server receipt but may not manufacture,
extend, or mutate entitlement. Existing `human_v1` and token-hash documents
remain valid inputs during migration. Missing projection verification maps to
`VERIFICATION_UNAVAILABLE`, never to a new introductory period or a claimed
known expiry.
