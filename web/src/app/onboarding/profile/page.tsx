"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { mutate } from "swr";
import { api, errorMessage } from "@/lib/api";
import { getToken } from "@/lib/auth";
import { Button, Field, inputClass } from "@/components/ui";
import { useToast } from "@/components/Toast";

export default function ProfileOnboardingPage() {
  const router = useRouter();
  const toast = useToast();
  const [nickname, setNickname] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await api("/users/me", { method: "PATCH", body: { nickname: nickname.trim(), profileImageUrl: null } });
      // 캐시된 NEED_PROFILE 때문에 다시 돌아오지 않게 먼저 갱신
      await mutate("/users/me/onboarding-status");
      router.replace("/onboarding/couple");
    } catch (err) {
      toast(errorMessage(err));
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="flex flex-1 flex-col">
      <div className="flex-1 pt-16 group-data-[keyboard=open]:pt-6">
        <p className="text-sm font-semibold text-brand-strong">1 / 2</p>
        <h1 className="mt-2 text-2xl font-bold leading-snug">
          상대에게 보일
          <br />
          이름을 알려주세요
        </h1>
        <p className="mt-2 text-sub">애칭도 좋아요. 나중에 마이페이지에서 바꿀 수 있어요.</p>
        <div className="mt-10">
          <Field label="닉네임" hint={`${nickname.length}/20`}>
            <input
              className={inputClass}
              value={nickname}
              onChange={(e) => setNickname(e.target.value.slice(0, 20))}
              placeholder="예) 민지"
              autoFocus
              enterKeyHint="next"
            />
          </Field>
        </div>
      </div>
      <div className="pb-8 group-data-[keyboard=open]:pb-3">
        <Button type="submit" full loading={saving} disabled={!nickname.trim()}>
          다음
        </Button>
      </div>
    </form>
  );
}
