import { NextResponse } from "next/server";
import { promises as fs } from "node:fs";
import path from "node:path";
import JSZip from "jszip";
import { jobDir } from "@/lib/storage";
import { findJob } from "@/lib/job-service";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

type Ctx = { params: Promise<{ id: string }> };

function isSafeId(id: string): boolean {
  return /^[a-zA-Z0-9_-]{1,64}$/.test(id);
}

/** GET /api/jobs/:id/zip — stream the whole scraped site as a ZIP. */
export async function GET(_request: Request, ctx: Ctx) {
  const { id } = await ctx.params;
  if (!isSafeId(id)) return NextResponse.json({ error: "Scan not found" }, { status: 404 });

  const job = await findJob(id);
  if (!job) return NextResponse.json({ error: "Scan not found" }, { status: 404 });

  const dir = jobDir(id);
  try {
    await fs.access(dir);
  } catch {
    return NextResponse.json({ error: "Scraped files are gone" }, { status: 404 });
  }

  const zip = new JSZip();
  const used = new Set<string>();

  const addDir = async (abs: string, rel: string): Promise<void> => {
    const entries = await fs.readdir(abs, { withFileTypes: true });
    for (const entry of entries) {
      const childAbs = path.join(abs, entry.name);
      const childRel = rel ? `${rel}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        await addDir(childAbs, childRel);
      } else if (entry.isFile()) {
        // ZIP paths must be unique — scraped names already are, but stay safe
        let zipPath = childRel;
        if (used.has(zipPath)) zipPath = `${childRel}-${used.size}`;
        used.add(zipPath);
        zip.file(zipPath, await fs.readFile(childAbs));
      }
    }
  };

  await addDir(dir, "");

  const host = new URL(job.url).hostname.replace(/^www\./, "").replace(/[^a-z0-9.-]/gi, "");
  const buf = await zip.generateAsync({
    type: "nodebuffer",
    compression: "DEFLATE",
    compressionOptions: { level: 6 },
  });

  return new NextResponse(new Uint8Array(buf), {
    status: 200,
    headers: {
      "Content-Type": "application/zip",
      "Content-Length": String(buf.length),
      "Content-Disposition": `attachment; filename="setbd-${host || "site"}-${id}.zip"`,
      "Cache-Control": "no-store",
    },
  });
}
