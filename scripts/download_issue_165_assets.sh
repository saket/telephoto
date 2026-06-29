#!/usr/bin/env bash
set -euo pipefail

readonly file_id="118Pgv22HmX0YwnycIVtVO5Vyx3or2g_9"
readonly asset_dir="sample/src/androidMain/assets/issue165"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

cookie_jar="$tmpdir/cookies.txt"
warning_html="$tmpdir/warning.html"
zip_file="$tmpdir/heavy-files.zip"

curl -fsSL \
  -c "$cookie_jar" \
  -b "$cookie_jar" \
  "https://drive.usercontent.google.com/download?id=${file_id}&export=download" \
  -o "$warning_html"

confirm="$(perl -ne 'print "$1\n" if /name="confirm" value="([^"]+)"/' "$warning_html")"
uuid="$(perl -ne 'print "$1\n" if /name="uuid" value="([^"]+)"/' "$warning_html")"

if [[ -z "$confirm" || -z "$uuid" ]]; then
  echo "Could not find Google Drive download confirmation fields." >&2
  exit 1
fi

curl -fL \
  -c "$cookie_jar" \
  -b "$cookie_jar" \
  "https://drive.usercontent.google.com/download?id=${file_id}&export=download&confirm=${confirm}&uuid=${uuid}" \
  -o "$zip_file"

mkdir -p "$asset_dir"
bsdtar -xf "$zip_file" -C "$tmpdir" "heavy files/25mb.jpg" "heavy files/50mb.jpg"
cp "$tmpdir/heavy files/25mb.jpg" "$asset_dir/25mb.jpg"
cp "$tmpdir/heavy files/50mb.jpg" "$asset_dir/50mb.jpg"

du -h "$asset_dir/25mb.jpg" "$asset_dir/50mb.jpg"
