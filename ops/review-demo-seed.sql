-- App Store 심사용 데모 커플 만들기 (운영 DB, 한 번만 실행)
--
-- 준비: 심사용 카카오 계정으로 운영 앱에 로그인 → 프로필(닉네임)까지 입력 → 커플 연결 화면에서 멈춤
-- 실행 (서버):
--   cd ~/ourtoday && set -a && . ./.env && set +a
--   docker exec -i ourtoday-postgres psql -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 -v nick='민지' < review-demo-seed.sql
--
-- 하는 일 (전부 한 트랜잭션, 조건이 안 맞으면 아무것도 안 바꾸고 중단)
--   1. 닉네임이 nick 인 카카오 계정을 찾음 (최근 3일 안에 가입, 프로필 완료, 커플 없음 → 정확히 1명이어야 함)
--   2. 로그인할 수 없는 상대 계정 demo:partner(지호) 생성
--   3. 둘을 커플로 연결 (사귄 날 = 437일 전)
--   4. 기록 7개(작년·재작년 오늘 포함), 반응, 캘린더 메모 3개, 버킷리스트 3개
-- 내용은 로컬 샘플(LocalSeed)·App Store 스크린샷과 같음

\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('demo.nick', :'nick', true);

DO $$
DECLARE
  nick      text := current_setting('demo.nick');
  today     date := (now() AT TIME ZONE 'Asia/Seoul')::date;
  a         uuid;               -- 심사 계정 (카카오)
  b         uuid := gen_random_uuid();  -- 데모 상대
  c         uuid := gen_random_uuid();  -- 커플
  first_post uuid;
  p         record;
  pid       uuid;
  done_id   uuid := gen_random_uuid();
  n         int;
BEGIN
  IF EXISTS (SELECT 1 FROM users WHERE kakao_id = 'demo:partner') THEN
    RAISE EXCEPTION '이미 demo:partner 가 있어요. 데모 커플은 이미 만들어졌어요.';
  END IF;

  SELECT count(*) INTO n FROM users u
   WHERE u.nickname = nick
     AND u.profile_completed
     AND u.kakao_id ~ '^[0-9]+$'
     AND u.created_at > now() - interval '3 days'
     AND NOT EXISTS (SELECT 1 FROM couple_members m WHERE m.user_id = u.id);
  IF n <> 1 THEN
    RAISE EXCEPTION '조건에 맞는 심사 계정이 % 명이에요 (1명이어야 함). 닉네임을 확인하세요.', n;
  END IF;
  SELECT u.id INTO a FROM users u
   WHERE u.nickname = nick AND u.profile_completed AND u.kakao_id ~ '^[0-9]+$'
     AND u.created_at > now() - interval '3 days'
     AND NOT EXISTS (SELECT 1 FROM couple_members m WHERE m.user_id = u.id);

  -- 심사 계정이 커플 연결 화면에서 만든 대기 중 초대(PENDING 커플)는 위 조건(couple_members 없음)으로 이미 걸러짐

  INSERT INTO users (id, kakao_id, nickname, profile_completed, created_at)
  VALUES (b, 'demo:partner', '지호', true, now());
  INSERT INTO user_settings (user_id, notify_memory_today, notify_partner_post, updated_at)
  VALUES (b, true, true, now());

  INSERT INTO couples (id, invite_code, invite_code_expires_at, started_at, status, created_at)
  VALUES (c, NULL, NULL, today - 437, 'CONNECTED', now());
  INSERT INTO couple_members (couple_id, user_id, joined_at) VALUES (c, a, now()), (c, b, now());

  FOR p IN
    SELECT * FROM (VALUES
      (1, 0,   'a', E'퇴근길에 같이 붕어빵 사 먹었다.\n팥이 꼬리까지 꽉 차 있어서 둘 다 감동\n내일도 저 가게 가자'),
      (2, 0,   'b', E'오늘은 각자 바빴지만 저녁 통화로 하루 정리.\n목소리 들으니까 피곤이 좀 풀렸다.'),
      (3, 1,   'a', E'비 오는 날 우산 하나로 걸었다 ☔️\n어깨 반쪽씩 젖었지만 웃겼음'),
      (4, 2,   'b', E'새로 생긴 파스타집 성공!\n다음엔 크림 말고 오일로 먹어보기'),
      (5, 3,   'a', E'영화 보다가 둘 다 졸았다 😴\n그래도 팝콘은 다 먹음'),
      (6, 365, 'a', E'작년 오늘, 처음으로 같이 한강 피크닉 🌿\n돗자리 깔자마자 바람이 엄청 불었지'),
      (7, 730, 'b', E'재작년 오늘의 기록. 첫 여행 계획 세우기!')
    ) AS t(ord, days_ago, who, body) ORDER BY ord
  LOOP
    pid := gen_random_uuid();
    INSERT INTO posts (id, couple_id, author_id, content, posted_date, created_at, updated_at)
    VALUES (pid, c, CASE p.who WHEN 'a' THEN a ELSE b END, p.body, today - p.days_ago, now(), now());
    IF first_post IS NULL THEN first_post := pid; END IF;
  END LOOP;

  INSERT INTO reactions (id, post_id, user_id, emoji_type, created_at) VALUES
    (gen_random_uuid(), first_post, b, 'HEART', now()),
    (gen_random_uuid(), first_post, b, 'LAUGH', now());

  INSERT INTO calendar_memos (id, couple_id, author_id, memo_date, title, description, color, created_at, updated_at) VALUES
    (gen_random_uuid(), c, a, today + 5,  '1주년 기념일 🎂', '레스토랑 7시 예약', '#FF7A8A', now(), now()),
    (gen_random_uuid(), c, b, today + 12, '부산 여행', NULL, '#6FB3E8', now(), now()),
    (gen_random_uuid(), c, a, today,      '치과 예약', NULL, '#7CC69A', now(), now());

  INSERT INTO bucket_items (id, couple_id, created_by, title, description, target_date, completed, completed_post_id, completed_at, created_at) VALUES
    (gen_random_uuid(), c, a, '오로라 보러 가기', '아이슬란드 겨울', DATE '2027-01-15', false, NULL, NULL, now()),
    (gen_random_uuid(), c, b, '같이 마라톤 10km 완주', NULL, NULL, false, NULL, NULL, now()),
    (done_id,           c, a, '한강 피크닉', NULL, NULL, true, NULL, now(), now());

  RAISE NOTICE '완료: 심사 계정 % ↔ demo:partner %, 커플 %', a, b, c;
END $$;

COMMIT;
