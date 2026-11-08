"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { useToast } from "@/hooks/use-toast";
import { deleteJob, getJob, listJobs, startScrape, zipUrl } from "@/lib/api";
import { formatBytes, type ScrapeJob } from "@/lib/types";
import { EditorView } from "@/components/scanner/editor/editor-view";
import { HeroSection } from "@/components/scanner/hero-section";
import { JobsSection } from "@/components/scanner/jobs-section";
import { SiteFooter } from "@/components/scanner/site-footer";
import { SiteHeader } from "@/components/scanner/site-header";
import { SplashScreen } from "@/components/scanner/splash-screen";

const SPLASH_SEEN_KEY = "sbd-splash-seen";
const POLL_INTERVAL_MS = 1200;

type View = "home" | "editor";

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Unknown error";
}

/**
 * Main client orchestrator for SetBD Scanner.
 * Holds all view state in one page: home (hero + history) <-> full-screen editor.
 */
export function ScannerApp() {
  const { toast } = useToast();

  const [view, setView] = useState<View>("home");
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [jobs, setJobs] = useState<ScrapeJob[]>([]);
  const [jobsLoading, setJobsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [trackedJobId, setTrackedJobId] = useState<string | null>(null);
  const [showSplash, setShowSplash] = useState(false);

  /* ---------------- Splash (once per browser session) ---------------- */

  useEffect(() => {
    try {
      if (!window.sessionStorage.getItem(SPLASH_SEEN_KEY)) setShowSplash(true);
    } catch {
      setShowSplash(true); // storage unavailable — show anyway
    }
  }, []);

  const finishSplash = useCallback(() => {
    setShowSplash(false);
    try {
      window.sessionStorage.setItem(SPLASH_SEEN_KEY, "1");
    } catch {
      /* ignore */
    }
  }, []);

  /* ---------------- Jobs data ---------------- */

  useEffect(() => {
    let cancelled = false;
    listJobs()
      .then((data) => {
        if (!cancelled) setJobs(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          toast({
            title: "Could not load scan history",
            description: errorMessage(err),
            variant: "destructive",
          });
        }
      })
      .finally(() => {
        if (!cancelled) setJobsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [toast]);

  const runningJob = useMemo(() => jobs.find((j) => j.status === "running") ?? null, [jobs]);
  const runningId = runningJob?.id ?? null;

  /* ---------------- Polling while a job is running ---------------- */

  useEffect(() => {
    if (!runningId) return;
    let cancelled = false;
    // Guards against overlapping interval ticks handling the terminal state twice.
    let finished = false;

    const tick = async () => {
      if (finished) return;
      try {
        const { job } = await getJob(runningId);
        if (cancelled || finished) return;

        if (job.status === "running") {
          setJobs((prev) => prev.map((j) => (j.id === job.id ? job : j)));
          return;
        }

        // Terminal state: notify + refresh the full list once BEFORE the polled
        // state clears runningId (which would cancel this effect).
        finished = true;
        if (job.status === "completed") {
          toast({
            title: "Scan completed",
            description: `${job.fileCount} files • ${formatBytes(job.totalSize)}`,
          });
        } else {
          toast({
            title: "Scan failed",
            description: job.message ?? job.url,
            variant: "destructive",
          });
        }
        try {
          const fresh = await listJobs();
          if (!cancelled) setJobs(fresh);
        } catch {
          // Fall back to applying the polled job so the UI does not stay "running".
          if (!cancelled) setJobs((prev) => prev.map((j) => (j.id === job.id ? job : j)));
        }
      } catch {
        /* transient error — keep polling silently */
      }
    };

    const interval = window.setInterval(() => void tick(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [runningId, toast]);

  /**
   * Job shown in the hero progress card: the tracked one (kept visible after
   * completion so the user can open the editor / download), falling back to
   * any currently running job.
   */
  const progressJob = useMemo(() => {
    if (trackedJobId) return jobs.find((j) => j.id === trackedJobId) ?? null;
    return runningJob;
  }, [jobs, trackedJobId, runningJob]);

  /* ---------------- Handlers ---------------- */

  const handleStartScrape = useCallback(
    async (url: string) => {
      if (submitting) return;
      setSubmitting(true);
      try {
        const job = await startScrape(url);
        setJobs((prev) => [job, ...prev.filter((j) => j.id !== job.id)]);
        setTrackedJobId(job.id);
        toast({ title: "Scan started", description: job.url });
      } catch (err) {
        toast({
          title: "Could not start scan",
          description: errorMessage(err),
          variant: "destructive",
        });
      } finally {
        setSubmitting(false);
      }
    },
    [submitting, toast]
  );

  const refreshJobs = useCallback(async () => {
    setRefreshing(true);
    try {
      const fresh = await listJobs();
      setJobs(fresh);
    } catch (err) {
      toast({
        title: "Refresh failed",
        description: errorMessage(err),
        variant: "destructive",
      });
    } finally {
      setRefreshing(false);
    }
  }, [toast]);

  const handleOpenEditor = useCallback((jobId: string) => {
    setActiveJobId(jobId);
    setView("editor");
  }, []);

  const goHome = useCallback(() => {
    setView("home");
    setActiveJobId(null);
  }, []);

  const handleDelete = useCallback(
    async (jobId: string) => {
      try {
        await deleteJob(jobId);
        setJobs((prev) => prev.filter((j) => j.id !== jobId));
        setTrackedJobId((prev) => (prev === jobId ? null : prev));
        if (activeJobId === jobId) {
          setActiveJobId(null);
          setView("home");
        }
        toast({ title: "Scan deleted" });
      } catch (err) {
        toast({
          title: "Delete failed",
          description: errorMessage(err),
          variant: "destructive",
        });
      }
    },
    [activeJobId, toast]
  );

  const handleDownloadZip = useCallback((jobId: string) => {
    const anchor = document.createElement("a");
    anchor.href = zipUrl(jobId);
    anchor.download = `setbd-scan-${jobId}.zip`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  }, []);

  /* ---------------- Render ---------------- */

  return (
    <>
      <AnimatePresence>
        {showSplash && <SplashScreen key="splash" onFinish={finishSplash} />}
      </AnimatePresence>

      <div className="flex min-h-screen flex-col">
        <SiteHeader onHome={goHome} />

        <main className="flex flex-1 flex-col">
          <AnimatePresence mode="wait" initial={false}>
            {view === "home" ? (
              <motion.div
                key="home"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="flex flex-1 flex-col"
              >
                <HeroSection
                  onStartScrape={handleStartScrape}
                  isSubmitting={submitting}
                  runningJob={progressJob}
                  onOpenEditor={handleOpenEditor}
                  onDownloadZip={handleDownloadZip}
                  onRetry={handleStartScrape}
                />
                <JobsSection
                  jobs={jobs}
                  loading={jobsLoading || refreshing}
                  onRefresh={() => void refreshJobs()}
                  onOpenEditor={handleOpenEditor}
                  onDownloadZip={handleDownloadZip}
                  onDelete={handleDelete}
                />
              </motion.div>
            ) : (
              <motion.div
                key="editor"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                className="flex flex-1 flex-col"
              >
                {activeJobId && (
                  <EditorView jobId={activeJobId} onClose={goHome} onDownloadZip={handleDownloadZip} />
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </main>

        <SiteFooter />
      </div>
    </>
  );
}
