"use client";

import { useMemo, useState } from "react";
import {
  Braces,
  ChevronRight,
  Code2,
  FileArchive,
  FileCode,
  FileCode2,
  FileImage,
  FileText,
  FileType,
  FileVideo,
  File as FileDefault,
  Folder,
  FolderOpen,
  type LucideIcon,
} from "lucide-react";
import { formatBytes, type FileNode } from "@/lib/types";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { cn } from "@/lib/utils";

/* ---------------- Shared helpers ---------------- */

const EXT_ICON_RULES: Array<{ test: RegExp; Icon: LucideIcon; color: string }> = [
  { test: /\.html?$/i, Icon: Code2, color: "text-orange-500" },
  { test: /\.css$/i, Icon: FileCode, color: "text-sky-500" },
  { test: /\.(js|mjs|cjs)$/i, Icon: FileCode2, color: "text-yellow-500" },
  { test: /\.json$/i, Icon: Braces, color: "text-emerald-500" },
  { test: /\.(png|jpe?g|webp|gif|svg|ico|avif)$/i, Icon: FileImage, color: "text-pink-500" },
  { test: /\.(woff2?|ttf|otf|eot)$/i, Icon: FileType, color: "text-violet-500" },
  { test: /\.(mp4|webm|mp3|wav|ogg)$/i, Icon: FileVideo, color: "text-cyan-500" },
  { test: /\.zip$/i, Icon: FileArchive, color: "text-amber-500" },
  { test: /\.(md|txt)$/i, Icon: FileText, color: "text-muted-foreground" },
];

/** Icon + accent color for a file name, by extension. */
export function getFileIcon(name: string): { Icon: LucideIcon; color: string } {
  for (const rule of EXT_ICON_RULES) {
    if (rule.test.test(name)) return rule;
  }
  return { Icon: FileDefault, color: "text-muted-foreground" };
}

/** Dirs first (alphabetical), then files (alphabetical). */
function sortChildren(children: FileNode[]): FileNode[] {
  return [...children].sort((a, b) => {
    if (a.type !== b.type) return a.type === "dir" ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
}

/* ---------------- Component ---------------- */

export interface FileTreeProps {
  node: FileNode;
  activePath: string | null;
  onSelect: (path: string) => void;
  depth?: number;
  /** When true (tree root) render children directly without a root row. */
  root?: boolean;
}

function FileRow({
  node,
  active,
  onSelect,
  depth,
}: {
  node: FileNode;
  active: boolean;
  onSelect: (path: string) => void;
  depth: number;
}) {
  const { Icon, color } = getFileIcon(node.name);
  return (
    <button
      type="button"
      onClick={() => onSelect(node.path)}
      aria-current={active || undefined}
      className={cn(
        "flex h-8 w-full items-center gap-1.5 rounded-md border-l-2 border-transparent pr-2 text-left text-sm transition-colors hover:bg-accent/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
        active && "border-primary bg-primary/10 text-primary"
      )}
      style={{ paddingLeft: depth * 12 + 8 }}
    >
      <Icon className={cn("size-3.5 shrink-0", active ? "text-primary" : color)} aria-hidden />
      <span className="truncate">{node.name}</span>
      <span className="ml-auto hidden shrink-0 text-[10px] text-muted-foreground/60 sm:block">
        {formatBytes(node.size)}
      </span>
    </button>
  );
}

export function FileTree({ node, activePath, onSelect, depth = 0, root = false }: FileTreeProps) {
  const children = useMemo(() => sortChildren(node.children ?? []), [node.children]);
  const containsActive =
    node.type === "dir" &&
    !!activePath &&
    !!node.path &&
    activePath.startsWith(`${node.path}/`);
  // Auto-open shallow folders and anything on the active path.
  const [open, setOpen] = useState(depth === 0 || (node.children?.length ?? 0) < 30 || containsActive);
  const isOpen = node.type === "dir" ? open || containsActive : false;

  if (node.type === "file") {
    return (
      <FileRow node={node} active={activePath === node.path} onSelect={onSelect} depth={depth} />
    );
  }

  if (root) {
    return (
      <div role="tree" aria-label="Scraped files">
        {children.map((child) => (
          <FileTree key={child.path} node={child} activePath={activePath} onSelect={onSelect} depth={depth} />
        ))}
      </div>
    );
  }

  return (
    <Collapsible open={isOpen} onOpenChange={setOpen}>
      <CollapsibleTrigger asChild>
        <button
          type="button"
          aria-expanded={isOpen}
          className="flex h-8 w-full items-center gap-1.5 rounded-md border-l-2 border-transparent pr-2 text-left text-sm transition-colors hover:bg-accent/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
          style={{ paddingLeft: depth * 12 + 8 }}
        >
          <ChevronRight
            className={cn(
              "size-3.5 shrink-0 text-muted-foreground transition-transform",
              isOpen && "rotate-90"
            )}
            aria-hidden
          />
          {isOpen ? (
            <FolderOpen
              className={cn("size-3.5 shrink-0", containsActive ? "text-primary" : "text-primary/80")}
              aria-hidden
            />
          ) : (
            <Folder
              className={cn("size-3.5 shrink-0", containsActive ? "text-primary" : "text-primary/80")}
              aria-hidden
            />
          )}
          <span className="truncate">{node.name}</span>
          <span className="ml-auto shrink-0 text-[10px] text-muted-foreground/60">
            {children.length}
          </span>
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="my-0.5">
          {children.map((child) => (
            <FileTree
              key={child.path}
              node={child}
              activePath={activePath}
              onSelect={onSelect}
              depth={depth + 1}
            />
          ))}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
