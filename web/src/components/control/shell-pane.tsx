"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Surface } from "@/components/ui/surface";
import { FieldNote } from "./warn-dialog";

export function ShellPane({
  cmd,
  out,
  note,
  live,
  online,
  onCmd,
  onRun,
}: {
  cmd: string;
  out: string;
  note: string | null;
  live: boolean;
  online: boolean;
  onCmd: (v: string) => void;
  onRun: () => void;
}) {
  return (
    <Surface>
      <CardHeader>
        <CardTitle>Shell</CardTitle>
      </CardHeader>
      <CardBody className="space-y-3">
        {note ? <FieldNote>{note}</FieldNote> : null}
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            onRun();
          }}
        >
          <Input
            value={cmd}
            onChange={(e) => onCmd(e.target.value)}
            className="font-mono text-xs"
            placeholder="dir C:\\"
            disabled={!live || !online}
          />
          <Button size="sm" type="submit" disabled={!live || !online || !cmd.trim()}>
            run
          </Button>
        </form>
        <pre className="max-h-[560px] min-h-[240px] overflow-auto rounded-2xl border border-white/10 bg-black/50 p-3 font-mono text-xs text-foreground/85 whitespace-pre-wrap">
          {out || " "}
        </pre>
      </CardBody>
    </Surface>
  );
}
