#!/bin/bash
set -e

# Original image is 1254x1254. Circle diameter is ~1164.
# 1. Generate standard/round launcher icons (from original raster directly)
for size in 48 72 96 144 192; do
  case $size in
    48) dir="mipmap-mdpi" ;;
    72) dir="mipmap-hdpi" ;;
    96) dir="mipmap-xhdpi" ;;
    144) dir="mipmap-xxhdpi" ;;
    192) dir="mipmap-xxxhdpi" ;;
  esac
  convert "HV1 Icon.png" -resize ${size}x${size} "app/src/main/res/$dir/ic_launcher.png"
  convert "HV1 Icon.png" -resize ${size}x${size} "app/src/main/res/$dir/ic_launcher_round.png"
done

# 2. Extract foreground: make background (#0A0D10 / black) transparent
convert "HV1 Icon.png" -fuzz 10% -transparent black /tmp/fg.png

# 3. Create padded foreground for adaptive icons
# Padded to 1904x1904 so the 1164 circle fits in 66dp of 108dp total (108/66 * 1164 = 1904)
convert /tmp/fg.png -background transparent -gravity center -extent 1904x1904 /tmp/fg_padded.png

# sizes for adaptive foregrounds (108dp): mdpi(108), hdpi(162), xhdpi(216), xxhdpi(324), xxxhdpi(486)
for size in 108 162 216 324 486; do
  case $size in
    108) dir="mipmap-mdpi" ;;
    162) dir="mipmap-hdpi" ;;
    216) dir="mipmap-xhdpi" ;;
    324) dir="mipmap-xxhdpi" ;;
    486) dir="mipmap-xxxhdpi" ;;
  esac
  convert /tmp/fg_padded.png -resize ${size}x${size} "app/src/main/res/$dir/ic_launcher_foreground.png"
done

# Update adaptive icon XMLs to point to the new mipmap foreground
cat << 'INNER_EOF' > app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground_monochrome" />
</adaptive-icon>
INNER_EOF

cp app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml

