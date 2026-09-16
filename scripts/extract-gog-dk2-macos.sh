#!/bin/bash
# Extract a legally-owned GOG Dungeon Keeper II installer for OpenKeeper on macOS.
# No game data is distributed by this project.
set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  ./scripts/extract-gog-dk2-macos.sh /path/to/setup_dungeon_keeper_2.exe [destination]

The matching GOG .bin file(s) must be in the same directory as the .exe.
The default destination is:
  ~/Library/Application Support/OpenKeeper/Dungeon Keeper II
EOF
}

if [[ ${1:-} == "-h" || ${1:-} == "--help" || $# -lt 1 ]]; then
    usage
    exit $([[ $# -lt 1 ]] && echo 2 || echo 0)
fi

installer="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
destination="${2:-$HOME/Library/Application Support/OpenKeeper/Dungeon Keeper II}"

if [[ ! -f "$installer" ]]; then
    echo "Installer not found: $installer" >&2
    exit 2
fi

if ! command -v innoextract >/dev/null 2>&1; then
    cat >&2 <<'EOF'
innoextract is required to unpack the GOG Inno Setup installer.
Install it with Homebrew:

  brew install innoextract
EOF
    exit 3
fi

installer_dir="$(dirname "$installer")"
if ! find "$installer_dir" -maxdepth 1 -type f -iname '*.bin' -print -quit | grep -q .; then
    echo "Warning: no .bin payload was found beside the installer." >&2
    echo "GOG multipart installers normally require the matching .bin file(s)." >&2
fi

mkdir -p "$destination"

echo "Extracting Dungeon Keeper II..."
echo "  installer:   $installer"
echo "  destination: $destination"
innoextract --extract --output-dir "$destination" "$installer"

# OpenKeeper validates the game directory using Data/editor/maps/FrontEnd3DLevel.kwd.
# Find it case-insensitively because the original Windows media is not consistent
# about filename casing, while macOS may be using a case-sensitive volume.
test_file="$(find "$destination" -type f -iname 'FrontEnd3DLevel.kwd' -print -quit)"
if [[ -z "$test_file" ]]; then
    cat >&2 <<EOF
Extraction completed, but OpenKeeper's validation file was not found:
  Data/editor/maps/FrontEnd3DLevel.kwd

Check the GOG installer/payload pair and the extraction output at:
  $destination
EOF
    exit 4
fi

dk2_root="$(dirname "$(dirname "$(dirname "$(dirname "$test_file")")")")"

cat <<EOF

Dungeon Keeper II data extracted successfully.

When OpenKeeper asks for the Dungeon Keeper II installation folder, select:
  $dk2_root

Validation file:
  $test_file
EOF
