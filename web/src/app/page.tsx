import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";

export default async function Home() {
  const sess = await getSession();
  if (sess) redirect("/dash");
  redirect("/login");
}
