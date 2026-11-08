import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { isBlockedHost, runScrape } from "@/lib/scraper";
import { serializeJob } from "@/lib/job-service";

export const dynamic = "force-dynamic";

/**
 * POST /api/scrape  { url: string }
 * Creates a job row and kicks off the scraper in the background.
 */
export async function POST(request: Request) {
  let url: string;
  try {
    const body = (await request.json()) as { url?: unknown };
    url = typeof body.url === "string" ? body.url.trim() : "";
  } catch {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }

  if (!url) {
    return NextResponse.json({ error: "URL is required" }, { status: 400 });
  }
  if (!/^https?:\/\//i.test(url)) {
    url = `https://${url}`;
  }

  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return NextResponse.json({ error: "That does not look like a valid URL" }, { status: 400 });
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    return NextResponse.json({ error: "Only http(s) URLs are supported" }, { status: 400 });
  }
  if (isBlockedHost(parsed.hostname)) {
    return NextResponse.json(
      { error: "Local and private addresses are not allowed" },
      { status: 400 }
    );
  }

  const job = await db.scrapeJob.create({
    data: { url: parsed.href, status: "running", stage: "fetching", progress: 0 },
  });

  // Fire-and-forget: the engine updates the DB row as it progresses.
  void runScrape(job.id, parsed.href);

  return NextResponse.json({ job: serializeJob(job) }, { status: 201 });
}
