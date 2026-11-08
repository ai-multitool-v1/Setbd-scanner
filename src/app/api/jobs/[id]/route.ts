import { NextResponse } from "next/server";
import { promises as fs } from "node:fs";
import { db } from "@/lib/db";
import { buildTree, jobDir } from "@/lib/storage";
import { findJob, reapStaleJobs, serializeJob } from "@/lib/job-service";

export const dynamic = "force-dynamic";

type Ctx = { params: Promise<{ id: string }> };

/** Job ids are cuids — anything else is rejected before touching the filesystem. */
function isSafeId(id: string): boolean {
  return /^[a-zA-Z0-9_-]{1,64}$/.test(id);
}

/** GET /api/jobs/:id — job metadata + file tree. */
export async function GET(_request: Request, ctx: Ctx) {
  const { id } = await ctx.params;
  if (!isSafeId(id)) return NextResponse.json({ error: "Scan not found" }, { status: 404 });
  await reapStaleJobs();

  const job = await findJob(id);
  if (!job) {
    return NextResponse.json({ error: "Scan not found" }, { status: 404 });
  }

  const tree = await buildTree(id);
  return NextResponse.json({ job: serializeJob(job), tree });
}

/** DELETE /api/jobs/:id — remove job row + all scraped files. */
export async function DELETE(_request: Request, ctx: Ctx) {
  const { id } = await ctx.params;
  if (!isSafeId(id)) return NextResponse.json({ error: "Scan not found" }, { status: 404 });

  const job = await findJob(id);
  if (!job) {
    return NextResponse.json({ error: "Scan not found" }, { status: 404 });
  }

  await fs.rm(jobDir(id), { recursive: true, force: true });
  await db.scrapeJob.delete({ where: { id } });
  return NextResponse.json({ ok: true });
}
