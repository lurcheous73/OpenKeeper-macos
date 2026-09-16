#!/bin/bash
# Build a native OpenKeeper macOS DMG using the JDK that is running Gradle.
set -euo pipefail

project_dir="${OPENKEEPER_PROJECT_DIR:-$PWD}"
app_name="OpenKeeper"
main_jar="${OPENKEEPER_MAIN_JAR:-OpenKeeper.jar}"
main_class="${OPENKEEPER_MAIN_CLASS:-toniarts.openkeeper.macos.MacLauncher}"
input_dir="$project_dir/build/install/$app_name/lib"
output_dir="$project_dir/build/macos"
jpackage_bin="${JAVA_HOME:-}/bin/jpackage"

if [[ -z "${JAVA_HOME:-}" || ! -x "$jpackage_bin" ]]; then
    echo "jpackage was not found. A full JDK 25 is required (JAVA_HOME=$JAVA_HOME)." >&2
    exit 2
fi

if [[ ! -d "$input_dir" ]]; then
    echo "OpenKeeper installDist input directory does not exist: $input_dir" >&2
    exit 3
fi

if [[ ! -f "$input_dir/$main_jar" ]]; then
    echo "OpenKeeper main JAR does not exist: $input_dir/$main_jar" >&2
    exit 4
fi

mkdir -p "$output_dir"
rm -f "$output_dir"/*.dmg

echo "Building native macOS DMG"
echo "  architecture: $(uname -m)"
echo "  Java:         $JAVA_HOME"
echo "  main JAR:     $main_jar"
echo "  main class:   $main_class"

"$jpackage_bin" \
    --type dmg \
    --name "$app_name" \
    --dest "$output_dir" \
    --input "$input_dir" \
    --main-jar "$main_jar" \
    --main-class "$main_class" \
    --vendor "OpenKeeper" \
    --description "Open source Dungeon Keeper II engine" \
    --java-options "-XstartOnFirstThread" \
    --java-options "-Djava.awt.headless=true" \
    --java-options "-Dvisualvm.display.name=OpenKeeper"

found_dmg="$(find "$output_dir" -maxdepth 1 -type f -name '*.dmg' -print -quit)"
if [[ -z "$found_dmg" ]]; then
    echo "jpackage completed without producing a DMG in $output_dir" >&2
    exit 5
fi

echo "Built: $found_dmg"
file "$found_dmg" || true
