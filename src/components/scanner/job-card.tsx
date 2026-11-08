"use client";

import { useState } from "react";
import { formatDistanceToNow } from "date-fns";
import { motion } from "framer-motion";
import {
  Clock3,
  Code2,
  Eye,
  FileArchive,
  Files,
  Globe,
  HardDrive,
  Loader2,
  MoreHorizontal,
  Trash2,
} from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Progress } from "@/components/ui/progress";
import { PreviewDialog } from "@/components/scanner/editor/preview-dialog";
import { formatBytes, type ScrapeJob } from "@/lib/types";

export interface JobCardProps {
  job: ScrapeJob;
  index?: number;
  onOpenEditor: (jobId: string) => void;
  onDownloadZip: (jobId: string) => void;
  onDelete: (jobId: string) => void | Promise<void>;
}

export function JobCard({ job, index = 0, onOpenEditor, onDownloadZip, onDelete }: JobCardProps) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const confirmDelete = async () => {
    setDeleting(true);
    try {
      await onDelete(job.id);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <motion.div
        layout
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95 }}
        transition={{
          type: "spring",
          stiffness: 320,
          damping: 28,
          delay: Math.min(index * 0.04, 0.24),
        }}
        whileHover={{ y: -2 }}
      >
        <Card className="glass flex h-full flex-col gap-3 rounded-xl border-border/60 p-4">
          {/* Header */}
          <div className="flex items-start gap-3">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Globe className="size-4" aria-hidden />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium" title={job.url}>
                {job.url}
              </p>
              {job.title && (
                <p className="truncate text-xs text-muted-foreground" title={job.title}>
                  {job.title}
                </p>
              )}
            </div>
            {job.status === "running" && (
              <Badge variant="outline" className="gap-1.5 border-primary/40 bg-primary/10 text-primary">
                <span className="relative flex size-1.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75" />
                  <span className="relative inline-flex size-1.5 rounded-full bg-primary" />
                </span>
                Running
              </Badge>
            )}
            {job.status === "completed" && (
              <Badge variant="outline" className="border-emerald-500/40 bg-emerald-500/10 text-emerald-500">
                Completed
              </Badge>
            )}
            {job.status === "failed" && <Badge variant="destructive">Failed</Badge>}
          </div>

          {/* Meta */}
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Clock3 className="size-3.5" aria-hidden />
              <span suppressHydrationWarning>
                {formatDistanceToNow(new Date(job.createdAt), { addSuffix: true })}
              </span>
            </span>
            <span className="inline-flex items-center gap-1">
              <Files className="size-3.5" aria-hidden />
              <span className="tabular-nums">{job.fileCount}</span>
            </span>
            <span className="inline-flex items-center gap-1">
              <HardDrive className="size-3.5" aria-hidden />
              {formatBytes(job.totalSize)}
            </span>
          </div>

          {/* Running progress */}
          {job.status === "running" && (
            <div className="flex items-center gap-2">
              <Progress
                value={job.progress}
                className="h-1.5 flex-1"
                aria-label={`Scan progress ${Math.round(job.progress)}%`}
              />
              <span className="text-xs font-medium tabular-nums text-primary">
                {Math.round(job.progress)}%
              </span>
            </div>
          )}

          {/* Actions */}
          <div className="mt-auto flex items-center justify-between gap-2 pt-1">
            <Button
              size="sm"
              variant="secondary"
              className="h-10 px-5"
              onClick={() => onOpenEditor(job.id)}
            >
              Open
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-11 rounded-full"
                  aria-label={`More actions for ${job.url}`}
                >
                  <MoreHorizontal className="size-4" aria-hidden />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-44">
                <DropdownMenuItem onSelect={() => onOpenEditor(job.id)}>
                  <Code2 aria-hidden /> Open Editor
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => onDownloadZip(job.id)}>
                  <FileArchive aria-hidden /> Download ZIP
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() => setPreviewOpen(true)}
                  disabled={job.status !== "completed"}
                >
                  <Eye aria-hidden /> Live Preview
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  onSelect={() => setConfirmOpen(true)}
                  className="text-destructive focus:text-destructive"
                >
                  <Trash2 aria-hidden /> Delete
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </Card>
      </motion.div>

      {/* Delete confirmation */}
      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this scan?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently remove the scraped files for {job.url}. This action cannot be
              undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className={buttonVariants({ variant: "destructive" })}
              onClick={() => void confirmDelete()}
              disabled={deleting}
            >
              {deleting && <Loader2 className="size-4 animate-spin" aria-hidden />}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <PreviewDialog open={previewOpen} onOpenChange={setPreviewOpen} jobId={job.id} />
    </>
  );
}
