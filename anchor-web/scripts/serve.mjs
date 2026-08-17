import { createReadStream, existsSync } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import { networkInterfaces } from "node:os";
import { extname, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const portArgument = process.argv.find((value) => /^\d+$/.test(value));
const port = Number(portArgument || process.env.PORT || 4173);
const mime = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8" };

const server = createServer(async (request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, `http://${request.headers.host}`).pathname);
  const requested = resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
  if (!requested.startsWith(root) || !existsSync(requested) || (await stat(requested)).isDirectory()) {
    response.writeHead(404).end("Not found");
    return;
  }
  response.writeHead(200, { "Content-Type": mime[extname(requested)] || "application/octet-stream", "Cache-Control": "no-store" });
  createReadStream(requested).pipe(response);
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Anchor Web: http://localhost:${port}`);
  for (const addresses of Object.values(networkInterfaces())) {
    for (const address of addresses || []) {
      if (address.family === "IPv4" && !address.internal) console.log(`LAN: http://${address.address}:${port}`);
    }
  }
});
