#!/bin/sh
# Regenerates the icon sources and renders the iOS app icon PNGs. Needs rsvg-convert (`brew install librsvg`).
set -e
cd "$(dirname "$0")/.."
python3 design/generate_icons.py
icons=iosApp/iosApp/Assets.xcassets/AppIcon.appiconset
rsvg-convert -w 1024 -h 1024 design/app-icon.svg -o "$icons/AppIcon.png"
rsvg-convert -w 1024 -h 1024 design/app-icon-dark.svg -o "$icons/AppIcon-Dark.png"
rsvg-convert -w 1024 -h 1024 design/app-icon-tinted.svg -o "$icons/AppIcon-Tinted.png"
# App Store icons must be opaque; rsvg always writes an alpha channel, so flatten it away.
for png in "$icons"/AppIcon*.png; do
    sips -s format jpeg -s formatOptions best "$png" --out "$png.jpg" >/dev/null
    sips -s format png "$png.jpg" --out "$png" >/dev/null
    rm "$png.jpg"
done
