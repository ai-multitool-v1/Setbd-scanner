// Frontend API client for SetBD Scanner
import type { ScrapeJob, FileNode, FileContent } from "./types";

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const data = (await res.json().catch(() => null)) as { error?: string } | null;
    throw new Error(data?.error || `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

/** Start scraping a website */
export async function startScrape(url: string): Promise<ScrapeJob> {
  return json(
    await fetch("/api/scrape", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url }),
    })
  );
}

/** List all scrape jobs (newest first) */
export async function listJobs(): Promise<ScrapeJob[]> {
  const data = await json<{ jobs: ScrapeJob[] }>(await fetch("/api/jobs"));
  return data.jobs;
}

/** Get one job + its file tree */
export async function getJob(id: string): Promise<{ job: ScrapeJob; tree: FileNode }> {
  return json(await fetch(`/api/jobs/${id}`, { cache: "no-store" }));
}

/** Read a scraped file (text content or binary marker) */
export async function getFile(id: string, path: string): Promise<FileContent> {
  return json(
    await fetch(`/api/jobs/${id}/file?path=${encodeURIComponent(path)}`, { cache: "no-store" })
  );
}

/** Save edited file content */
export async function saveFile(id: string, path: string, content: string): Promise<void> {
  await json(
    await fetch(`/api/jobs/${id}/file`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path, content }),
    })
  );
}

/** Delete a job and its files */
export async function deleteJob(id: string): Promise<void> {
  await json(await fetch(`/api/jobs/${id}`, { method: "DELETE" }));
}

/** Direct zip download URL */
export function zipUrl(id: string): string {
  return `/api/jobs/${id}/zip`;
}

/** Raw file preview URL (served with correct mime so relative assets resolve) */
export function rawUrl(id: string, path = "index.html"): string {
  return `/api/jobs/${id}/raw/${path}`;
}
