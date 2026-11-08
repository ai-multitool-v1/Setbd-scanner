"use client";

import Image from "next/image";
import { AnimatePresence } from "framer-motion";
import { History, RefreshCw } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { JobCard } from "@/components/scanner/job-card";
import type { ScrapeJob } from "@/lib/types";

export interface JobsSectionProps {
  jobs: ScrapeJob[];
  loading: boolean;
  onRefresh: () => void;
  onOpenEditor: (jobId: string) => void;
  onDownloadZip: (jobId: string) => void;
  onDelete: (jobId: string) => void | Promise<void>;
}

export function JobsSection({
  jobs,
  loading,
  onRefresh,
  onOpenEditor,
  onDownloadZip,
  onDelete,
}: JobsSectionProps) {
  return (
    <section id="history" className="mx-auto w-full max-w-6xl scroll-mt-20 px-4 pb-16">
      <div className="mb-4 flex items-center gap-2">
        <History className="size-5 text-primary" aria-hidden />
        <h2 className="text-xl font-bold tracking-tight">Scan History</h2>
        <Badge variant="secondary" className="tabular-nums">
          {jobs.length}
        </Badge>
        <Button
          variant="ghost"
          size="icon"
          className="ml-auto size-11 rounded-full"
          onClick={onRefresh}
          disabled={loading}
          aria-label="Refresh scan history"
        >
          <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} aria-hidden />
        </Button>
      </div>

      {loading && jobs.length === 0 ? (
        /* Loading skeletons */
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-hidden>
          {[0, 1, 2].map((i) => (
            <Card key={i} className="glass flex flex-col gap-4 p-4">
              <div className="flex items-center gap-3">
                <Skeleton className="size-9 rounded-lg" />
                <Skeleton className="h-4 flex-1" />
              </div>
              <Skeleton className="h-3 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
            </Card>
          ))}
        </div>
      ) : jobs.length === 0 ? (
        /* Empty state */
        <Card className="flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed p-10 text-center">
          <Image
            src="/images/scanner.gif"
            alt=""
            width={160}
            height={100}
            className="h-24 w-auto opacity-90"
          />
          <p className="text-sm text-muted-foreground">
            No scans yet — paste a URL above and hit{" "}
            <span className="font-medium text-foreground">Scan Now</span>.
          </p>
        </Card>
      ) : (
        /* Scrollable card grid */
        <div className="firefox-scroll max-h-[640px] overflow-y-auto pr-1">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <AnimatePresence initial={false}>
              {jobs.map((job, i) => (
                <JobCard
                  key={job.id}
                  job={job}
                  index={i}
                  onOpenEditor={onOpenEditor}
                  onDownloadZip={onDownloadZip}
                  onDelete={onDelete}
                />
              ))}
            </AnimatePresence>
          </div>
        </div>
      )}
    </section>
  );
}
