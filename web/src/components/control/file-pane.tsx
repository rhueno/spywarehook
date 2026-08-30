"use client";

import { ChevronUp, Folder, HardDrive } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Surface } from "@/components/ui/surface";
import { fmtSize, joinPath, parentPath, type FsEntry } from "./types";
import { FieldNote } from "./warn-dialog";

export function FilePane({
  path,
  entries,
  note,
  live,
  online,
  onPath,
  onOpen,
  onRoots,
  onList,
  onDel,
  onPut,
}: {
  path: string;
  entries: FsEntry[];
  note: string | null;
  live: boolean;
  online: boolean;
  onPath: (v: string) => void;
  onOpen: (e: FsEntry) => void;
  onRoots: () => void;
  onList: (p: string) => void;
  onDel: () => void;
  onPut: (file: File) => void;
}) {
  const up = parentPath(path);
  const crumbs = path
    ? path.replace(/[\\/]+$/, "").split(/[\\/]/).filter(Boolean)
    : [];

  return (
    <Surface>
      <CardHeader>
        <CardTitle>Files</CardTitle>
      </CardHeader>
      <CardBody className="space-y-3">
        <div className="flex flex-wrap gap-2">
          <Input
            value={path}
            onChange={(e) => onPath(e.target.value)}
            className="min-w-[240px] flex-1 font-mono text-xs"
            placeholder="C:\\"
            onKeyDown={(e) => {
              if (e.key === "Enter") onList(path);
            }}
          />
          <Button size="sm" disabled={!live || !online} onClick={() => onList(path)}>
            open
          </Button>
          <Button size="sm" variant="outline" disabled={!live || !online} onClick={onRoots}>
            roots
          </Button>
          <Button
            size="sm"
            variant="danger"
            disabled={!live || !online || !path}
            onClick={onDel}
          >
            delete
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-1 font-mono text-[11px] text-muted-foreground">
          <button
            type="button"
            className="rounded-lg px-2 py-1 hover:bg-white/[0.05] hover:text-foreground"
            onClick={onRoots}
          >
            <HardDrive className="mr-1 inline size-3" />
            roots
          </button>
          {crumbs.map((c, i) => {
            const next = crumbs.slice(0, i + 1).join("\\");
            const drive = next.length === 2 && next[1] === ":" ? `${next}\\` : next;
            return (
              <button
                key={i}
                type="button"
                className="rounded-lg px-2 py-1 hover:bg-white/[0.05] hover:text-foreground"
                onClick={() => onList(drive)}
              >
                {c}
              </button>
            );
          })}
        </div>
        {note ? <FieldNote>{note}</FieldNote> : null}
        <div className="max-h-[560px] overflow-auto rounded-2xl border border-white/10">
          <table className="w-full text-left text-sm">
            <tbody>
              {path ? (
                <tr
                  className="cursor-pointer border-b border-white/5 hover:bg-white/[0.03]"
                  onClick={() => onList(up)}
                >
                  <td className="px-3 py-2 text-muted-foreground">
                    <ChevronUp className="mr-2 inline size-3.5" />
                    ..
                  </td>
                  <td className="px-3 py-2 text-right font-mono text-xs text-muted-foreground">
                    up
                  </td>
                </tr>
              ) : null}
              {entries.length === 0 ? (
                <tr>
                  <td className="px-3 py-10 text-center text-sm text-muted-foreground" colSpan={2}>
                    {live && online ? "empty" : "offline"}
                  </td>
                </tr>
              ) : (
                entries.map((e) => (
                  <tr
                    key={e.n + String(e.d)}
                    className="cursor-pointer border-b border-white/5 hover:bg-white/[0.03]"
                    onClick={() => onOpen(e)}
                  >
                    <td className="px-3 py-2">
                      {e.d ? (
                        <Folder className="mr-2 inline size-3.5 text-muted-foreground" />
                      ) : null}
                      {e.n}
                    </td>
                    <td className="px-3 py-2 text-right font-mono text-xs text-muted-foreground">
                      {e.d ? "dir" : fmtSize(e.s)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <label className="inline-flex cursor-pointer items-center gap-2 font-mono text-xs text-muted-foreground">
          <span className="rounded-2xl border border-white/10 px-3 py-2 transition hover:border-white/25 hover:text-foreground">
            upload
          </span>
          <input
            type="file"
            className="hidden"
            onChange={(ev) => {
              const f = ev.target.files?.[0];
              if (f) onPut(f);
              ev.target.value = "";
            }}
          />
        </label>
      </CardBody>
    </Surface>
  );
}

export { joinPath };
