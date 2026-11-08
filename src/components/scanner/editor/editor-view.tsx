"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  ArrowLeft,
  Download,
  Eye,
  FileArchive,
  FileCode2,
  FileQuestion,
  Loader2,
  PanelLeft,
  RefreshCw,
  Save,
} from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { CodeEditor } from "@/components/scanner/editor/code-editor";
import { FileTree, getFileIcon } from "@/components/scanner/editor/file-tree";
import { PreviewDialog } from "@/components/scanner/editor/preview-dialog";
import { getFile, getJob, rawUrl, saveFile } from "@/lib/api";
import { formatBytes, type FileContent, type FileNode, type ScrapeJob } from "@/lib/types";
import { useToast } from "@/hooks/use-toast";

export interface EditorViewProps {
  jobId: string;
  onClose: () => void;
  onDownloadZip: (jobId: string) => void;
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Unknown error";
}

function countFiles(node: FileNode): number {
  if (node.type === "file") return 1;
  return (node.children ?? []).reduce((acc, child) => acc + countFiles(child), 0);
}

function totalSize(node: FileNode): number {
  if (node.type === "file") return node.size;
  return (node.children ?? []).reduce((acc, child) => acc + totalSize(child), 0);
}

function firstFile(node: FileNode): string | null {
  if (node.type === "file") return node.path;
  for (const child of node.children ?? []) {
    const hit = firstFile(child);
    if (hit) return hit;
  }
  return null;
}

/** Prefer any index.html, falling back to the first file in the tree. */
function findIndexPath(root: FileNode): string | null {
  const walk = (node: FileNode): string | null => {
    if (node.type === "file") {
      return /(^|\/)index\.html$/i.test(node.path) ? node.path : null;
    }
    for (const child of node.children ?? []) {
      const hit = walk(child);
      if (hit) return hit;
    }
    return null;
  };
  return walk(root) ?? firstFile(root);
}

function TreeSkeleton() {
  return (
    <div className="space-y-2 px-1 py-1" aria-hidden>
      {[64, 48, 56, 40, 52, 44, 60].map((w, i) => (
        <div
          key={i}
          className="h-6 animate-pulse rounded-md bg-muted/60"
          style={{ width: `${w}%`, marginLeft: `${(i % 3) * 12}px` }}
        />
      ))}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 text-muted-foreground">
      <FileCode2 className="size-10 opacity-50" aria-hidden />
      <p className="text-sm">Select a file to view &amp; edit its source</p>
    </div>
  );
}

