"use client";

import { useState, useSyncExternalStore, type FormEvent } from "react";
import Image from "next/image";
import { AnimatePresence, motion, useReducedMotion, type Variants } from "framer-motion";
import { useTheme } from "next-themes";
import {
  FileArchive,
  FileCode2,
  FolderTree,
  Globe,
  ImageDown,
  Loader2,
  MonitorSmartphone,
  Radar,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrapeProgress } from "@/components/scanner/scrape-progress";
import type { ScrapeJob } from "@/lib/types";
import { cn } from "@/lib/utils";

export interface HeroSectionProps {
  /** Called with a normalized URL (https:// is added when missing). */
  onStartScrape: (url: string) => void;
  isSubmitting: boolean;
  /** Tracked job shown in the progress card below the form. */
  runningJob: ScrapeJob | null;
  onOpenEditor: (jobId: string) => void;
  onDownloadZip: (jobId: string) => void;
  onRetry: (url: string) => void;
}

const FEATURES = [
  { icon: FileCode2, label: "HTML / CSS / JS" },
  { icon: FolderTree, label: "Structured assets" },
  { icon: ImageDown, label: "Smart image compression" },
  { icon: FileArchive, label: "One-click ZIP" },
  { icon: MonitorSmartphone, label: "Installable PWA" },
] as const;

const containerVariants: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08, delayChildren: 0.1 } },
};

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 18 },
  show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 260, damping: 26 } },
};

const chipListVariants: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06 } },
};

const chipVariants: Variants = {
  hidden: { opacity: 0, y: 10, scale: 0.95 },
  show: { opacity: 1, y: 0, scale: 1, transition: { type: "spring", stiffness: 300, damping: 22 } },
};

/** Hydration-safe mounted flag (false during SSR, true on the client). */
const emptySubscribe = () => () => {};
const useMounted = () => useSyncExternalStore(emptySubscribe, () => true, () => false);

export function HeroSection({
  onStartScrape,
  isSubmitting,
  runningJob,
  onOpenEditor,
  onDownloadZip,
  onRetry,
}: HeroSectionProps) {
  const { resolvedTheme } = useTheme();
  const reducedMotion = useReducedMotion();
  const mounted = useMounted();
  const [url, setUrl] = useState("");

  // defaultTheme is "dark" — keep the dark hero before hydration settles.
  const isDark = mounted ? resolvedTheme !== "light" : true;

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const trimmed = url.trim();
    if (!trimmed) return;
    const normalized = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
    onStartScrape(normalized);
  };

  return (
    <section className="relative overflow-hidden">
      {/* Background layers (cross-fade between dark / light brand art) */}
      <div className="absolute inset-0" aria-hidden>
        <Image
          src="/images/bg-hero-dark.webp"
          alt=""
          fill
          priority
          sizes="100vw"
          className={cn(
            "object-cover transition-opacity duration-700",
            isDark ? "opacity-100" : "opacity-0"
          )}
        />
        <Image
          src="/images/bg-hero-light.webp"
          alt=""
          fill
          sizes="100vw"
          className={cn(
            "object-cover transition-opacity duration-700",
            !isDark ? "opacity-100" : "opacity-0"
          )}
        />
        <div className="absolute inset-0 bg-gradient-to-b from-background/60 via-background/80 to-background" />
        <div className="absolute inset-0 bg-grid" />
      </div>

      <div className="relative mx-auto w-full max-w-4xl px-4 py-16 text-center md:py-24">
        <motion.div
          variants={reducedMotion ? undefined : containerVariants}
          initial={reducedMotion ? false : "hidden"}
          animate="show"
          className="flex flex-col items-center gap-5"
        >
          <motion.div variants={itemVariants}>
            <Badge
              variant="outline"
              className="gap-2 border-primary/40 bg-primary/10 px-3 py-1 text-[11px] font-semibold tracking-wider text-primary"
            >
              <span className="relative flex size-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75" />
                <span className="relative inline-flex size-2 rounded-full bg-primary" />
              </span>
              PUBLIC WEB SCANNER — v1.0
            </Badge>
          </motion.div>

          <motion.h1
            variants={itemVariants}
            className="text-4xl font-extrabold tracking-tight md:text-6xl"
          >
            Scrape any website.
            <br />
            <span className="text-gradient">Own the source.</span>
          </motion.h1>

          <motion.p
            variants={itemVariants}
            className="max-w-2xl text-balance text-sm text-muted-foreground md:text-base"
          >
            SetBD Scanner downloads the publicly accessible HTML, CSS, JS, images and fonts of any
            site into a clean folder structure — then lets you edit the source live, preview it
            instantly, and export everything as a ZIP archive.
          </motion.p>

          <motion.form
            variants={itemVariants}
            noValidate
            onSubmit={handleSubmit}
            className="glass mt-2 flex w-full max-w-2xl items-center gap-2 rounded-full border border-border/70 p-2 pl-4 shadow-lg shadow-primary/5 transition-shadow focus-within:ring-2 focus-within:ring-ring/40"
          >
            <Globe className="size-5 shrink-0 text-primary" aria-hidden />
            <Input
              type="url"
              inputMode="url"
              required
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://example.com"
              aria-label="Website URL to scan"
              className="h-11 min-w-0 flex-1 border-0 bg-transparent px-1 text-base shadow-none focus-visible:ring-0"
            />
            <Button
              type="submit"
              size="lg"
              disabled={isSubmitting}
              className="h-11 shrink-0 rounded-full px-5 md:px-6"
            >
              {isSubmitting ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <Radar className="size-4" aria-hidden />
              )}
              <span className="hidden sm:inline">{isSubmitting ? "Scanning…" : "Scan Now"}</span>
              <span className="sm:hidden">Scan</span>
            </Button>
          </motion.form>

          <motion.ul variants={chipListVariants} className="flex flex-wrap items-center justify-center gap-2">
            {FEATURES.map((feature) => (
              <motion.li key={feature.label} variants={chipVariants}>
                <span className="glass inline-flex items-center gap-1.5 rounded-full border border-border/60 px-3 py-1.5 text-xs text-muted-foreground">
                  <feature.icon className="size-3.5 text-primary" aria-hidden />
                  {feature.label}
                </span>
              </motion.li>
            ))}
          </motion.ul>
        </motion.div>

        {/* Live scan progress card */}
        <AnimatePresence>
          {runningJob && (
            <motion.div
              key={runningJob.id}
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 12 }}
              transition={{ duration: 0.35, ease: "easeOut" }}
              className="mt-10 text-left"
            >
              <ScrapeProgress
                job={runningJob}
                onOpenEditor={onOpenEditor}
                onDownloadZip={onDownloadZip}
                onRetry={onRetry}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </section>
  );
}
