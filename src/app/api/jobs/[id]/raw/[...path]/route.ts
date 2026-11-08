import { NextResponse } from "next/server";
import { promises as fs } from "node:fs";
import { isTextFile, mimeFor, safeResolve } from "@/lib/storage";

export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ id: string; path: string[] }> };

function isSafeId(id: string): boolean {
  return /^[a-zA-Z0-9_-]{1,64}$/.test(id);
}

/**
 * GET /api/jobs/:id/raw/<path> — serve a scraped file with its real mime type.
 * Used for live preview (iframe) + image previews, so relative asset paths
 * inside the scraped HTML resolve correctly against this route.
 */
export async function GET(_request: Request, ctx: Ctx) {
  const { id, path: segments } = await ctx.params;

  const rel = (segments ?? []).join("/");
  const target = rel || "index.html";
  const abs = safeResolve(id, target);
  if (!abs) return NextResponse.json({ error: "Invalid path" }, { status: 400 });

  let stat;
  try {
    stat = await fs.stat(abs);
  } catch {
    return NextResponse.json({ error: "File not found" }, { status: 404 });
  }
  if (stat.isDirectory()) {
    const indexAbs = safeResolve(id, `${target}/index.html`);
    if (indexAbs) {
      return serveFile(indexAbs);
    }
    return NextResponse.json({ error: "Not a file" }, { status: 400 });
  }

  return serveFile(abs);
}

async function serveFile(abs: string): Promise<NextResponse> {
  const fileName = abs.split("/").pop() ?? "file";
  const mime = mimeFor(fileName);
  const data = await fs.readFile(abs);
  const isInlineText = isTextFile(fileName);

  return new NextResponse(new Uint8Array(data), {
    status: 200,
    headers: {
      "Content-Type": mime,
      "Content-Length": String(data.length),
      "Content-Disposition": "inline",
      "X-Content-Type-Options": "nosniff",
      "Cache-Control": isInlineText ? "no-store" : "private, max-age=60",
    },
  });
}
