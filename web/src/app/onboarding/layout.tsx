import { KeyboardSafeScreen } from "@/components/KeyboardSafeScreen";

export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return (
    <KeyboardSafeScreen className="pt-safe pb-safe mx-auto flex max-w-md flex-col px-6">
      {children}
    </KeyboardSafeScreen>
  );
}
