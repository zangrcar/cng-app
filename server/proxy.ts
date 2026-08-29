import { createReadStream, existsSync, statSync } from "node:fs";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { extname, join, normalize } from "node:path";

const PORT = process.env.CNG_PROXY_ONLY ? 5174 : 4173;
const MIMIT_CSV_BASE = "https://www.mimit.gov.it/images/exportCSV";
const MIMIT_API_BASE = "https://carburanti.mise.gov.it/ospzApi";
const DIST_DIR = join(process.cwd(), "dist");

const mimeTypes: Record<string, string> = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".ico": "image/x-icon",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".webmanifest": "application/manifest+json",
};

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(body));
}

async function proxyMimit(request: IncomingMessage, response: ServerResponse, pathname: string): Promise<void> {
  let upstreamUrl: string | null = null;
  if (request.method === "GET" && pathname === "/proxy/mimit/stations") {
    upstreamUrl = `${MIMIT_CSV_BASE}/anagrafica_impianti_attivi.csv`;
  } else if (request.method === "GET" && pathname === "/proxy/mimit/prices") {
    upstreamUrl = `${MIMIT_CSV_BASE}/prezzo_alle_8.csv`;
  } else {
    const detailMatch = pathname.match(/^\/proxy\/mimit\/station\/(\d+)$/);
    if (request.method === "GET" && detailMatch) {
      upstreamUrl = `${MIMIT_API_BASE}/registry/servicearea/${detailMatch[1]}`;
    }
  }

  if (!upstreamUrl) {
    sendJson(response, 404, { error: "Unknown MIMIT proxy route" });
    return;
  }

  try {
    const upstream = await fetch(upstreamUrl, {
      headers: { accept: pathname.includes("station/") ? "application/json" : "text/csv" },
      signal: AbortSignal.timeout(30_000),
    });
    const body = Buffer.from(await upstream.arrayBuffer());
    response.writeHead(upstream.status, {
      "cache-control": "no-store",
      "content-type": upstream.headers.get("content-type") ?? "application/octet-stream",
    });
    response.end(body);
  } catch (error) {
    sendJson(response, 502, {
      error: "MIMIT is unavailable",
      detail: error instanceof Error ? error.message : "Unknown provider error",
    });
  }
}

function serveStatic(response: ServerResponse, pathname: string): void {
  const relativePath = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  const safePath = normalize(relativePath).replace(/^(\.\.(\/|\\|$))+/, "");
  let filePath = join(DIST_DIR, safePath);
  if (!existsSync(filePath) || !statSync(filePath).isFile()) filePath = join(DIST_DIR, "index.html");

  if (!existsSync(filePath)) {
    sendJson(response, 503, { error: "Production build missing. Run npm run build first." });
    return;
  }

  const extension = extname(filePath);
  const requiresRevalidation = extension === ".html" || extension === ".webmanifest" || filePath.endsWith("sw.js");
  response.writeHead(200, {
    "cache-control": requiresRevalidation ? "no-cache" : "public, max-age=31536000, immutable",
    "content-type": mimeTypes[extension] ?? "application/octet-stream",
  });
  createReadStream(filePath).pipe(response);
}

const server = createServer(async (request, response) => {
  const pathname = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`).pathname;
  if (pathname.startsWith("/proxy/mimit/")) {
    await proxyMimit(request, response, pathname);
    return;
  }
  if (process.env.CNG_PROXY_ONLY) {
    sendJson(response, 404, { error: "Proxy route not found" });
    return;
  }
  serveStatic(response, pathname);
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(process.env.CNG_PROXY_ONLY ? `MIMIT proxy: http://127.0.0.1:${PORT}` : `CNG Route: http://127.0.0.1:${PORT}`);
});
