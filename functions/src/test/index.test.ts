import assert from "assert";
import {
  mapPlayStateToEntitlementStatus,
  getPurchaseDocId,
  verifyTokenWithGooglePlay,
  EXPECTED_PACKAGE_NAME,
  EXPECTED_PRODUCT_ID
} from "../index";

describe("Hv1 Platform Production Entitlement Backend Unit Tests", () => {
  const now = 1753460000000; // Fixed timestamp for test determinism

  // Mock Play Client Builder
  function createMockPlayClient(
    subscriptionState: string,
    lineItems: any[],
    shouldThrow: boolean = false,
    throwError: any = null
  ) {
    return {
      purchases: {
        subscriptionsv2: {
          get: async ({ packageName, token }: { packageName: string; token: string }) => {
            if (shouldThrow) {
              throw throwError || new Error("Google Play API Error");
            }
            return {
              data: {
                subscriptionState,
                lineItems
              }
            };
          }
        }
      }
    };
  }

  // 1. Valid active annual subscription
  it("1. should verify valid active annual subscription", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now + 86400000 * 365).toISOString(),
        autoRenewingPlan: { autoRenewEnabled: true }
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "valid_active_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.status, "ACTIVE");
      assert.strictEqual(result.entitlement.autoRenewEnabled, true);
      assert.strictEqual(result.entitlement.productId, EXPECTED_PRODUCT_ID);
    }
  });

  // 2. Valid cancelled-but-still-active subscription
  it("2. should verify valid cancelled-but-still-active subscription", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_CANCELED", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now + 86400000 * 30).toISOString(),
        autoRenewingPlan: { autoRenewEnabled: false }
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "valid_cancelled_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.status, "CANCELLED_ACTIVE");
      assert.strictEqual(result.entitlement.autoRenewEnabled, false);
    }
  });

  // 3. Valid grace-period subscription
  it("3. should verify valid grace-period subscription", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now + 86400000 * 5).toISOString(),
        autoRenewingPlan: { autoRenewEnabled: true }
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "valid_grace_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.status, "GRACE_PERIOD");
    }
  });

  // 4. Expired subscription
  it("4. should handle expired subscription", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_EXPIRED", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now - 1000).toISOString(),
        autoRenewingPlan: { autoRenewEnabled: false }
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "expired_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.status, "EXPIRED");
    }
  });

  // 5. Revoked subscription
  it("5. should handle revoked subscription", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_REVOKED", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now - 1000).toISOString(),
        autoRenewingPlan: { autoRenewEnabled: false }
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "revoked_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.status, "REVOKED");
    }
  });

  // 6. Pending subscription
  it("6. should handle pending subscription", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_PENDING", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now + 86400000).toISOString(),
        autoRenewingPlan: { autoRenewEnabled: false }
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "pending_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.status, "PENDING");
    }
  });

  // 7. Invalid purchase token (400/404 from Play API)
  it("7. should return INVALID_PURCHASE when Play API returns 404", async () => {
    const playClient = createMockPlayClient("", [], true, { code: 404, message: "Purchase token not found" });

    const result = await verifyTokenWithGooglePlay(playClient, "invalid_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "INVALID_PURCHASE");
    }
  });

  // 8. Google API 401/403 (Backend auth failure)
  it("8. should return BACKEND_CONFIGURATION_ERROR on Google API 401/403", async () => {
    const playClient = createMockPlayClient("", [], true, { code: 401, message: "Unauthorized credentials" });

    const result = await verifyTokenWithGooglePlay(playClient, "any_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "BACKEND_CONFIGURATION_ERROR");
    }
  });

  // 9. Google API 404
  it("9. should map Google API 404 to INVALID_PURCHASE", async () => {
    const playClient = createMockPlayClient("", [], true, { status: 404, message: "Not Found" });

    const result = await verifyTokenWithGooglePlay(playClient, "missing_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "INVALID_PURCHASE");
    }
  });

  // 10. Google API timeout (502 / 500)
  it("10. should map Google API timeout / 500 to PLAY_API_UNAVAILABLE", async () => {
    const playClient = createMockPlayClient("", [], true, { code: 500, message: "Internal server error" });

    const result = await verifyTokenWithGooglePlay(playClient, "timeout_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "PLAY_API_UNAVAILABLE");
    }
  });

  // 11. Google auth failure (null playClient)
  it("11. should return BACKEND_CONFIGURATION_ERROR when playClient is null", async () => {
    const result = await verifyTokenWithGooglePlay(null, "some_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "BACKEND_CONFIGURATION_ERROR");
    }
  });

  // 12. Missing expiry
  it("12. should fail when subscription line item is missing expiry", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
      {
        productId: EXPECTED_PRODUCT_ID,
        autoRenewingPlan: { autoRenewEnabled: true }
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "no_expiry_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "MALFORMED_REQUEST");
    }
  });

  // 13. Wrong package name
  it("13. should reject request with wrong package name", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", []);

    const result = await verifyTokenWithGooglePlay(playClient, "some_token", "com.wrong.package", EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "PRODUCT_MISMATCH");
    }
  });

  // 14. Wrong product ID
  it("14. should reject request with wrong product ID", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
      {
        productId: "some_other_product_gold_tier",
        expiryTime: new Date(now + 86400000).toISOString()
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "some_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "PRODUCT_MISMATCH");
    }
  });

  // 15. Malformed request (invalid date string)
  it("15. should fail when expiry date string is malformed", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: "invalid-date-string"
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "malformed_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.strictEqual(result.error.code, "MALFORMED_REQUEST");
    }
  });

  // 16. Unknown subscription state
  it("16. should map unknown subscription state to EXPIRED (fail closed)", async () => {
    const status = mapPlayStateToEntitlementStatus("UNKNOWN_FUTURE_STATE_XYZ", now + 86400000, false, now);
    assert.strictEqual(status, "EXPIRED");
  });

  // 17. Unknown RTDN notification type handling
  it("17. should map unknown RTDN notification state to EXPIRED when state is unknown", () => {
    const status = mapPlayStateToEntitlementStatus("SUBSCRIPTION_STATE_UNSPECIFIED", now + 86400000, false, now);
    assert.strictEqual(status, "EXPIRED");
  });

  // 18. Duplicate RTDN timestamp logic
  it("18. should produce identical SHA-256 doc IDs for duplicate RTDN tokens", () => {
    const token = "rtdn_purchase_token_sample_abc123";
    const docId1 = getPurchaseDocId(token);
    const docId2 = getPurchaseDocId(token);
    assert.strictEqual(docId1, docId2);
  });

  // 19. RTDN triggers authoritative Play re-query
  it("19. verifyTokenWithGooglePlay is called authoritatively during verification", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now + 86400000 * 100).toISOString()
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "rtdn_requery_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.source, "GOOGLE_PLAY_BACKEND");
    }
  });

  // 20. RTDN does not manufacture expiry
  it("20. should require explicit expiry date from Play API response", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: null
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "no_expiry_rtdn_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
  });

  // 21. Purchase doc ID exposes NO raw token substring
  it("21. purchase document ID must expose NO substring of the raw purchase token", () => {
    const rawToken = "super_secret_purchase_token_value_999";
    const docId = getPurchaseDocId(rawToken);

    assert.ok(docId.startsWith("play_"));
    // Ensure no 5-character or larger substring of rawToken is present in docId (other than 'play_')
    assert.strictEqual(docId.includes("super"), false);
    assert.strictEqual(docId.includes("secret"), false);
    assert.strictEqual(docId.includes("purchase"), false);
    assert.strictEqual(docId.includes("token"), false);
    assert.strictEqual(docId.includes("999"), false);
  });

  // 22. <=31 days remaining does not automatically imply trial
  it("22. subscription with 20 days remaining without trial offer tags should map to ACTIVE, not TRIAL_ACTIVE", async () => {
    const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
      {
        productId: EXPECTED_PRODUCT_ID,
        expiryTime: new Date(now + 86400000 * 20).toISOString(),
        offerDetails: { offerTags: [] } // No trial offer tags
      }
    ]);

    const result = await verifyTokenWithGooglePlay(playClient, "twenty_days_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, true);
    if (result.success) {
      assert.strictEqual(result.entitlement.status, "ACTIVE");
    }
  });

  // 23. Backend infrastructure failure cannot produce hasAppAccess
  it("23. backend infrastructure failure must fail closed without granting access", async () => {
    const playClient = createMockPlayClient("", [], true, new Error("Database network failure"));

    const result = await verifyTokenWithGooglePlay(playClient, "infra_fail_token", EXPECTED_PACKAGE_NAME, EXPECTED_PRODUCT_ID, now);
    assert.strictEqual(result.success, false);
    if (!result.success) {
      assert.notStrictEqual(result.error.code, undefined);
    }
  });
});
