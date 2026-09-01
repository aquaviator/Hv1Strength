import * as assert from "node:assert";
import { isAllowedWorkoutStudioOrigin } from "../index";

describe("Workout Studio entitlement boundary", () => {
  it("allows native, live, custom-domain and finite preview callers", () => {
    assert.strictEqual(isAllowedWorkoutStudioOrigin(undefined), true);
    assert.strictEqual(isAllowedWorkoutStudioOrigin("https://studio.humanv1.com"), true);
    assert.strictEqual(isAllowedWorkoutStudioOrigin("https://hv1-workout-studio.web.app"), true);
    assert.strictEqual(isAllowedWorkoutStudioOrigin("https://hv1-workout-studio--studio-rc-f0i2z1mz.web.app"), true);
  });

  it("rejects lookalike and unrelated browser origins", () => {
    assert.strictEqual(isAllowedWorkoutStudioOrigin("https://evil.example"), false);
    assert.strictEqual(isAllowedWorkoutStudioOrigin("https://hv1-workout-studio.web.app.evil.example"), false);
    assert.strictEqual(isAllowedWorkoutStudioOrigin("http://hv1-workout-studio.web.app"), false);
  });
});
