#!/bin/sh
# ============================================================================
# 画像の変換のテスト用の画像を作る（plan 001 の「画像の変換」）。作った画像はコミットする。
#
#   docker run --rm -v "$PWD:/out" debian:trixie-slim sh /out/generate.sh
#
# 左が赤、右が青の 64x32。JPEG・HEIC・AVIF には、向き（Orientation=6：時計回りに 90 度）、
# 位置情報、撮影機器を付ける。向きを反映すると 32x64 になる。
# ============================================================================
set -eu
apt-get update -qq >/dev/null
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq imagemagick libimage-exiftool-perl libheif-examples \
  libheif-plugin-x265 libheif-plugin-aomenc >/dev/null
cd /out
convert -size 32x32 xc:red -size 32x32 xc:blue +append -quality 90 /tmp/base.jpg
cp /tmp/base.jpg source.jpg
exiftool -q -overwrite_original -Orientation#=6 -GPSLatitude=35.681 -GPSLatitudeRef=N \
  -GPSLongitude=139.767 -GPSLongitudeRef=E -Make=TestCam source.jpg
convert /tmp/base.jpg source.png
convert /tmp/base.jpg -quality 90 source.webp
convert /tmp/base.jpg BMP3:source.bmp
heif-enc -q 80 -o source.heic source.jpg
heif-enc -A -q 80 -o source.avif source.jpg
# 先頭だけの、壊れた HEIC
head -c 300 source.heic > broken.heic
