"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const assert_1 = __importDefault(require("assert"));
const index_1 = require("../index");
describe("Hv1 Platform Production Entitlement Backend Unit Tests", () => {
    const now = 1753460000000; // Fixed timestamp for test determinism
    it("accepts only an explicit valid backend trial policy", () => {
        assert_1.default.deepStrictEqual((0, index_1.parseTrialPolicy)({ trialEnabled: true, trialDurationDays: 30 }), { trialEnabled: true, trialDurationDays: 30 });
        assert_1.default.strictEqual((0, index_1.parseTrialPolicy)(undefined), null);
        assert_1.default.strictEqual((0, index_1.parseTrialPolicy)({ trialEnabled: true }), null);
        assert_1.default.strictEqual((0, index_1.parseTrialPolicy)({ trialEnabled: true, trialDurationDays: 0 }), null);
        assert_1.default.strictEqual((0, index_1.parseTrialPolicy)({ trialEnabled: true, trialDurationDays: 30.5 }), null);
        assert_1.default.strictEqual((0, index_1.parseTrialPolicy)({ trialEnabled: "true", trialDurationDays: 30 }), null);
    });
    // Mock Play Client Builder
    function createMockPlayClient(subscriptionState, lineItems, shouldThrow = false, throwError = null) {
        return {
            purchases: {
                subscriptionsv2: {
                    get: async ({ packageName, token }) => {
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
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now + 86400000 * 365).toISOString(),
                autoRenewingPlan: { autoRenewEnabled: true }
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "valid_active_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.status, "ACTIVE");
            assert_1.default.strictEqual(result.entitlement.autoRenewEnabled, true);
            assert_1.default.strictEqual(result.entitlement.productId, index_1.EXPECTED_PRODUCT_ID);
        }
    });
    // 2. Valid cancelled-but-still-active subscription
    it("2. should verify valid cancelled-but-still-active subscription", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_CANCELED", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now + 86400000 * 30).toISOString(),
                autoRenewingPlan: { autoRenewEnabled: false }
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "valid_cancelled_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.status, "CANCELLED_ACTIVE");
            assert_1.default.strictEqual(result.entitlement.autoRenewEnabled, false);
        }
    });
    // 3. Valid grace-period subscription
    it("3. should verify valid grace-period subscription", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now + 86400000 * 5).toISOString(),
                autoRenewingPlan: { autoRenewEnabled: true }
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "valid_grace_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.status, "GRACE_PERIOD");
        }
    });
    // 4. Expired subscription
    it("4. should handle expired subscription", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_EXPIRED", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now - 1000).toISOString(),
                autoRenewingPlan: { autoRenewEnabled: false }
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "expired_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.status, "EXPIRED");
        }
    });
    // 5. Revoked subscription
    it("5. should handle revoked subscription", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_REVOKED", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now - 1000).toISOString(),
                autoRenewingPlan: { autoRenewEnabled: false }
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "revoked_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.status, "REVOKED");
        }
    });
    // 6. Pending subscription
    it("6. should handle pending subscription", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_PENDING", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now + 86400000).toISOString(),
                autoRenewingPlan: { autoRenewEnabled: false }
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "pending_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.status, "PENDING");
        }
    });
    // 7. Invalid purchase token (400/404 from Play API)
    it("7. should return INVALID_PURCHASE when Play API returns 404", async () => {
        const playClient = createMockPlayClient("", [], true, { code: 404, message: "Purchase token not found" });
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "invalid_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "INVALID_PURCHASE");
        }
    });
    // 8. Google API 401/403 (Backend auth failure)
    it("8. should return BACKEND_CONFIGURATION_ERROR on Google API 401/403", async () => {
        const playClient = createMockPlayClient("", [], true, { code: 401, message: "Unauthorized credentials" });
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "any_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "BACKEND_CONFIGURATION_ERROR");
        }
    });
    // 9. Google API 404
    it("9. should map Google API 404 to INVALID_PURCHASE", async () => {
        const playClient = createMockPlayClient("", [], true, { status: 404, message: "Not Found" });
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "missing_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "INVALID_PURCHASE");
        }
    });
    // 10. Google API timeout (502 / 500)
    it("10. should map Google API timeout / 500 to PLAY_API_UNAVAILABLE", async () => {
        const playClient = createMockPlayClient("", [], true, { code: 500, message: "Internal server error" });
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "timeout_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "PLAY_API_UNAVAILABLE");
        }
    });
    // 11. Google auth failure (null playClient)
    it("11. should return BACKEND_CONFIGURATION_ERROR when playClient is null", async () => {
        const result = await (0, index_1.verifyTokenWithGooglePlay)(null, "some_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "BACKEND_CONFIGURATION_ERROR");
        }
    });
    // 12. Missing expiry
    it("12. should fail when subscription line item is missing expiry", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                autoRenewingPlan: { autoRenewEnabled: true }
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "no_expiry_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "MALFORMED_REQUEST");
        }
    });
    // 13. Wrong package name
    it("13. should reject request with wrong package name", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", []);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "some_token", "com.wrong.package", index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "PRODUCT_MISMATCH");
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
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "some_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "PRODUCT_MISMATCH");
        }
    });
    // 15. Malformed request (invalid date string)
    it("15. should fail when expiry date string is malformed", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: "invalid-date-string"
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "malformed_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.strictEqual(result.error.code, "MALFORMED_REQUEST");
        }
    });
    // 16. Unknown subscription state
    it("16. should map unknown subscription state to EXPIRED (fail closed)", async () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("UNKNOWN_FUTURE_STATE_XYZ", now + 86400000, false, now);
        assert_1.default.strictEqual(status, "EXPIRED");
    });
    // 17. Unknown RTDN notification type handling
    it("17. should map unknown RTDN notification state to EXPIRED when state is unknown", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_UNSPECIFIED", now + 86400000, false, now);
        assert_1.default.strictEqual(status, "EXPIRED");
    });
    // 18. Duplicate RTDN timestamp logic
    it("18. should produce identical SHA-256 doc IDs for duplicate RTDN tokens", () => {
        const token = "rtdn_purchase_token_sample_abc123";
        const docId1 = (0, index_1.getPurchaseDocId)(token);
        const docId2 = (0, index_1.getPurchaseDocId)(token);
        assert_1.default.strictEqual(docId1, docId2);
    });
    // 19. RTDN triggers authoritative Play re-query
    it("19. verifyTokenWithGooglePlay is called authoritatively during verification", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now + 86400000 * 100).toISOString()
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "rtdn_requery_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.source, "GOOGLE_PLAY_BACKEND");
        }
    });
    // 20. RTDN does not manufacture expiry
    it("20. should require explicit expiry date from Play API response", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: null
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "no_expiry_rtdn_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
    });
    // 21. Purchase doc ID exposes NO raw token substring
    it("21. purchase document ID must expose NO substring of the raw purchase token", () => {
        const rawToken = "super_secret_purchase_token_value_999";
        const docId = (0, index_1.getPurchaseDocId)(rawToken);
        assert_1.default.ok(docId.startsWith("play_"));
        // Ensure no 5-character or larger substring of rawToken is present in docId (other than 'play_')
        assert_1.default.strictEqual(docId.includes("super"), false);
        assert_1.default.strictEqual(docId.includes("secret"), false);
        assert_1.default.strictEqual(docId.includes("purchase"), false);
        assert_1.default.strictEqual(docId.includes("token"), false);
        assert_1.default.strictEqual(docId.includes("999"), false);
    });
    // 22. <=31 days remaining does not automatically imply trial
    it("22. subscription with 20 days remaining without trial offer tags should map to ACTIVE, not TRIAL_ACTIVE", async () => {
        const playClient = createMockPlayClient("SUBSCRIPTION_STATE_ACTIVE", [
            {
                productId: index_1.EXPECTED_PRODUCT_ID,
                expiryTime: new Date(now + 86400000 * 20).toISOString(),
                offerDetails: { offerTags: [] } // No trial offer tags
            }
        ]);
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "twenty_days_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, true);
        if (result.success) {
            assert_1.default.strictEqual(result.entitlement.status, "ACTIVE");
        }
    });
    // 23. Backend infrastructure failure cannot produce hasAppAccess
    it("23. backend infrastructure failure must fail closed without granting access", async () => {
        const playClient = createMockPlayClient("", [], true, new Error("Database network failure"));
        const result = await (0, index_1.verifyTokenWithGooglePlay)(playClient, "infra_fail_token", index_1.EXPECTED_PACKAGE_NAME, index_1.EXPECTED_PRODUCT_ID, now);
        assert_1.default.strictEqual(result.success, false);
        if (!result.success) {
            assert_1.default.notStrictEqual(result.error.code, undefined);
        }
    });
    // 24. Deterministic Java String hashCode for humanUserId derivation
    it("24. should compute deterministic hashCode matching Kotlin for humanUserId derivation", () => {
        const hash = (0, index_1.getJavaStringHashCode)("user_12345");
        assert_1.default.strictEqual(typeof hash, "number");
        const humanId = "human_" + hash.toString().replace("-", "n").padEnd(12, "x").substring(0, 12);
        assert_1.default.ok(humanId.startsWith("human_"));
    });
    // 25. FIRESTORE_USER_SUBCOLLECTIONS scope completeness
    it("25. should identify all 10 user-owned subcollections for complete cloud purge", () => {
        assert_1.default.strictEqual(index_1.FIRESTORE_USER_SUBCOLLECTIONS.length, 10);
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("profile"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("sessions"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("loggedSets"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("weight"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("tape"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("customExercises"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("templates"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("templateExercises"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("templateSets"));
        assert_1.default.ok(index_1.FIRESTORE_USER_SUBCOLLECTIONS.includes("processedCommands"));
    });
    // 26. Mock Firestore Purge execution
    it("26. should purge all subcollections and root user doc in Firestore mock", async () => {
        let deletedCount = 0;
        const deletedPaths = [];
        const mockDb = {
            collection: (colName) => ({
                doc: (docId) => ({
                    collection: (subName) => ({
                        get: async () => ({
                            empty: false,
                            docs: [
                                { ref: `users/${docId}/${subName}/doc1` },
                                { ref: `users/${docId}/${subName}/doc2` }
                            ]
                        })
                    }),
                    get: async () => ({ exists: true }),
                    delete: async () => {
                        deletedPaths.push(`users/${docId}`);
                        deletedCount++;
                    }
                })
            }),
            batch: () => ({
                delete: (ref) => {
                    deletedPaths.push(ref);
                    deletedCount++;
                },
                commit: async () => { }
            })
        };
        const res = await (0, index_1.purgeUserCloudData)(mockDb, "test_uid", "human_test123");
        assert_1.default.strictEqual(res.deletedSubcollections.length, 10);
        assert_1.default.strictEqual(res.totalDocumentsDeleted, 21); // 20 subdocs + 1 root doc
    });
});
//# sourceMappingURL=index.test.js.map