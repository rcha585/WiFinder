export const DISPLAY_PRESETS = {
  laptop14: {
    label: "14 inch laptop Chrome",
    note: "14 inch 16:10 screen, browser maximized",
    screenWidthCm: 30.2,
    screenHeightCm: 18.9,
    markerWidthCm: 14.0,
  },
  iphone14pro: {
    label: "iPhone 14 Pro Safari",
    note: "6.1 inch iPhone 14 Pro, portrait Safari",
    screenWidthCm: 7.15,
    screenHeightCm: 14.75,
    markerWidthCm: 5.4,
  },
  ipadpro13: {
    label: "13 inch iPad Pro Safari",
    note: "13 inch iPad Pro, Safari",
    screenWidthCm: 21.55,
    screenHeightCm: 28.16,
    markerWidthCm: 16.0,
  },
};

export const ANCHOR_PRESETS = {
  A: "laptop14",
  B: "iphone14pro",
  C: "ipadpro13",
  D: "laptop14",
};

export function presetForAnchor(anchorId) {
  return ANCHOR_PRESETS[anchorId] || ANCHOR_PRESETS.A;
}

export function normalizePreset(value, anchorId = "A") {
  return Object.hasOwn(DISPLAY_PRESETS, value) ? value : presetForAnchor(anchorId);
}