export function EditorView({ jobId, onClose, onDownloadZip }: EditorViewProps) {
  const { toast } = useToast();

  const [job, setJob] = useState<ScrapeJob | null>(null);
  const [tree, setTree] = useState<FileNode | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadingJob, setLoadingJob] = useState(true);

  const [activePath, setActivePath] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<FileContent | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [dirty, setDirty] = useState(false);
  const [loadingFile, setLoadingFile] = useState(false);
  const [saving, setSaving] = useState(false);

  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [discardOpen, setDiscardOpen] = useState(false);
  const pendingActionRef = useRef<(() => void) | null>(null);
  const [caret, setCaret] = useState<{ line: number; col: number }>({ line: 1, col: 1 });

  /* ---------------- Data loading ---------------- */

  const openFile = useCallback(
    async (path: string) => {
      setActivePath(path);
      setLoadingFile(true);
      try {
        const fc = await getFile(jobId, path);
        setFileContent(fc);
        setFileError(null);
        setDraft(fc.isText && fc.content != null ? fc.content : "");
        setDirty(false);
      } catch (err) {
        setFileContent(null);
        setDraft("");
        setFileError(errorMessage(err));
        toast({
          title: "Could not open file",
          description: errorMessage(err),
          variant: "destructive",
        });
      } finally {
        setLoadingFile(false);
      }
    },
    [jobId, toast]
  );

  const loadJob = useCallback(async () => {
    setLoadingJob(true);
    setLoadError(null);
    try {
      const data = await getJob(jobId);
      setJob(data.job);
      setTree(data.tree);
      const initial = findIndexPath(data.tree);
      if (initial) void openFile(initial);
    } catch (err) {
      setLoadError(errorMessage(err));
    } finally {
      setLoadingJob(false);
    }
  }, [jobId, openFile]);

  useEffect(() => {
    void loadJob();
  }, [loadJob]);

  /* ---------------- Mutations ---------------- */

  const handleSave = useCallback(async () => {
    if (!activePath || !dirty || saving) return;
    setSaving(true);
    try {
      await saveFile(jobId, activePath, draft);
      setDirty(false);
      setFileContent((prev) =>
        prev && prev.path === activePath
          ? { ...prev, content: draft, size: new TextEncoder().encode(draft).length }
          : prev
      );
      toast({ title: `Saved ${activePath}` });
    } catch (err) {
      toast({
        title: "Save failed",
        description: errorMessage(err),
        variant: "destructive",
      });
    } finally {
      setSaving(false);
    }
  }, [activePath, dirty, draft, jobId, saving, toast]);

  /** Open a file, asking for confirmation first when there are unsaved edits. */
  const requestOpen = useCallback(
    (path: string) => {
      if (dirty) {
        pendingActionRef.current = () => void openFile(path);
        setDiscardOpen(true);
        return;
      }
      void openFile(path);
    },
    [dirty, openFile]
  );

  const confirmDiscard = () => {
    setDirty(false);
    setDiscardOpen(false);
    const action = pendingActionRef.current;
    pendingActionRef.current = null;
    action?.();
  };

  /* ---------------- Keyboard shortcuts ---------------- */

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "s") {
        e.preventDefault();
        void handleSave();
        return;
      }
      if (e.key === "Escape" && !previewOpen && !discardOpen) {
        if (dirty) {
          pendingActionRef.current = onClose;
          setDiscardOpen(true);
        } else {
          onClose();
        }
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [handleSave, dirty, previewOpen, discardOpen, onClose]);

  /* ---------------- Derived ---------------- */

  const fileCount = useMemo(() => (tree ? countFiles(tree) : 0), [tree]);
  const sizeTotal = useMemo(() => (tree ? totalSize(tree) : 0), [tree]);
  const lines = useMemo(() => (draft === "" ? 1 : draft.split("\n").length), [draft]);

  const activeIsText = fileContent?.isText === true && fileContent.path === activePath;
  const activeIsImage =
    !!fileContent && fileContent.path === activePath && fileContent.mime.startsWith("image/");
  const ActiveFileIcon = activePath ? getFileIcon(activePath.split("/").pop() ?? activePath).Icon : null;

  const treePanel = (onPicked?: () => void) => (
    <>
      {loadingJob || !tree ? (
        <TreeSkeleton />
      ) : (
        <FileTree
          node={tree}
          root
          activePath={activePath}
          onSelect={(p) => {
            requestOpen(p);
            onPicked?.();
          }}
        />
      )}
    </>
  );

  return (
    <>
      <motion.div
        initial={{ opacity: 0, y: 28 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: 28 }}
        transition={{ type: "spring", stiffness: 300, damping: 30 }}
        className="fixed inset-0 z-50 flex flex-col bg-background"
      >
        {/* Top bar */}
        <div className="glass flex h-14 shrink-0 items-center gap-2 border-b border-border/60 px-3">
          <Button
            variant="ghost"
            size="icon"
            className="size-11 rounded-full"
            onClick={onClose}
            aria-label="Back to scanner"
          >
            <ArrowLeft className="size-5" aria-hidden />
          </Button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold" title={job?.url ?? jobId}>
              {job?.url ?? "Loading…"}
            </p>
            <p className="text-[11px] text-muted-foreground">Source Editor</p>
          </div>

          {dirty && (
            <span className="flex items-center gap-1.5 text-[11px] font-medium text-amber-500">
              <span className="size-2 rounded-full bg-amber-500" aria-hidden />
              <span className="sr-only">Unsaved changes</span>
              <span className="hidden sm:inline">Unsaved</span>
            </span>
          )}

          <Button
            size="sm"
            className="h-9"
            onClick={() => void handleSave()}
            disabled={!dirty || saving}
          >
            {saving ? (
              <Loader2 className="size-4 animate-spin" aria-hidden />
            ) : (
              <Save className="size-4" aria-hidden />
            )}
            <span className="hidden sm:inline">{saving ? "Saving…" : "Save"}</span>
            <span className="sr-only sm:hidden">Save</span>
          </Button>

          <Button size="sm" variant="ghost" className="h-9" onClick={() => setPreviewOpen(true)}>
            <Eye className="size-4" aria-hidden />
            <span className="hidden md:inline">Live Preview</span>
            <span className="sr-only md:hidden">Live Preview</span>
          </Button>

          <Button
            size="sm"
            variant="secondary"
            className="hidden h-9 md:inline-flex"
            onClick={() => onDownloadZip(jobId)}
          >
            <FileArchive className="size-4" aria-hidden />
            ZIP
          </Button>

          {/* Mobile file browser */}
          <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
            <SheetTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="size-11 rounded-full md:hidden"
                aria-label="Open file browser"
              >
                <PanelLeft className="size-5" aria-hidden />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-80 gap-0 p-0">
              <SheetHeader className="border-b border-border/60 px-4 py-3">
                <SheetTitle className="text-sm">Files</SheetTitle>
                <SheetDescription className="sr-only">Browse scraped files</SheetDescription>
              </SheetHeader>
              <ScrollArea className="min-h-0 flex-1">
                <div className="p-2">{treePanel(() => setSidebarOpen(false))}</div>
              </ScrollArea>
              <div className="border-t border-border/60 px-4 py-2 text-xs text-muted-foreground">
                {fileCount} files • {formatBytes(sizeTotal)}
              </div>
            </SheetContent>
          </Sheet>
        </div>

        {/* Body */}
        {loadError ? (
          <div className="flex flex-1 items-center justify-center p-6">
            <div className="w-full max-w-md space-y-4">
              <Alert variant="destructive">
                <AlertTriangle aria-hidden />
                <AlertTitle>Could not load this scan</AlertTitle>
                <AlertDescription>{loadError}</AlertDescription>
              </Alert>
              <div className="flex gap-2">
                <Button onClick={() => void loadJob()}>
                  <RefreshCw className="size-4" aria-hidden /> Retry
                </Button>
                <Button variant="outline" onClick={onClose}>
                  <ArrowLeft className="size-4" aria-hidden /> Back
                </Button>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex min-h-0 flex-1 overflow-hidden">
            {/* Desktop sidebar */}
            <aside className="hidden w-72 shrink-0 flex-col border-r border-border/60 md:flex">
              <div className="flex items-center justify-between border-b border-border/60 px-3 py-2">
                <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Files
                </span>
                <Badge variant="secondary" className="tabular-nums">
                  {fileCount}
                </Badge>
              </div>
              <ScrollArea className="min-h-0 flex-1">
                <div className="px-2 py-2">{treePanel()}</div>
              </ScrollArea>
              <div className="border-t border-border/60 px-3 py-2 text-xs text-muted-foreground">
                {fileCount} files • {formatBytes(sizeTotal)}
              </div>
            </aside>

            {/* Main column */}
            <div className="flex min-w-0 flex-1 flex-col">
              <div className="flex h-10 shrink-0 items-center gap-2 border-b border-border/60 px-4">
                {ActiveFileIcon && <ActiveFileIcon className="size-4 shrink-0 text-primary" aria-hidden />}
                <span className="code-editor truncate text-xs" title={activePath ?? undefined}>
                  {activePath ?? "No file selected"}
                </span>
                {fileContent && fileContent.path === activePath && (
                  <span className="ml-auto hidden shrink-0 text-xs text-muted-foreground sm:block">
                    {formatBytes(fileContent.size)} • {fileContent.mime}
                  </span>
                )}
              </div>

              <div className="relative min-h-0 flex-1 overflow-hidden">
                {loadingFile ? (
                  <div className="flex h-full items-center justify-center">
                    <Loader2 className="size-6 animate-spin text-muted-foreground" aria-label="Loading file" />
                  </div>
                ) : fileError && activePath ? (
                  <div className="flex h-full items-center justify-center p-6">
                    <div className="w-full max-w-sm space-y-4">
                      <Alert variant="destructive">
                        <AlertTriangle aria-hidden />
                        <AlertTitle>Could not open file</AlertTitle>
                        <AlertDescription>{fileError}</AlertDescription>
                      </Alert>
                      <Button size="sm" onClick={() => void openFile(activePath)}>
                        <RefreshCw className="size-4" aria-hidden /> Retry
                      </Button>
                    </div>
                  </div>
                ) : !activePath || !fileContent ? (
                  <EmptyState />
                ) : activeIsText ? (
                  <CodeEditor
                    value={draft}
                    onChange={(v) => {
                      setDraft(v);
                      setDirty(true);
                    }}
                    onSave={() => void handleSave()}
                    path={activePath}
                    onCaret={(line, col) => setCaret({ line, col })}
                  />
                ) : activeIsImage ? (
                  <div className="flex h-full flex-col items-center justify-center gap-3 overflow-auto p-4">
                    {/* Raw <img> is intentional: next/image cannot proxy dynamic API bytes with accurate mime */}
                    <img
                      src={rawUrl(jobId, fileContent.path)}
                      alt={fileContent.path}
                      className="max-h-full max-w-full rounded-lg border border-border/60 object-contain shadow-lg"
                    />
                    <p className="text-xs text-muted-foreground">
                      Images are compressed copies of the originals.
                    </p>
                  </div>
                ) : (
                  <div className="flex h-full items-center justify-center p-4">
                    <div className="glass flex max-w-sm flex-col items-center gap-3 rounded-2xl border border-border/60 p-8 text-center">
                      <FileQuestion className="size-10 text-muted-foreground" aria-hidden />
                      <p className="text-sm font-medium">Binary file — preview not supported</p>
                      <Button asChild size="sm" variant="secondary" className="h-10">
                        <a
                          href={rawUrl(jobId, fileContent.path)}
                          download={fileContent.path.split("/").pop() ?? "file"}
                        >
                          <Download className="size-4" aria-hidden /> Download file
                        </a>
                      </Button>
                    </div>
                  </div>
                )}
              </div>

              <div className="flex h-8 shrink-0 items-center justify-between gap-3 border-t border-border/60 px-4 text-[11px] text-muted-foreground">
                <span className="tabular-nums">
                  {lines} lines • {draft.length} chars
                  {activeIsText && (
                    <>
                      {" "}
                      • Ln {caret.line}, Col {caret.col}
                    </>
                  )}
                </span>
                <span className="hidden sm:block">Ctrl/⌘+S to save</span>
                <span className="code-editor hidden max-w-[40%] truncate md:block">
                  {activePath ?? ""}
                </span>
              </div>
            </div>
          </div>
        )}
      </motion.div>

      {/* Discard-unsaved-changes confirmation */}
      <AlertDialog
        open={discardOpen}
        onOpenChange={(open) => {
          setDiscardOpen(open);
          if (!open) pendingActionRef.current = null;
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Discard unsaved changes?</AlertDialogTitle>
            <AlertDialogDescription>
              Your edits have not been saved yet. Closing now will lose them.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Keep editing</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDiscard}>Discard</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <PreviewDialog open={previewOpen} onOpenChange={setPreviewOpen} jobId={jobId} />
    </>
  );
}
