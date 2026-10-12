#!/bin/sh
# ============================================================================
# ローカル用と E2E 用の画像のバケットを作る（plan 001 第 7 節、plan 004 第 6.1 節）。何度実行しても同じ結果になる。
# 本番では、同じ設定を基盤の構成（IaC）で作る。
# ============================================================================
set -eu

S3="aws --endpoint-url http://seaweedfs:8333 s3api"

# 受付と配信のバケットを 1 組作る
#   $1：受付のバケット
#   $2：配信のバケット
setup_buckets() {
  incoming="$1"
  public="$2"

  for bucket in "$incoming" "$public"; do
    $S3 head-bucket --bucket "$bucket" 2>/dev/null || $S3 create-bucket --bucket "$bucket"
  done

  # 受付：管理画面（開発サーバーと E2E）から署名付き URL で PUT する
  $S3 put-bucket-cors --bucket "$incoming" --cors-configuration '{
    "CORSRules": [{
      "AllowedOrigins": ["http://localhost:5173", "http://localhost:4173"],
      "AllowedMethods": ["PUT"],
      "AllowedHeaders": ["*"],
      "MaxAgeSeconds": 600
    }]
  }'

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

setup_buckets "$INCOMING_BUCKET" "$PUBLIC_BUCKET"
setup_buckets "$E2E_INCOMING_BUCKET" "$E2E_PUBLIC_BUCKET"
