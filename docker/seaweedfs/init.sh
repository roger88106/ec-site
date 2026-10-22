#!/bin/sh
# ============================================================================
# 画像のバケットを作る。何度実行しても同じ結果になる。
# 環境変数 SLOT_INCOMING_BUCKET、SLOT_PUBLIC_BUCKET、SLOT_ADMIN_ORIGIN を指定したときは、そのスロットの 1 組だけを作る
# （scripts/dev/slot.mjs が使う）。指定しなければ、ローカル用と E2E 用の 2 組を作る。
# 本番では、同じ設定を基盤の構成（IaC）で作る。
# ============================================================================
set -eu

S3="aws --endpoint-url http://seaweedfs:8333 s3api"

# 受付と配信のバケットを 1 組作る
#   $1：受付のバケット
#   $2：配信のバケット
#   $3：受付の CORS の許可元（JSON の文字列を、カンマで並べたもの）
setup_buckets() {
  incoming="$1"
  public="$2"
  origins="$3"

  for bucket in "$incoming" "$public"; do
    $S3 head-bucket --bucket "$bucket" 2>/dev/null || $S3 create-bucket --bucket "$bucket"
  done

  # 受付：管理画面（開発サーバーと E2E）から署名付き URL で PUT する
  $S3 put-bucket-cors --bucket "$incoming" --cors-configuration "{
    \"CORSRules\": [{
      \"AllowedOrigins\": [$origins],
      \"AllowedMethods\": [\"PUT\"],
      \"AllowedHeaders\": [\"*\"],
      \"MaxAgeSeconds\": 600
    }]
  }"

  # 受付：登録されなかったファイルは 1 日で消す
  $S3 put-bucket-lifecycle-configuration --bucket "$incoming" --lifecycle-configuration '{
    "Rules": [{ "ID": "expire-uploads", "Status": "Enabled", "Filter": { "Prefix": "" }, "Expiration": { "Days": 1 } }]
  }'

  # 配信：だれでも読める（本番は CDN の後ろ）
  $S3 put-bucket-policy --bucket "$public" --policy "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [{
      \"Effect\": \"Allow\",
      \"Principal\": \"*\",
      \"Action\": [\"s3:GetObject\"],
      \"Resource\": [\"arn:aws:s3:::$public/*\"]
    }]
  }"

  echo "画像のバケットを用意した：$incoming、$public"
}

if [ -n "${SLOT_INCOMING_BUCKET:-}" ]; then
  seed_bucket="$SLOT_PUBLIC_BUCKET"
  setup_buckets "$SLOT_INCOMING_BUCKET" "$SLOT_PUBLIC_BUCKET" "\"$SLOT_ADMIN_ORIGIN\""
else
  seed_bucket="$PUBLIC_BUCKET"
  default_origins='"http://localhost:5173", "http://localhost:4173"'
  setup_buckets "$INCOMING_BUCKET" "$PUBLIC_BUCKET" "$default_origins"
  setup_buckets "$E2E_INCOMING_BUCKET" "$E2E_PUBLIC_BUCKET" "$default_origins"
fi

# seed の商品画像を、配信のバケットの assets/ に写す（db/seed/local/R__media_seed.sql）。
# sync は、バケットにないものと、大きさの違うものだけを写す（1 回の呼び出しで済ませ、CI の時間を延ばさない）。
# アセットのファイルはアプリが上書きしないので、seed の画像はいつも seed と同じになる。
# E2E のバケットには写さない（E2E は画像を API で作る）
aws --endpoint-url http://seaweedfs:8333 s3 sync /seed/assets "s3://$seed_bucket/assets/" \
  --exclude "*" --include "*.webp" --content-type image/webp --size-only --only-show-errors
echo "seed の商品画像を用意した：$seed_bucket/assets/"
