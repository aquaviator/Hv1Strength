"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const assert_1 = __importDefault(require("assert"));
const index_1 = require("../index");
describe("Hv1 Platform Backend Verification Unit Tests", () => {
    const now = 1753460000000;
    it("should map SUBSCRIPTION_STATE_ACTIVE with trial flag to TRIAL_ACTIVE", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_ACTIVE", now + 86400000 * 30, true, now);
        assert_1.default.strictEqual(status, "TRIAL_ACTIVE");
    });
    it("should map SUBSCRIPTION_STATE_ACTIVE without trial flag to ACTIVE", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_ACTIVE", now + 86400000 * 365, false, now);
        assert_1.default.strictEqual(status, "ACTIVE");
    });
    it("should map SUBSCRIPTION_STATE_CANCELED with future expiry to CANCELLED_ACTIVE", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_CANCELED", now + 86400000 * 10, false, now);
        assert_1.default.strictEqual(status, "CANCELLED_ACTIVE");
    });
    it("should map SUBSCRIPTION_STATE_CANCELED with past expiry to EXPIRED", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_CANCELED", now - 1000, false, now);
        assert_1.default.strictEqual(status, "EXPIRED");
    });
    it("should map SUBSCRIPTION_STATE_IN_GRACE_PERIOD to GRACE_PERIOD", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", now + 86400000 * 7, false, now);
        assert_1.default.strictEqual(status, "GRACE_PERIOD");
    });
    it("should map SUBSCRIPTION_STATE_ON_HOLD to ACCOUNT_HOLD", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_ON_HOLD", now + 86400000 * 30, false, now);
        assert_1.default.strictEqual(status, "ACCOUNT_HOLD");
    });
    it("should map SUBSCRIPTION_STATE_PAUSED to PAUSED", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_PAUSED", now + 86400000 * 30, false, now);
        assert_1.default.strictEqual(status, "PAUSED");
    });
    it("should map SUBSCRIPTION_STATE_EXPIRED to EXPIRED", () => {
        const status = (0, index_1.mapPlayStateToEntitlementStatus)("SUBSCRIPTION_STATE_EXPIRED", now - 1000, false, now);
        assert_1.default.strictEqual(status, "EXPIRED");
    });
    it("should generate deterministic doc IDs for purchase tokens", () => {
        const token = "test_purchase_token_123456789";
        const docId1 = (0, index_1.getPurchaseDocId)(token);
        const docId2 = (0, index_1.getPurchaseDocId)(token);
        assert_1.default.strictEqual(docId1, docId2);
        assert_1.default.ok(docId1.includes("play_"));
    });
});
//# sourceMappingURL=index.test.js.map