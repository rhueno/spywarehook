import { redirect } from "next/navigation";
import { getSession, isAdminSession } from "@/lib/auth";

export default async function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const sess = await getSession();
  if (!sess || !isAdminSession(sess)) redirect("/");
  return children;
}
