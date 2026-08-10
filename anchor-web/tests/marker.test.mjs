import assert from "node:assert/strict";
import test from "node:test";
import { fnv1a, markerBits, normalizeSessionId } from "../src/marker.js";
import { DISPLAY_PRESETS, presetForAnchor } from "../src/presets.js";

test("normalizes session IDs shared with Android", () => {
  assert.equal(normalizeSessionId(" ab-cd_12 "), "ABCD12");
});

test("FNV-1a stays deterministic across browser and Android implementations", () => {
  assert.equal(fnv1a("ABCD1234:A"), 3535589556);
});

test("each anchor receives a stable distinct marker", () => {
  const a1 = markerBits("ABCD1234", "A");
  const a2 = markerBits("ABCD1234", "A");
  const b = markerBits("ABCD1234", "B");
  assert.deepEqual(a1, a2);
  assert.notDeepEqual(a1, b);
});

test("default device presets cover the required anchors", () => {
  assert.equal(DISPLAY_PRESETS[presetForAnchor("A")].markerWidthCm, 14.0);
  assert.equal(DISPLAY_PRESETS[presetForAnchor("B")].markerWidthCm, 5.4);
  assert.equal(DISPLAY_PRESETS[presetForAnchor("C")].markerWidthCm, 16.0);
});
