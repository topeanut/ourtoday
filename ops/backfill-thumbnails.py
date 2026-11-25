"""
예전 게시글 사진의 피드용 썸네일을 한 번에 만듦 (post_images.thumbnail_key 가 null 인 것만).
웹 업로드와 같은 규칙: 긴 변 800px, JPEG 품질 80, 키는 {원본키에서 확장자 뺀 것}_thumb.jpg

서버에서 실행 (서버에 Python 설치 없이 일회용 컨테이너로):
  cd ~/ourtoday && set -a && . ./.env && set +a && \
  docker run --rm --network ourtoday_default -v "$PWD/ops:/ops:ro" \
    -e STORAGE_ENDPOINT -e STORAGE_REGION -e STORAGE_BUCKET -e STORAGE_ACCESS_KEY -e STORAGE_SECRET_KEY \
    -e DB_NAME -e DB_USERNAME -e DB_PASSWORD \
    python:3.12-slim sh -c "pip install -q --root-user-action=ignore boto3 pillow 'psycopg[binary]' && python /ops/backfill-thumbnails.py"

--dry-run 을 붙이면 만들기만 하고 업로드·DB 기록은 안 함. 여러 번 돌려도 안전 (이미 있는 건 건너뜀)
비밀값은 출력하지 않음
"""
import io
import os
import sys

import boto3
import psycopg
from botocore.config import Config
from PIL import Image, ImageOps

EDGE = 800
QUALITY = 80


def thumbnail_key_of(image_key: str) -> str:
    return image_key[: image_key.rfind(".")] + "_thumb.jpg"


def make_thumbnail(data: bytes) -> bytes:
    image = ImageOps.exif_transpose(Image.open(io.BytesIO(data)))
    image.thumbnail((EDGE, EDGE), Image.LANCZOS)
    if image.mode in ("RGBA", "LA", "P"):
        image = image.convert("RGBA")
        background = Image.new("RGB", image.size, "white")
        background.paste(image, mask=image.split()[-1])
        image = background
    elif image.mode != "RGB":
        image = image.convert("RGB")
    out = io.BytesIO()
    image.save(out, "JPEG", quality=QUALITY, optimize=True)
    return out.getvalue()


def main() -> int:
    dry_run = "--dry-run" in sys.argv
    bucket = os.environ.get("STORAGE_BUCKET") or "ourtoday-images"
    s3 = boto3.client(
        "s3",
        endpoint_url=os.environ["STORAGE_ENDPOINT"],
        region_name=os.environ.get("STORAGE_REGION") or "auto",
        aws_access_key_id=os.environ["STORAGE_ACCESS_KEY"],
        aws_secret_access_key=os.environ["STORAGE_SECRET_KEY"],
        # 서버(Java)와 같은 path-style. 최신 boto3 의 기본 체크섬 헤더는 S3 호환 저장소가 거부할 수 있어 필요할 때만
        config=Config(
            s3={"addressing_style": "path"},
            request_checksum_calculation="when_required",
            response_checksum_validation="when_required",
        ),
    )

    with psycopg.connect(
        host="postgres",
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
    ) as conn:
        rows = conn.execute(
            "select id, image_key from post_images where thumbnail_key is null order by id"
        ).fetchall()
        print(f"대상 {len(rows)}장{' (dry-run)' if dry_run else ''}")

        done = failed = 0
        for image_id, key in rows:
            try:
                original = s3.get_object(Bucket=bucket, Key=key)["Body"].read()
                thumb = make_thumbnail(original)
                thumb_key = thumbnail_key_of(key)
                if not dry_run:
                    s3.put_object(Bucket=bucket, Key=thumb_key, Body=thumb, ContentType="image/jpeg")
                    conn.execute(
                        "update post_images set thumbnail_key = %s where id = %s", (thumb_key, image_id)
                    )
                    conn.commit()
                done += 1
                print(f"✅ {key}  {len(original) // 1024}KB → {len(thumb) // 1024}KB")
            except Exception as e:  # 한 장 실패해도 나머지는 계속
                conn.rollback()
                failed += 1
                print(f"❌ {key}  {type(e).__name__}: {e}")

        print(f"완료 {done}장, 실패 {failed}장")
        return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
