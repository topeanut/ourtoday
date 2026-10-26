"use client";

import { createContext, useCallback, useContext, useState } from "react";

const ToastContext = createContext<(message: string) => void>(() => {});

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);

  const show = useCallback((msg: string) => {
    setMessage(msg);
    window.setTimeout(() => setMessage((cur) => (cur === msg ? null : cur)), 2200);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      {message && (
        <div
          role="status"
          className="animate-fade pointer-events-none fixed inset-x-0 z-[60] flex justify-center px-4"
          style={{ bottom: "calc(96px + var(--safe-bottom))" }}
        >
          <div className="rounded-full bg-ink/90 px-4 py-2.5 text-sm text-white shadow-lg">{message}</div>
        </div>
      )}
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
