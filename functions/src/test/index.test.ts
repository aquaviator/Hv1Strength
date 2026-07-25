import assert from "assert";
import { mapPlayStateToEntitlementStatus, getPurchaseDocId } from "../index";

describe("Hv1 Platform Backend Verification Unit Tests", () => {
  const now = 1753460000000;

  it("should map SUBSCRIPTION_STATE_ACTIVE with trial flag to TRIAL_ACTIVE", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_ACTIVE",
      now + 86400000 * 30,
      true,
      now
    );
    assert.strictEqual(status, "TRIAL_ACTIVE");
  });

  it("should map SUBSCRIPTION_STATE_ACTIVE without trial flag to ACTIVE", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_ACTIVE",
      now + 86400000 * 365,
      false,
      now
    );
    assert.strictEqual(status, "ACTIVE");
  });

  it("should map SUBSCRIPTION_STATE_CANCELED with future expiry to CANCELLED_ACTIVE", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_CANCELED",
      now + 86400000 * 10,
      false,
      now
    );
    assert.strictEqual(status, "CANCELLED_ACTIVE");
  });

  it("should map SUBSCRIPTION_STATE_CANCELED with past expiry to EXPIRED", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_CANCELED",
      now - 1000,
      false,
      now
    );
    assert.strictEqual(status, "EXPIRED");
  });

  it("should map SUBSCRIPTION_STATE_IN_GRACE_PERIOD to GRACE_PERIOD", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
      now + 86400000 * 7,
      false,
      now
    );
    assert.strictEqual(status, "GRACE_PERIOD");
  });

  it("should map SUBSCRIPTION_STATE_ON_HOLD to ACCOUNT_HOLD", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_ON_HOLD",
      now + 86400000 * 30,
      false,
      now
    );
    assert.strictEqual(status, "ACCOUNT_HOLD");
  });

  it("should map SUBSCRIPTION_STATE_PAUSED to PAUSED", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_PAUSED",
      now + 86400000 * 30,
      false,
      now
    );
    assert.strictEqual(status, "PAUSED");
  });

  it("should map SUBSCRIPTION_STATE_EXPIRED to EXPIRED", () => {
    const status = mapPlayStateToEntitlementStatus(
      "SUBSCRIPTION_STATE_EXPIRED",
      now - 1000,
      false,
      now
    );
    assert.strictEqual(status, "EXPIRED");
  });

  it("should generate deterministic doc IDs for purchase tokens", () => {
    const token = "test_purchase_token_123456789";
    const docId1 = getPurchaseDocId(token);
    const docId2 = getPurchaseDocId(token);

    assert.strictEqual(docId1, docId2);
    assert.ok(docId1.includes("play_"));
  });
});

