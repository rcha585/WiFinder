import { ANCHOR_IDS, drawMarker, normalizeSessionId } from "./marker.js";

const setup = document.querySelector("#setup");
const anchorScreen = document.querySelector("#anchor-screen");
const sessionInput = document.querySelector("#session-id");
const anchorSelect = document.querySelector("#anchor-id");
const markerCanvas = document.querySelector("#marker");

document.querySelector("#create-session").addEventListener("click", () => {
  const bytes = new Uint8Array(4);
  crypto.getRandomValues(bytes);
  const sessionId = Array.from(bytes, (value) => value.toString(16).padStart(2, "0"))
    .join("")
    .toUpperCase();
  openAnchor(sessionId, "A");
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
  openAnchor(sessionId, anchorSelect.value);
});

sessionInput.addEventListener("input", () => {
  sessionInput.value = normalizeSessionId(sessionInput.value);
  sessionInput.setCustomValidity("");
});

document.querySelector("#fullscreen").addEventListener("click", async () => {
  if (!document.fullscreenElement) {
    await anchorScreen.requestFullscreen();
  } else {
    await document.exitFullscreen();
  }
});

document.querySelector("#leave-session").addEventListener("click", () => {
  history.pushState({}, "", location.pathname);
  showSetup();
});

window.addEventListener("popstate", routeFromLocation);
routeFromLocation();

function routeFromLocation() {
  const params = new URLSearchParams(location.search);
  const sessionId = normalizeSessionId(params.get("session") || "");
  const anchorId = (params.get("anchor") || "").toUpperCase();
  if (sessionId.length >= 4 && ANCHOR_IDS.includes(anchorId)) {
    renderAnchor(sessionId, anchorId);
  } else {
    showSetup();
  }
}

function openAnchor(sessionId, anchorId) {
  const params = new URLSearchParams({ session: normalizeSessionId(sessionId), anchor: anchorId });
  history.pushState({}, "", `${location.pathname}?${params.toString()}`);
  routeFromLocation();
}

function renderAnchor(sessionId, anchorId) {
  setup.hidden = true;
  anchorScreen.hidden = false;
  document.querySelector("#session-label").textContent = `Session ${sessionId}`;
  document.querySelector("#anchor-label").textContent = `WiFinder Anchor ${anchorId}`;
  document.title = `WiFinder Anchor ${anchorId} · ${sessionId}`;
  drawMarker(markerCanvas, sessionId, anchorId);
  renderAnchorLinks(sessionId, anchorId);
  localStorage.setItem("wifinder-anchor-session", sessionId);
}

function renderAnchorLinks(sessionId, currentAnchorId) {
  const container = document.querySelector("#anchor-links");
  container.replaceChildren();
  for (const anchorId of ANCHOR_IDS) {
    const url = new URL(location.href);
    url.search = new URLSearchParams({ session: sessionId, anchor: anchorId }).toString();
    const button = document.createElement("button");
    button.type = "button";
    button.className = anchorId === currentAnchorId ? "anchor-link current" : "anchor-link";
    button.textContent = anchorId === currentAnchorId ? `Anchor ${anchorId} · this device` : `Copy Anchor ${anchorId} link`;
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
}
