"use client";

import { useCallback, useState } from "react";
import { ExternalLink, Globe, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import { rawUrl } from "@/lib/api";

export interface PreviewDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  jobId: string;
  path?: string;
}

/** Sandboxed live preview of a scraped file (defaults to index.html). */
export function PreviewDialog({ open, onOpenChange, jobId, path = "index.html" }: PreviewDialogProps) {
  const [loaded, setLoaded] = useState(false);

  // Reset the loading state whenever the dialog is opened.
  const handleOpenChange = useCallback(
    (next: boolean) => {
      if (next) setLoaded(false);
      onOpenChange(next);
    },
    [onOpenChange]
  );

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="w-[95vw] max-w-5xl gap-3 p-4 sm:p-6">
        <DialogTitle className="flex items-center gap-2 text-sm font-semibold">
          <Globe className="size-4 shrink-0 text-primary" aria-hidden />
          <span className="code-editor truncate">{path}</span>
          <Button asChild variant="ghost" size="icon" className="ml-auto size-8 shrink-0">
            <a
              href={rawUrl(jobId, path)}
              target="_blank"
              rel="noreferrer"
              aria-label="Open preview in a new tab"
            >
              <ExternalLink className="size-4" aria-hidden />
            </a>
          </Button>
        </DialogTitle>

        <div className="relative">
          {!loaded && (
            <div className="absolute inset-0 z-10 flex items-center justify-center rounded-md bg-muted/40">
              <Loader2 className="size-6 animate-spin text-muted-foreground" aria-label="Loading preview" />
            </div>
          )}
          <iframe
            title="Scraped site preview"
            src={rawUrl(jobId, path)}
            onLoad={() => setLoaded(true)}
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
            className="h-[75vh] w-full rounded-md border bg-white"
          />
        </div>

        <DialogDescription className="text-xs">
          Scripts from the scraped site run sandboxed.
        </DialogDescription>
      </DialogContent>
    </Dialog>
  );
}
