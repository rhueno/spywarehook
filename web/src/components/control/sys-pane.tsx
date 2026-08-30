"use client";

import { Button } from "@/components/ui/button";
import { CardBody, CardHeader, CardTitle } from "@/components/ui/card";
import { Surface } from "@/components/ui/surface";
import { FieldNote } from "./warn-dialog";

const ACTS = [
  ["shutdown", "Shutdown", true],
  ["reboot", "Reboot", true],
  ["lock", "Lock", false],
  ["sleep", "Sleep", false],
  ["logoff", "Logoff", false],
] as const;

export function SysPane({
  live,
  online,
  grabBusy,
  grabNote,
  powNote,
  onGrab,
  onPow,
}: {
  live: boolean;
  online: boolean;
  grabBusy: boolean;
  grabNote: string | null;
  powNote: string | null;
  onGrab: () => void;
  onPow: (kind: string, label: string) => void;
}) {
  return (
    <Surface>
      <CardHeader>
        <CardTitle>System</CardTitle>
      </CardHeader>
      <CardBody className="space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" disabled={!live || !online || grabBusy} onClick={onGrab}>
            {grabBusy ? "Fetching log…" : "Grab log"}
          </Button>
          {grabNote ? <span className="text-xs text-muted-foreground">{grabNote}</span> : null}
        </div>
        {powNote ? <FieldNote>{powNote}</FieldNote> : null}
        <div className="flex flex-wrap gap-2">
          {ACTS.map(([kind, label, danger]) => (
            <Button
              key={kind}
              size="sm"
              variant={danger ? "danger" : "outline"}
              disabled={!live || !online}
              onClick={() => onPow(kind, label)}
            >
              {label}
            </Button>
          ))}
        </div>
      </CardBody>
    </Surface>
  );
}
