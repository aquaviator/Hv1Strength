#!/bin/bash
set -e

echo "Generating raster icons..."
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
  git add "app/src/main/res/$dir/ic_launcher.png" "app/src/main/res/$dir/ic_launcher_round.png"
done

