import { Github, ShieldCheck } from "lucide-react";

const GITHUB_URL = "https://github.com/ai-multitool-v1/Setbd-scanner";

export function SiteFooter() {
  return (
    <footer className="mt-auto border-t border-border/60 py-6 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
      <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-3 px-4 text-center text-sm text-muted-foreground sm:flex-row sm:text-left">
        <p className="flex items-start gap-2 sm:items-center">
          <ShieldCheck className="mt-0.5 size-4 shrink-0 text-primary sm:mt-0" aria-hidden />
          <span>
            SetBD Scanner — for educational &amp; ethical use only. Respect copyrights and
            robots.txt.
          </span>
        </p>
        <div className="flex items-center gap-3">
          <span>v1.0 • Installable PWA</span>
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noreferrer"
            aria-label="SetBD Scanner on GitHub"
            className="rounded-full p-1 transition-colors hover:text-foreground"
          >
            <Github className="size-4" aria-hidden />
          </a>
        </div>
      </div>
    </footer>
  );
}
