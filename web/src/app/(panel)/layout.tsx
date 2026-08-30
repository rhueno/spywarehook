import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { AppShell } from "@/components/shell/app-shell";

export default async function PanelLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const sess = await getSession();
  if (!sess) redirect("/login");
  const role = sess.kind === "admin" ? "admin" : "user";
  return <AppShell initialRole={role}>{children}</AppShell>;
}
