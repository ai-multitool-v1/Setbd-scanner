"use client";

import { useCallback, useMemo, useRef, type KeyboardEvent, type UIEvent } from "react";
import { cn } from "@/lib/utils";

export interface CodeEditorProps {
  value: string;
  onChange: (value: string) => void;
  onSave: () => void;
  path: string;
  disabled?: boolean;
  /** Reports the 1-based caret line/column after clicks / key presses. */
  onCaret?: (line: number, col: number) => void;
}

/**
 * Lightweight source editor: synced line-number gutter + monospace textarea.
 * Supports Tab → two spaces and Cmd/Ctrl+S → onSave.
 */
export function CodeEditor({
  value,
  onChange,
  onSave,
  path,
  disabled = false,
  onCaret,
}: CodeEditorProps) {
  const gutterRef = useRef<HTMLDivElement>(null);
  const lineCount = useMemo(() => Math.max(value.split("\n").length, 1), [value]);
  const gutterWidth = `${String(lineCount).length + 1}ch`;

  const emitCaret = useCallback(
    (el: HTMLTextAreaElement) => {
      if (!onCaret) return;
      const upto = el.value.slice(0, el.selectionStart);
      const lines = upto.split("\n");
      onCaret(lines.length, (lines[lines.length - 1]?.length ?? 0) + 1);
    },
    [onCaret]
  );

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    const el = e.currentTarget;

    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "s") {
      e.preventDefault();
      e.stopPropagation(); // editor-view also listens globally — don't double-fire
      onSave();
      return;
    }

    if (e.key === "Tab" && !disabled) {
      e.preventDefault();
      const start = el.selectionStart;
      const end = el.selectionEnd;
      onChange(value.slice(0, start) + "  " + value.slice(end));
      requestAnimationFrame(() => {
        el.selectionStart = el.selectionEnd = start + 2;
      });
    }
  };

  const handleScroll = (e: UIEvent<HTMLTextAreaElement>) => {
    if (gutterRef.current) {
      gutterRef.current.scrollTop = e.currentTarget.scrollTop;
    }
  };

  return (
    <div className="flex min-h-0 flex-1 overflow-hidden">
      {/* Line-number gutter */}
      <div
        ref={gutterRef}
        aria-hidden
        className="code-editor select-none overflow-hidden py-3 pr-2 text-right text-muted-foreground/50"
        style={{ width: gutterWidth }}
      >
        {Array.from({ length: lineCount }, (_, i) => (
          <div key={i}>{i + 1}</div>
        ))}
      </div>

      {/* Code area */}
      <textarea
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          emitCaret(e.target);
        }}
        onKeyDown={handleKeyDown}
        onKeyUp={(e) => emitCaret(e.currentTarget)}
        onClick={(e) => emitCaret(e.currentTarget)}
        onScroll={handleScroll}
        spellCheck={false}
        autoCapitalize="off"
        autoCorrect="off"
        wrap="off"
        readOnly={disabled}
        aria-label={`Editor for ${path}`}
        className={cn(
          "code-editor min-h-0 w-full flex-1 resize-none overflow-auto whitespace-pre bg-transparent p-3 outline-none",
          disabled && "cursor-not-allowed opacity-60"
        )}
      />
    </div>
  );
}
