import { db } from "./db";
import type { ScrapeJob } from "./types";
import type { ScrapeJob as DbJob } from "@prisma/client";

/** A running job older than this is considered dead (server restart etc.). */
const STALE_MS = 15 * 60 * 1000;

export function serializeJob(job: DbJob): ScrapeJob {
  return {
    id: job.id,
    url: job.url,
    title: job.title,
    status: job.status as ScrapeJob["status"],
    stage: job.stage as ScrapeJob["stage"],
    progress: job.progress,
    message: job.message,
    fileCount: job.fileCount,
    totalSize: job.totalSize,
    createdAt: job.createdAt.toISOString(),
    updatedAt: job.updatedAt.toISOString(),
    completedAt: job.completedAt ? job.completedAt.toISOString() : null,
  };
}

/** Mark dead "running" jobs as failed so the UI never hangs on them. */
export async function reapStaleJobs(): Promise<void> {
  try {
    await db.scrapeJob.updateMany({
      where: { status: "running", updatedAt: { lt: new Date(Date.now() - STALE_MS) } },
      data: { status: "failed", stage: "error", message: "Scan timed out or server restarted" },
    });
  } catch {
    /* non-fatal */
  }
}

export async function findJob(id: string): Promise<DbJob | null> {
  return db.scrapeJob.findUnique({ where: { id } });
}
