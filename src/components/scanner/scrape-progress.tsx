"use client";

import { useMemo, useState } from "react";
import Image from "next/image";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  CheckCircle2,
  Circle,
  Code2,
  DownloadCloud,
  Eye,
  FileArchive,
  Globe,
  ImageDown,
  Network,
  RotateCcw,
  type LucideIcon,
} from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { PreviewDialog } from "@/components/scanner/editor/preview-dialog";
import { STAGE_LABELS, STAGE_ORDER, formatBytes, type JobStage, type ScrapeJob } from "@/lib/types";
import { cn } from "@/lib/utils";

export interface ScrapeProgressProps {
  job: ScrapeJob;
  onOpenEditor: (jobId: string) => void;
  onDownloadZip: (jobId: string) => void;
  /** Retry a failed scan with the same URL. */
  onRetry: (url: string) => void;
}

const STAGE_ICONS: Partial<Record<JobStage, LucideIcon>> = {
  fetching: Globe,
  mapping: Network,
  downloading: DownloadCloud,
  optimizing: ImageDown,
  done: CheckCircle2,
};

function StatusBadge({ status }: { status: ScrapeJob["status"] }) {
  if (status === "completed") {
    return (
      <Badge variant="outline" className="border-emerald-500/40 bg-emerald-500/10 text-emerald-500">
        Completed
      </Badge>
    );
  }
  if (status === "failed") {
    return <Badge variant="destructive">Failed</Badge>;
  }
  return (
    <Badge variant="outline" className="gap-1.5 border-primary/40 bg-primary/10 text-primary">
      <span className="relative flex size-1.5">
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75" />
        <span className="relative inline-flex size-1.5 rounded-full bg-primary" />
      </span>
      Running
    </Badge>
  );
}

export function ScrapeProgress({ job, onOpenEditor, onDownloadZip, onRetry }: ScrapeProgressProps) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const running = job.status === "running";

  const currentIdx = useMemo(() => {
    if (job.status === "completed") return STAGE_ORDER.length - 1;
    if (job.status === "failed") return -1;
    return Math.max(STAGE_ORDER.indexOf(job.stage), 0);
  }, [job.status, job.stage]);

  return (
    <div className="glass relative overflow-hidden rounded-2xl border border-border/60 p-4 shadow-lg shadow-primary/5 sm:p-5">
      {/* Scanline sweep while running */}
      {running && (
        <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden rounded-2xl">
          <div className="animate-scanline absolute inset-y-0 left-0 w-1/3 bg-gradient-to-r from-transparent via-primary/[0.07] to-transparent" />
        </div>
      )}

      {/* Target + status */}
      <div className="relative flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <Globe className="size-4" aria-hidden />
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium" title={job.url}>
              {job.url}
            </p>
            {job.title && (
              <p className="truncate text-xs text-muted-foreground" title={job.title}>
                {job.title}
              </p>
            )}
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {running && (
            <Image
              src="/images/scanner.gif"
              alt=""
              width={64}
              height={64}
              className="hidden h-14 w-auto rounded-lg opacity-80 sm:block"
            />
          )}
          <StatusBadge status={job.status} />
        </div>
      </div>

      {/* Pipeline steps */}
      <ol className="relative mt-4 flex items-start gap-0.5">
        {STAGE_ORDER.map((stage, i) => {
          const Icon = STAGE_ICONS[stage] ?? Circle;
          const done = i < currentIdx || (i === currentIdx && job.status === "completed");
          const current = i === currentIdx && running;
          const upcoming = !done && !current;
          return (
            <li key={stage} className="flex min-w-0 flex-1 items-start last:flex-none">
              <div className="flex min-w-[52px] flex-col items-center gap-1.5">
                <motion.div
                  initial={false}
                  animate={{ scale: done ? 1 : 0.96 }}
                  transition={{ type: "spring", stiffness: 400, damping: 20 }}
                  className={cn(
                    "flex size-8 items-center justify-center rounded-full border transition-colors",
                    done && "border-primary bg-primary text-primary-foreground",
                    current && "border-primary bg-primary/10 text-primary ring-2 ring-primary/30",
                    upcoming && "border-border bg-muted/40 text-muted-foreground/50"
                  )}
                >
                  <Icon className="size-4" aria-hidden />
                </motion.div>
                {current && (
                  <div className="h-0.5 w-8 overflow-hidden rounded-full bg-primary/20">
                    <div className="animate-scanline h-full w-1/2 rounded-full bg-primary" />
                  </div>
                )}
                <span
                  className={cn(
                    "max-w-[72px] truncate text-center text-[10px] leading-tight sm:text-[11px]",
                    done && "text-primary",
                    current && "text-foreground",
                    upcoming && "text-muted-foreground/60"
                  )}
                >
                  {STAGE_LABELS[stage]}
                </span>
              </div>
              {i < STAGE_ORDER.length - 1 && (
                <div
                  aria-hidden
                  className={cn(
                    "mt-4 h-0.5 min-w-2 flex-1 rounded-full transition-colors",
                    i < currentIdx ? "bg-primary" : "bg-border"
                  )}
                />
              )}
            </li>
          );
        })}
      </ol>

      {/* Progress bar + live counts */}
      <div className="relative mt-4 flex items-center gap-3">
        <Progress value={job.progress} className="h-2 flex-1" aria-label={`Scan progress ${Math.round(job.progress)}%`} />
        <span className="w-10 shrink-0 text-right text-xs font-semibold tabular-nums text-primary">
          {Math.round(job.progress)}%
        </span>
      </div>
      <p className="relative mt-1.5 text-xs text-muted-foreground">
        <span className="tabular-nums">{job.fileCount}</span> files •{" "}
        <span className="tabular-nums">{formatBytes(job.totalSize)}</span>
      </p>

      {/* Completed actions */}
      {job.status === "completed" && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
          className="relative mt-4 flex flex-wrap items-center gap-2 border-t border-border/50 pt-4"
        >
          <span className="mr-auto flex items-center gap-2 text-sm font-medium text-emerald-500">
            <motion.span
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: "spring", stiffness: 400, damping: 15, delay: 0.1 }}
              className="flex size-6 items-center justify-center rounded-full bg-emerald-500/15"
            >
              <CheckCircle2 className="size-4" aria-hidden />
            </motion.span>
            Scan complete
          </span>
          <Button size="sm" className="h-10" onClick={() => onOpenEditor(job.id)}>
            <Code2 aria-hidden /> Open Editor
          </Button>
          <Button size="sm" variant="secondary" className="h-10" onClick={() => onDownloadZip(job.id)}>
            <FileArchive aria-hidden /> Download ZIP
          </Button>
          <Button size="sm" variant="ghost" className="h-10" onClick={() => setPreviewOpen(true)}>
            <Eye aria-hidden /> Live Preview
          </Button>
        </motion.div>
      )}

      {/* Failure */}
      {job.status === "failed" && (
        <div className="relative mt-4 flex flex-col gap-3 border-t border-border/50 pt-4">
          <Alert variant="destructive">
            <AlertTriangle aria-hidden />
            <AlertTitle>Scan failed</AlertTitle>
            <AlertDescription>{job.message || "The scraper could not process this URL."}</AlertDescription>
          </Alert>
          <div>
            <Button size="sm" variant="outline" className="h-10" onClick={() => onRetry(job.url)}>
              <RotateCcw aria-hidden /> Retry scan
            </Button>
          </div>
        </div>
      )}

      <PreviewDialog open={previewOpen} onOpenChange={setPreviewOpen} jobId={job.id} />
    </div>
  );
}
