# Item Get!

Item Get! is a configurable reminder mod for Minecraft 1.21.1 on NeoForge. Inspired by Nintendo's item acquisition presentation in *The Legend of Zelda: Breath of the Wild*, it lets modpack authors create cinematic first-time and milestone notifications through an in-game visual editor.

## Features

- Item acquisition and entity kill counters
- Health, hunger, effect, weather, time, biome, and structure triggers
- Custom title, description, icon, sound, and singleplayer pause behavior
- Creative-tab item picker with full item component data, including potion and enchanted-item variants
- Built-in, vanilla, modded, and user-provided sound selection
- External MP3 and OGG sound support
- Searchable selection lists and batch reminder management
- Per-player progress preserved across death, End return, logout, and rule edits
- Chinese and English localization

## Usage

Press `I` by default to open the reminder manager. The manager is editable by operators in multiplayer. The reminder close key defaults to `R`; both keys can be changed in Minecraft's controls screen.

Custom sounds can be placed in:

```text
config/item_get/sounds
```

MP3 and OGG files in that directory appear under the custom sound filter.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.235 or newer in the 21.1 series
- Java 21

## Building

Clone the repository and run:

```powershell
./gradlew.bat clean build
```

The distributable JAR is generated in `build/libs/`. The unshaded `-slim.jar` is a development artifact and is not the release file.

## License

Item Get! is copyright (C) 2026 kuzhi and licensed under the [GNU General Public License v3.0 only](LICENSE).

The release JAR bundles a relocated copy of JLayer 1.0.1 for MP3 decoding. See [THIRD_PARTY_NOTICES.md](src/main/resources/THIRD_PARTY_NOTICES.md).

The presentation is inspired by Nintendo's *The Legend of Zelda: Breath of the Wild*. This independent fan project is not affiliated with or endorsed by Nintendo and contains no assets from The Legend of Zelda series.
