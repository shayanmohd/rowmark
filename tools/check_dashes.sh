#!/usr/bin/env bash
# Fails when an em dash (U+2014) or en dash (U+2013) appears anywhere in the project a person could read.
set -u
cd "$(dirname "$0")/.."
DASH="$(printf '\xe2\x80\x93|\xe2\x80\x94')"
hits="$(LC_ALL=C grep -rlIE "$DASH" --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.kotlin --exclude='*.ttf' --exclude='*.jks' . 2>/dev/null)"
if [ -n "$hits" ]; then echo "DASHES FOUND in:"; echo "$hits"; exit 1; fi
echo "no em or en dashes anywhere in the project"
