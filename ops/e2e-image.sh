#!/usr/bin/env bash
# 운영 서버에서 사진 업로드 전 과정 점검 (임시 유저·커플·게시글 만들고 끝나면 삭제)
#   업로드 URL 발급 → presigned PUT → 게시글에 첨부 → 조회 URL 다운로드 → 게시글 삭제 시 파일 삭제
# 비밀값(.env)은 서버 안에서만 사용하고 출력하지 않음
set -uo pipefail
cd /home/ubuntu/ourtoday
set -a; . ./.env; set +a

PSQL="docker exec -i ourtoday-postgres psql -U ourtoday -d ourtoday -tAq"
API=http://localhost:8080/api/v1
A=$(cat /proc/sys/kernel/random/uuid); B=$(cat /proc/sys/kernel/random/uuid); CP=$(cat /proc/sys/kernel/random/uuid)
ok() { echo "✅ $*"; }; ng() { echo "❌ $*"; FAIL=1; }; FAIL=0

cleanup() {
  $PSQL -c "delete from reactions where user_id in ('$A','$B');
            delete from post_images where post_id in (select id from posts where couple_id='$CP');
            delete from posts where couple_id='$CP';
            delete from couple_members where couple_id='$CP'; delete from couples where id='$CP';
            delete from users where id in ('$A','$B');" >/dev/null
  rm -f /tmp/e2e.jpg /tmp/e2e.down
}
trap cleanup EXIT

$PSQL -c "insert into users(id,kakao_id,nickname,profile_completed,created_at) values
            ('$A','e2e-$A','e2eA',true,now()),('$B','e2e-$B','e2eB',true,now());
          insert into couples(id,status,started_at,created_at) values ('$CP','CONNECTED',current_date,now());
          insert into couple_members(couple_id,user_id,joined_at) values ('$CP','$A',now()),('$CP','$B',now());" >/dev/null

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
NOW=$(date +%s)
H=$(printf '{"alg":"HS512"}' | b64url)
P=$(printf '{"sub":"%s","iat":%d,"exp":%d}' "$A" "$NOW" $((NOW + 600)) | b64url)
S=$(printf '%s.%s' "$H" "$P" | openssl dgst -sha512 -hmac "$JWT_SECRET" -binary | b64url)
AUTH="Authorization: Bearer $H.$P.$S"
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1"; }

head -c 4096 /dev/urandom > /tmp/e2e.jpg
LEN=$(stat -c%s /tmp/e2e.jpg)

R=$(curl -s -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"contentType\":\"image/jpeg\",\"contentLength\":$LEN}" $API/images/presigned-url)
URL=$(echo "$R" | json 'd["uploadUrl"]' 2>/dev/null) && KEY=$(echo "$R" | json 'd["imageKey"]') \
  && ok "업로드 URL 발급" || { ng "업로드 URL 발급 실패: $R"; exit 1; }

CORS=$(curl -s -o /dev/null -D - -X OPTIONS -H "Origin: https://ourtoday.vercel.app" \
  -H "Access-Control-Request-Method: PUT" -H "Access-Control-Request-Headers: content-type" "$URL" \
  | grep -i '^access-control-allow-origin' | tr -d '\r')
[ -n "$CORS" ] && ok "브라우저 직접 업로드(CORS) 허용: $CORS" || echo "⚠️ CORS 응답 없음 → 브라우저 직접 업로드는 막힐 수 있음 (앱/서버 업로드는 무관)"

C=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: image/jpeg' --data-binary @/tmp/e2e.jpg "$URL")
[ "$C" = 200 ] && ok "presigned PUT 업로드 ($LEN bytes)" || ng "PUT 실패 HTTP $C"

C=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: image/jpeg' --data-binary @/etc/hostname "$URL")
[ "$C" != 200 ] && ok "크기 다른 파일은 거부됨 (HTTP $C)" || ng "크기 검증이 안 됨"

R=$(curl -s -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"content\":\"e2e\",\"postedDate\":\"$(TZ=Asia/Seoul date +%F)\",\"imageKeys\":[\"$KEY\"]}" $API/posts)
PID=$(echo "$R" | json 'd["id"]' 2>/dev/null) && DL=$(echo "$R" | json 'd["images"][0]["url"]') \
  && ok "게시글에 사진 첨부" || ng "게시글 작성 실패: $R"

C=$(curl -s -o /tmp/e2e.down -w '%{http_code}' "$DL")
[ "$C" = 200 ] && cmp -s /tmp/e2e.jpg /tmp/e2e.down && ok "조회 URL로 같은 파일 다운로드" || ng "다운로드 실패 HTTP $C"

C=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "$AUTH" $API/posts/$PID)
sleep 2
G=$(curl -s -o /dev/null -w '%{http_code}' "$DL")
[ "$C" = 204 ] && [ "$G" != 200 ] && ok "게시글 삭제 시 파일도 삭제 (다운로드 HTTP $G)" || ng "삭제 확인 실패 DELETE=$C GET=$G"

[ $FAIL = 0 ] && echo "E2E 통과" || echo "E2E 실패"
