export const ANCHOR_IDS = ["A", "B", "C", "D"];

export function normalizeSessionId(value) {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 16);
}

export function fnv1a(value) {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

export function markerBits(sessionId, anchorId, gridSize = 31) {
  let state = fnv1a(`${normalizeSessionId(sessionId)}:${anchorId}`) || 0x6d2b79f5;
  const bits = new Uint8Array(gridSize * gridSize);
  const nextBit = () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    state >>>= 0;
    return state & 1;
  };

  for (let y = 2; y < gridSize - 2; y += 1) {
    for (let x = 2; x < gridSize - 2; x += 1) {
      bits[y * gridSize + x] = nextBit();
    }
  }

  stampFinder(bits, gridSize, 4, 4, 7);
  stampFinder(bits, gridSize, gridSize - 11, 4, 7);
  stampFinder(bits, gridSize, 4, gridSize - 11, 7);
  stampOrientation(bits, gridSize, anchorId);
  return bits;
}

function stampFinder(bits, gridSize, left, top, size) {
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const edge = x === 0 || y === 0 || x === size - 1 || y === size - 1;
      const centre = x >= 2 && x <= size - 3 && y >= 2 && y <= size - 3;
      bits[(top + y) * gridSize + left + x] = edge || centre ? 1 : 0;
    }
  }
}

function stampOrientation(bits, gridSize, anchorId) {
  const code = Math.max(0, ANCHOR_IDS.indexOf(anchorId));
  const top = gridSize - 10;
  const left = gridSize - 10;
  for (let y = 0; y < 6; y += 1) {
    for (let x = 0; x < 6; x += 1) {
      const diagonal = x === y || x + y === 5;
      const encoded = y === 5 && ((code >> (x % 2)) & 1) === 1;
      bits[(top + y) * gridSize + left + x] = diagonal || encoded ? 1 : 0;
    }
  }
}

export function drawMarker(canvas, sessionId, anchorId) {
  const gridSize = 31;
  const bits = markerBits(sessionId, anchorId, gridSize);
  const context = canvas.getContext("2d", { alpha: false });
  const cell = canvas.width / gridSize;

  context.fillStyle = "#fff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = "#000";
  for (let y = 0; y < gridSize; y += 1) {
    for (let x = 0; x < gridSize; x += 1) {
      const border = x < 2 || y < 2 || x >= gridSize - 2 || y >= gridSize - 2;
      if (border || bits[y * gridSize + x] === 1) {
        context.fillRect(Math.floor(x * cell), Math.floor(y * cell), Math.ceil(cell), Math.ceil(cell));
      }
    }
  }
}
