// Shared types between frontend and backend for SetBD Scanner

export type JobStatus = "running" | "completed" | "failed";

export type JobStage =
  | "fetching" // downloading the main HTML
  | "mapping" // parsing & collecting asset links
  | "downloading" // fetching css/js/images/fonts
  | "optimizing" // compressing images
  | "done"
  | "error";

export interface ScrapeJob {
  id: string;
  url: string;
  title: string | null;
  status: JobStatus;
  stage: JobStage;
  progress: number; // 0 - 100
  message: string | null;
  fileCount: number;
  totalSize: number; // bytes
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface FileNode {
  name: string;
  path: string; // relative path inside the scrape folder e.g. "assets/css/main.css"
  type: "file" | "dir";
  size: number;
  children?: FileNode[];
}

export interface FileContent {
  path: string;
  isText: boolean;
  size: number;
  mime: string;
  content: string | null; // for text files, raw text; null for binary
}

export const STAGE_LABELS: Record<JobStage, string> = {
  fetching: "Fetching HTML",
  mapping: "Mapping assets",
  downloading: "Downloading assets",
  optimizing: "Optimizing images",
  done: "Packaging complete",
  error: "Failed",
};

export const STAGE_ORDER: JobStage[] = [
  "fetching",
  "mapping",
  "downloading",
  "optimizing",
  "done",
];

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const val = bytes / Math.pow(1024, i);
  return `${val >= 100 ? Math.round(val) : val.toFixed(1)} ${units[i]}`;
}
