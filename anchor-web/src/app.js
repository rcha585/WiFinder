import { ANCHOR_IDS, drawMarker, normalizeSessionId } from "./marker.js";
import { DISPLAY_PRESETS, normalizePreset, presetForAnchor } from "./presets.js";

const setup = document.querySelector("#setup");
const anchorScreen = document.querySelector("#anchor-screen");
const sessionInput = document.querySelector("#session-id");
const anchorSelect = document.querySelector("#anchor-id");
const presetSelect = document.querySelector("#display-preset");
const markerCanvas = document.querySelector("#marker");

for (const [key, preset] of Object.entries(DISPLAY_PRESETS)) {
  const option = document.createElement("option");
  option.value = key;
  option.textContent = preset.label;
  presetSelect.append(option);
}

document.querySelector("#create-session").addEventListener("click", () => {
  const bytes = new Uint8Array(4);
  crypto.getRandomValues(bytes);
  const sessionId = Array.from(bytes, (value) => value.toString(16).padStart(2, "0"))
    .join("")
    .toUpperCase();
  openAnchor(sessionId, "A", presetForAnchor("A"));
});

document.querySelector("#join-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const sessionId = normalizeSessionId(sessionInput.value);
  if (sessionId.length < 4) {
    sessionInput.setCustomValidity("Use at least four letters or digits.");
    sessionInput.reportValidity();
    return;
  }
  sessionInput.setCustomValidity("");
  openAnchor(sessionId, anchorSelect.value, presetSelect.value);
});

sessionInput.addEventListener("input", () => {
  sessionInput.value = normalizeSessionId(sessionInput.value);
  sessionInput.setCustomValidity("");
});

anchorSelect.addEventListener("change", () => {
  presetSelect.value = presetForAnchor(anchorSelect.value);
});

document.querySelector("#fullscreen").addEventListener("click", async () => {
  if (!document.fullscreenElement) {
    await anchorScreen.requestFullscreen();
  } else {
    await document.exitFullscreen();
  }
  requestAnimationFrame(routeFromLocation);
});

document.querySelector("#leave-session").addEventListener("click", () => {
  history.pushState({}, "", location.pathname);
  showSetup();
});

window.addEventListener("popstate", routeFromLocation);
window.addEventListener("resize", routeFromLocation);
routeFromLocation();

function routeFromLocation() {
  const params = new URLSearchParams(location.search);
  const sessionId = normalizeSessionId(params.get("session") || "");
  const anchorId = (params.get("anchor") || "").toUpperCase();
  const presetKey = normalizePreset(params.get("preset") || "", anchorId);
  if (sessionId.length >= 4 && ANCHOR_IDS.includes(anchorId)) {
    renderAnchor(sessionId, anchorId, presetKey);
  } else {
    showSetup();
  }
}

function openAnchor(sessionId, anchorId, presetKey = presetForAnchor(anchorId)) {
  const params = new URLSearchParams({
    session: normalizeSessionId(sessionId),
    anchor: anchorId,
    preset: normalizePreset(presetKey, anchorId),
  });
  history.pushState({}, "", `${location.pathname}?${params.toString()}`);
  routeFromLocation();
}

function renderAnchor(sessionId, anchorId, presetKey) {
  setup.hidden = true;
  anchorScreen.hidden = false;
  document.querySelector("#session-label").textContent = `Session ${sessionId}`;
  document.querySelector("#anchor-label").textContent = `WiFinder Anchor ${anchorId}`;
  document.title = `WiFinder Anchor ${anchorId} - ${sessionId}`;
  applyDisplayPreset(presetKey);
  drawMarker(markerCanvas, sessionId, anchorId);
  renderAnchorLinks(sessionId, anchorId);
  localStorage.setItem("wifinder-anchor-session", sessionId);
}

function renderAnchorLinks(sessionId, currentAnchorId) {
  const container = document.querySelector("#anchor-links");
  container.replaceChildren();
  for (const anchorId of ANCHOR_IDS) {
    const presetKey = presetForAnchor(anchorId);
    const preset = DISPLAY_PRESETS[presetKey];
    const url = new URL(location.href);
    url.search = new URLSearchParams({ session: sessionId, anchor: anchorId, preset: presetKey }).toString();
    const button = document.createElement("button");
    button.type = "button";
    button.className = anchorId === currentAnchorId ? "anchor-link current" : "anchor-link";
    button.textContent = anchorId === currentAnchorId
      ? `Anchor ${anchorId} - ${preset.label}`
      : `Copy Anchor ${anchorId} link (${preset.label})`;
    button.addEventListener("click", async () => {
      if (anchorId === currentAnchorId) return;
      await navigator.clipboard.writeText(url.toString());
      const previous = button.textContent;
      button.textContent = "Copied";
      setTimeout(() => { button.textContent = previous; }, 1200);
    });
    container.append(button);
  }
}

function showSetup() {
  anchorScreen.hidden = true;
  setup.hidden = false;
  document.title = "WiFinder Anchor";
  sessionInput.value = localStorage.getItem("wifinder-anchor-session") || "";
  anchorSelect.value = "A";
  presetSelect.value = presetForAnchor("A");
}

function applyDisplayPreset(presetKey) {
  const preset = DISPLAY_PRESETS[presetKey];
  const physicalWidthCm = currentPhysicalWidthCm(preset);
  const widthPx = Math.max(1, screen.width);
  const targetPixels = Math.round((preset.markerWidthCm / physicalWidthCm) * widthPx);
  document.documentElement.style.setProperty("--marker-size", `${targetPixels}px`);
  document.querySelector("#preset-label").textContent = preset.label;
  document.querySelector("#preset-note").textContent = preset.note;
  document.querySelector("#preset-width").textContent = preset.markerWidthCm.toFixed(1);
  requestAnimationFrame(() => updateRenderedWidth(preset));
}

function updateRenderedWidth(preset) {
  const renderedPixels = markerCanvas.getBoundingClientRect().width;
  const renderedCm = renderedPixels * currentPhysicalWidthCm(preset) / Math.max(1, screen.width);
  document.querySelector("#rendered-width").textContent = renderedCm.toFixed(1);
}

function currentPhysicalWidthCm(preset) {
  const landscape = screen.width >= screen.height;
  return landscape
    ? Math.max(preset.screenWidthCm, preset.screenHeightCm)
    : Math.min(preset.screenWidthCm, preset.screenHeightCm);
}
