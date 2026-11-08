import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { reapStaleJobs, serializeJob } from "@/lib/job-service";

export const dynamic = "force-dynamic";

/** GET /api/jobs — all jobs, newest first (capped at 50). */
export async function GET() {
  await reapStaleJobs();
  const rows = await db.scrapeJob.findMany({
    orderBy: { createdAt: "desc" },
    take: 50,
  });
  return NextResponse.json({ jobs: rows.map(serializeJob) });
}
