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
const assert = __importStar(require("node:assert"));
const index_1 = require("../index");
describe("Workout Studio entitlement boundary", () => {
    it("allows native, live, custom-domain and finite preview callers", () => {
        assert.strictEqual((0, index_1.isAllowedWorkoutStudioOrigin)(undefined), true);
        assert.strictEqual((0, index_1.isAllowedWorkoutStudioOrigin)("https://studio.humanv1.com"), true);
        assert.strictEqual((0, index_1.isAllowedWorkoutStudioOrigin)("https://hv1-workout-studio.web.app"), true);
        assert.strictEqual((0, index_1.isAllowedWorkoutStudioOrigin)("https://hv1-workout-studio--studio-rc-f0i2z1mz.web.app"), true);
    });
    it("rejects lookalike and unrelated browser origins", () => {
        assert.strictEqual((0, index_1.isAllowedWorkoutStudioOrigin)("https://evil.example"), false);
        assert.strictEqual((0, index_1.isAllowedWorkoutStudioOrigin)("https://hv1-workout-studio.web.app.evil.example"), false);
        assert.strictEqual((0, index_1.isAllowedWorkoutStudioOrigin)("http://hv1-workout-studio.web.app"), false);
    });
});
//# sourceMappingURL=entitlement.contract.test.js.map