#!/usr/bin/env bash

# Written by AI.
#
# Generate 8 test images (normal, flips, rotations) to verify EXIF orientation handling.
# Real cameras often store pixels in sensor order and rely on the EXIF Orientation tag
# for correct display. This script simulates that by:
#   - Baking the inverse transform into pixels
#   - Writing the matching EXIF Orientation value (1–8)
#
# Usage: ./generate_exif_orientations.sh <input_image>
#
# Requires: ImageMagick (magick/convert) and ExifTool
# brew install imagemagick
# brew install exiftool

set -eo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <input_image>"
  exit 1
fi

INPUT="$1"
DIRNAME=$(dirname "$INPUT")
BASENAME=$(basename "$INPUT")
EXT="${BASENAME##*.}"
NAME="${BASENAME%.*}"

# Prefer 'magick' (IM7) else 'convert'
if command -v magick >/dev/null 2>&1; then IM_CMD="magick"
elif command -v convert >/dev/null 2>&1; then IM_CMD="convert"
else echo "❌ Install ImageMagick (magick/convert)"; exit 1; fi

# exiftool wrapper (ignore minor XMP warnings)
set_orientation () {
  exiftool -overwrite_original -m -EXIF:Orientation="$1" -n "$2" >/dev/null
}

# Normalize input's tag to 1
set_orientation 1 "$INPUT"

make_variant () {
  value="$1"; suffix="$2"
  out="${DIRNAME}/${NAME}_${suffix}.${EXT}"

  case "$value" in
    1) "$IM_CMD" "$INPUT" "$out" ;;
    2) "$IM_CMD" "$INPUT" -flop "$out" ;;
    3) "$IM_CMD" "$INPUT" -rotate 180 "$out" ;;
    4) "$IM_CMD" "$INPUT" -flip "$out" ;;
    5) "$IM_CMD" "$INPUT" -transpose "$out" ;;
    6) "$IM_CMD" "$INPUT" -rotate 270 "$out" ;; # inverse of rot90CW
    7) "$IM_CMD" "$INPUT" -transverse "$out" ;;
    8) "$IM_CMD" "$INPUT" -rotate 90 "$out" ;;  # inverse of rot90CCW
    *) echo "Unknown orientation $value"; exit 1 ;;
  esac

  set_orientation "$value" "$out"
  echo "✔ ${out}  (EXIF Orientation=$value)"
}

make_variant 1 normal
make_variant 2 flipH
make_variant 3 rot180
make_variant 4 flipV
make_variant 5 transpose
make_variant 6 rot90CW
make_variant 7 transverse
make_variant 8 rot90CCW

echo "✅ Done."
