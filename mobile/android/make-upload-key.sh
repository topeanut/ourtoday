#!/usr/bin/env bash
# Play 업로드 키(upload.jks) + keystore.properties 만들고 서명된 AAB 빌드. 비밀번호는 화면에 안 보이고 파일 밖으로 안 나감
#   bash mobile/android/make-upload-key.sh
set -euo pipefail
cd "$(dirname "$0")"
if [ -f upload.jks ]; then echo "upload.jks 가 이미 있어요. 덮어쓰지 않습니다."; exit 1; fi

while true; do
  read -r -s -p "업로드 키 비밀번호 (8자 이상): " P1; echo
  read -r -s -p "한 번 더: " P2; echo
  [ "$P1" = "$P2" ] && [ ${#P1} -ge 8 ] && break
  echo "일치하지 않거나 8자 미만이에요. 다시 입력해 주세요."
done

export KS_PASS="$P1"
keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Ourtoday, O=BuildUp, C=KR" -storepass:env KS_PASS -keypass:env KS_PASS >/dev/null
umask 077
printf 'storeFile=upload.jks\nstorePassword=%s\nkeyAlias=upload\nkeyPassword=%s\n' "$P1" "$P1" > keystore.properties
unset P1 P2 KS_PASS
chmod 600 upload.jks keystore.properties

./gradlew -q bundleRelease
echo "완료: $(pwd)/app/build/outputs/bundle/release/app-release.aab"
echo "upload.jks 와 비밀번호는 비밀번호 관리자 등에 꼭 백업하세요 (git 에는 안 올라감)"
