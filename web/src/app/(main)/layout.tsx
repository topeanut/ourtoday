import { AppShell } from "@/components/AppShell";
import { SessionGate } from "@/components/Session";

export default function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionGate>
      <AppShell>{children}</AppShell>
    </SessionGate>
  );
}
