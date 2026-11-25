"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import useSWR, { mutate } from "swr";
import { api, errorMessage, fetcher } from "@/lib/api";
import { getToken } from "@/lib/auth";
import { uploadProfileImage } from "@/lib/image";
import type { User } from "@/lib/types";
import { ProfilePhotoPicker } from "@/components/ProfilePhotoPicker";
import { Button, Field, inputClass } from "@/components/ui";
import { useToast } from "@/components/Toast";

export default function ProfileOnboardingPage() {
  const router = useRouter();
  const toast = useToast();
  // 직접 입력하기 전까지는 카카오 닉네임(동의했을 때)을 기본값으로 보여줌
  const [typed, setNickname] = useState<string | null>(null);
  const [photo, setPhoto] = useState<File | null>(null);
  // 카카오로 가입했고 프로필 사진에 동의했으면 그 사진이 이미 들어 있음
  const { data: me } = useSWR<User>(getToken() ? "/users/me" : null, fetcher);
  const nickname = typed ?? me?.nickname ?? "";
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      // 직접 고른 사진이 있으면 카카오 사진보다 우선
      const profileImageKey = photo ? await uploadProfileImage(photo) : null;
      await api("/users/me", { method: "PATCH", body: { nickname: nickname.trim(), profileImageKey } });
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
        <div className="mt-8 flex items-center gap-4 group-data-[keyboard=open]:hidden">
          <ProfilePhotoPicker name={nickname} seed={me?.id} currentSrc={me?.profileImageUrl} file={photo} onChange={setPhoto} size={72} />
          <p className="text-sm text-sub">
            {photo
              ? "이 사진으로 보여요."
              : me?.profileImageUrl
                ? "카카오 프로필 사진을 가져왔어요. 눌러서 다른 사진으로 바꿀 수 있어요."
                : "사진은 넣지 않아도 괜찮아요."}
          </p>
        </div>
        <div className="mt-8 group-data-[keyboard=open]:mt-6">
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
