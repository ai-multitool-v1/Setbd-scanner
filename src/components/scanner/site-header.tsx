"use client";

import { useSyncExternalStore } from "react";
import Image from "next/image";
import { Github, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const GITHUB_URL = "https://github.com/ai-multitool-v1/Setbd-scanner";

/** Hydration-safe mounted flag (false during SSR, true on the client). */
const emptySubscribe = () => () => {};
const useMounted = () => useSyncExternalStore(emptySubscribe, () => true, () => false);

export interface SiteHeaderProps {
  /** Navigate back to the home view. */
  onHome: () => void;
}

export function SiteHeader({ onHome }: SiteHeaderProps) {
  const { resolvedTheme, setTheme } = useTheme();
  const mounted = useMounted();

  // defaultTheme is "dark" — render dark-consistent UI before hydration settles.
  const isDark = mounted && resolvedTheme === "dark";

  return (
    <header className="glass sticky top-0 z-40 w-full border-b border-border/60">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between gap-3 px-4">
        <button
          type="button"
          onClick={onHome}
          aria-label="SetBD Scanner home"
          className="flex min-w-0 items-center gap-2.5 rounded-lg py-1 pr-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
        >
          <span className="relative block size-9 shrink-0 overflow-hidden rounded-lg">
            <Image src="/images/logo.webp" alt="" fill sizes="36px" className="object-cover" />
          </span>
          <span className="truncate text-base">
            <span className="font-bold">SetBD</span>{" "}
            <span className="text-gradient font-semibold">Scanner</span>
          </span>
        </button>

        <div className="flex shrink-0 items-center gap-1.5">
          <Badge
            variant="outline"
            className="hidden gap-1.5 border-primary/40 bg-primary/5 text-[11px] text-primary sm:inline-flex"
          >
            <span className="relative flex size-1.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75" />
              <span className="relative inline-flex size-1.5 rounded-full bg-primary" />
            </span>
            PWA ready
          </Badge>

          <Button
            variant="ghost"
            size="icon"
            className="size-11 rounded-full"
            onClick={() => setTheme(isDark ? "light" : "dark")}
            aria-label={isDark ? "Switch to light theme" : "Switch to dark theme"}
          >
            {mounted ? (
              isDark ? (
                <Sun className="size-5" aria-hidden />
              ) : (
                <Moon className="size-5" aria-hidden />
              )
            ) : (
              <Sun className="size-5 opacity-0" aria-hidden />
            )}
          </Button>

          <Button asChild variant="ghost" size="icon" className="size-11 rounded-full">
            <a href={GITHUB_URL} target="_blank" rel="noreferrer" aria-label="SetBD Scanner on GitHub">
              <Github className="size-5" aria-hidden />
            </a>
          </Button>
        </div>
      </div>
    </header>
  );
}
