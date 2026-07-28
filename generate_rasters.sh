#!/bin/bash
ICON_SRC="HV1 Icon.png"

# Define sizes
declare -A sizes=(
    ["mdpi"]="48x48"
    ["hdpi"]="72x72"
    ["xhdpi"]="96x96"
    ["xxhdpi"]="144x144"
    ["xxxhdpi"]="192x192"
)

# Generate square and round variants
for res in "${!sizes[@]}"; do
    size="${sizes[$res]}"
    dir="app/src/main/res/mipmap-${res}"
    mkdir -p "$dir"
    
    # Square
    convert "$ICON_SRC" -resize "$size" "$dir/ic_launcher.png"
    # Round
    convert "$ICON_SRC" -resize "$size" \
        \( +clone -alpha extract -draw 'fill black polygon 0,0 0,15 15,0 fill white circle 15,15 15,0' \
        \( +clone -flip \) -compose Multiply -composite \
        \( +clone -flop \) -compose Multiply -composite \) \
        -alpha off -compose CopyOpacity -composite "$dir/ic_launcher_round.png"
done

