# ClickFeeder

A client-side Fabric mod for Minecraft 26.1.2. Right-click with breeding food in your hand to instantly feed every breedable animal within reach. Hotbar auto-switching and inventory restock keep you going without breaking flow.

## What it does

- Hold any breeding food (wheat, carrots, seeds, beetroot, etc.)
- Right-click into the air (the **Use Item** keybind)
- All feedable animals within a **5-block radius** get fed at once
- Automatically cycles through hotbar slots when your hand runs out
- Pulls matching food from your main inventory into empty hotbar slots

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.18.4+ |
| Fabric API | 0.147.0+ |
| Java | 25 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.1.2
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.1.2 and place it in `.minecraft/mods/`
3. Download the latest `clickfeeder-*.jar` from [Releases](https://github.com/Mxlted/clickfeeder/releases)
4. Place the JAR in `.minecraft/mods/`
5. Launch the game and hold some wheat

## Building from source

```bash
git clone https://github.com/Mxlted/clickfeeder.git
cd clickfeeder
./gradlew build
```

The compiled JAR will be in `build/libs/`.

Requires Java 25 and a Gradle wrapper (included). The project uses Fabric Loom 1.15+ with Mojang mappings.

## License

MIT
