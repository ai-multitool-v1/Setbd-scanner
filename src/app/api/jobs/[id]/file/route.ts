import { NextResponse } from "next/server";
import { promises as fs } from "node:fs";
import { db } from "@/lib/db";
import { isTextFile, jobDir, measureJobDir, mimeFor, safeResolve } from "@/lib/storage";
import { findJob } from "@/lib/job-service";

export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ id: string }> };

function isSafeId(id: string): boolean {
  return /^[a-zA-Z0-9_-]{1,64}$/.test(id);
}

/** GET /api/jobs/:id/file?path=assets/css/main.css — read file content. */
export async function GET(request: Request, ctx: Ctx) {
  const { id } = await ctx.params;
  if (!isSafeId(id)) return NextResponse.json({ error: "Scan not found" }, { status: 404 });

  const rel = new URL(request.url).searchParams.get("path") ?? "";
  const abs = safeResolve(id, rel);
  if (!abs) return NextResponse.json({ error: "Invalid file path" }, { status: 400 });

  let stat;
  try {
    stat = await fs.stat(abs);
  } catch {
    return NextResponse.json({ error: "File not found" }, { status: 404 });
  }
  if (!stat.isFile()) {
    return NextResponse.json({ error: "Not a file" }, { status: 400 });
  }

  const fileName = rel.split("/").pop() ?? rel;
  const isText = isTextFile(fileName) && stat.size < 4 * 1024 * 1024;
  const content = isText ? await fs.readFile(abs, "utf8") : null;

  return NextResponse.json({
    path: rel,
    isText,
    size: stat.size,
    mime: mimeFor(fileName),
    content,
  });
}

/** PUT /api/jobs/:id/file  { path, content } — save edited text file. */
export async function PUT(request: Request, ctx: Ctx) {
  const { id } = await ctx.params;
  if (!isSafeId(id)) return NextResponse.json({ error: "Scan not found" }, { status: 404 });

  const job = await findJob(id);
  if (!job) return NextResponse.json({ error: "Scan not found" }, { status: 404 });

  let body: { path?: unknown; content?: unknown };
  try {
    body = (await request.json()) as { path?: unknown; content?: unknown };
  } catch {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  const rel = typeof body.path === "string" ? body.path : "";
  const content = typeof body.content === "string" ? body.content : null;
  if (!rel || content === null) {
    return NextResponse.json({ error: "path and content are required" }, { status: 400 });
  }

  const abs = safeResolve(id, rel);
  if (!abs) return NextResponse.json({ error: "Invalid file path" }, { status: 400 });

  const fileName = rel.split("/").pop() ?? rel;
  if (!isTextFile(fileName)) {
    return NextResponse.json({ error: "Binary files cannot be edited" }, { status: 400 });
  }
  if (Buffer.byteLength(content, "utf8") > 4 * 1024 * 1024) {
    return NextResponse.json({ error: "File too large to save (4 MB max)" }, { status: 413 });
  }

  await fs.writeFile(abs, content, "utf8");

  // keep job totals in sync after edits
  const { files, totalSize } = await measureJobDir(id);
  await db.scrapeJob
    .update({
      where: { id },
      data: { fileCount: files, totalSize },
    })
    .catch(() => undefined);

  return NextResponse.json({ ok: true });
}
