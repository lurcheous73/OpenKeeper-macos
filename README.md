OpenKeeper
=================

An open source remake of the Dungeon Keeper II game and engine.

Goal is to fully implement the game (version 1.7 with 3 bonus packs) as open source cross platform version, with minimal or no changes at all, using the original game assets. So it will require the original game to play / develop. Future development could have fan made graphics (to at least enable standalone version) and features.

OpenKeeper is written in Java using [JMonkeyEngine](http://jmonkeyengine.org/). Currently we are using JME 3.9 + Java 25.

Builds are available from the CI. This fork currently produces native packages for macOS Apple Silicon, macOS Intel, and Windows x64 from the same game code.

[![Build Status](https://github.com/lurcheous73/OpenKeeper-macos/actions/workflows/gradle.yml/badge.svg)](../../actions)

macOS (Apple Silicon + Intel)
=============================

This fork builds OpenKeeper natively for both current Mac architectures:

- Apple Silicon (`arm64`) on a native Apple Silicon GitHub runner.
- Intel (`x86_64`) on a native Intel GitHub runner.
- No Rosetta is required for the Apple Silicon build.
- The macOS package is a self-contained `.dmg` made with JDK 25 `jpackage`, so players do not need to install Java separately.
- macOS launches with `-XstartOnFirstThread`, as required by GLFW/LWJGL.

To build a DMG locally on a Mac with JDK 25 installed:

```bash
./gradlew clean test macDmg
```

The output is written to `build/macos/`.

Original Dungeon Keeper II data is still required. For a legally-owned GOG installer, install `innoextract` and use the helper:

```bash
brew install innoextract
bash scripts/extract-gog-dk2-macos.sh /path/to/setup_dungeon_keepertm_2.exe
```

Keep the matching GOG `.bin` payload beside the `.exe`. The helper extracts the Windows installer without Wine and prints the exact directory to select when OpenKeeper asks for the Dungeon Keeper II installation folder. Game assets are never included in OpenKeeper builds.

Windows x64
===========

The same fork also builds a native self-contained Windows x64 package. The Windows build uses the same gameplay engine and therefore includes the same fog-of-war and compatibility fixes as the macOS builds.

- Windows x64 / AMD64 only.
- No separate Java installation is required; the JDK 25 runtime is bundled by `jpackage`.
- The CI artifact is a portable `OpenKeeper-Windows-x86_64.zip`. Extract it and run `OpenKeeper.exe`.
- Original Dungeon Keeper II 1.7 game data is still required and is never redistributed with OpenKeeper.

To build the Windows portable package locally from PowerShell with JDK 25 installed:

```powershell
.\gradlew.bat clean test windowsZip
```

The output is written to `build\windows\`, including a SHA-256 checksum file.

Fog of war
==========

This fork includes a Dungeon Keeper II-style fog-of-war implementation shared by macOS and Windows: unexplored terrain is concealed as taggable earth, creature perception reveals nearby terrain, explored areas retain map knowledge, scripted camera sequences retain terrain fog, and current perception drives the moving fog layer. The implementation is shared Java/JMonkeyEngine code rather than a platform-specific renderer fork.

[Here is my YouTube channel where I sometimes publish videos of the progress](https://www.youtube.com/user/Kaljis83/videos).

Contact
========

For persistent discussion and/or feedback, try [this forum at keeperklan.com](https://keeperklan.com/forums/101-OpenKeeper). Also we have opened a [Discord channel](https://discord.gg/e2Dnqkn).

Contributing
=============

We are always looking for talented people to join us. I'll try to create as many issues I possibly can and keep them simple and small. You can start from these or come join us on IRC or email. Pull requests are always welcome! See [how to set up the project](https://github.com/tonihele/OpenKeeper/wiki/How-to-set-up-OpenKeeper).

Please keep in mind:
 - Learn to use GIT (forking, pull requests, etc)
 - Coding style
    - Global variables on top
    - Javadoc on at least public & protected methods
    - Organize imports
    - Default Netbeans code formatting
    - Code header (the license)

- One feature per branch / commit
- If in doubt, ask! :)

License
==========

GNU GPLv3 or later. You should add license.txt to your IDE to appear as automatic header in code files.

Resources
=========

 * [Reversal of DK2 Binary File Formats](http://keeperklan.com/threads/4623-Reversal-of-DKII-Binary-File-Formats)
 * [DK2 texture formats](http://keeperklan.com/threads/220-DK2-texture-format)
 * [Jadex AgentKeeper, a Dungeon Keeper like game](https://code.google.com/p/jadex-agentkeeper/)
 * [kwd, library for loading kwd/klb files](https://github.com/werkt/kwd)
 * [Sound & Video formats](http://wiki.multimedia.cx/index.php?title=Electronic_Arts_Formats)
 * [A write-up about the walls in DK2](http://simonschreibt.de/gat/dungeon-keeper-2-walls/)
 * [DK2 editor manual, contains a lot of hints on how the game should work](http://keeper.lubiki.pl/dk2_docs/dk2_editor_manual.htm)

