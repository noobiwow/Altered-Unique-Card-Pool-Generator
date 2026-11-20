#!/usr/bin/env bash
# Downloads the card data the backend reads into backend/cardsdata/ (not
# committed): for each set, the unique prints and their effect fragments, plus
# the small reference tables (sets, factions, keywords, types...). Only these
# files are downloaded from the CardsData repository. Restart the backend after
# an update.
#
#   ./update-cardsdata.sh                latest CardsData (main)
#   ./update-cardsdata.sh <branch|tag>   a given version
#   CARDSDATA_REPO=<url or file://path> ./update-cardsdata.sh   another clone
set -euo pipefail
cd "$(dirname "$0")"

REPO="${CARDSDATA_REPO:-https://github.com/Altered-Re-Union/CardsData.git}"
REF="${1:-}"
DEST=backend/cardsdata
TABLES="CardFamilies CardFamilySubTypes CardSubTypes CardTypes EffectFragments Factions Keywords Sets"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP" "$DEST.new"' EXIT

echo "Downloading card data from $REPO${REF:+ ($REF)}..."
git clone --quiet --depth 1 --filter=blob:none --no-checkout ${REF:+--branch "$REF"} "$REPO" "$TMP/repo"
patterns=()
for table in $TABLES; do patterns+=("/data/csv/$table.csv"); done
patterns+=("/data/csv/Unique/*/UniquePrints.csv" "/data/csv/Unique/*/UniquePrintEffectFragments.csv")
git -C "$TMP/repo" sparse-checkout set --no-cone "${patterns[@]}"
git -C "$TMP/repo" checkout --quiet

CSV="$TMP/repo/data/csv"
rm -rf "$DEST.new"
mkdir -p "$DEST.new/Unique"
for table in $TABLES; do
  cp "$CSV/$table.csv" "$DEST.new/"
done
for set_dir in "$CSV"/Unique/*/; do
  set_name=$(basename "$set_dir")
  [ -f "$set_dir/UniquePrints.csv" ] || continue
  mkdir -p "$DEST.new/Unique/$set_name"
  cp "$set_dir/UniquePrints.csv" "$set_dir/UniquePrintEffectFragments.csv" "$DEST.new/Unique/$set_name/"
done
[ -n "$(ls "$DEST.new/Unique")" ] || { echo "No set found in $REPO" >&2; exit 1; }
echo "$(git -C "$TMP/repo" rev-parse HEAD) $(git -C "$TMP/repo" log -1 --format=%cI)" > "$DEST.new/VERSION"

# Swap in the new data only once it is complete.
rm -rf "$DEST.old"
[ -d "$DEST" ] && mv "$DEST" "$DEST.old"
mv "$DEST.new" "$DEST"
rm -rf "$DEST.old"

echo "Card data updated in $DEST: $(ls "$DEST/Unique" | tr '\n' ' ')($(du -sh "$DEST" | cut -f1))"
echo "CardsData version: $(cat "$DEST/VERSION")"
