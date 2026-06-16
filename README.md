# ClickFeeder

A client-side Fabric mod for Minecraft 26.x. Right-click with breeding food in your hand to feed eligible nearby adult animals within reach. Baby animals are ignored so food is not wasted speeding up growth. Automatic hotbar switching and inventory restocking keep you going without breaking flow.

## What it does

- Hold any breeding food (wheat, carrots, seeds, beetroot, etc.)
- Right-click into the air (the **Use Item** keybind)
- Up to 20 eligible adult animals within a **5-block radius** get handled per click
- Baby animals are skipped before food counting, hotbar switching, restocking, or interaction
- Automatically switches between hotbar slots when your current stack runs out
- Pulls matching food from your main inventory into the hotbar when needed
- Cancels the original item-use when it handles a feed batch so edible food is not eaten after a stale target
- Limits to 20 animals per click to prevent server overload

## How it works

1. On right-click, scans for nearby adult animals that can breed with the held food
2. Counts total available food across your inventory
3. Feeds animals one by one, automatically restocking your hand:
   - First uses food already in hotbar slots
   - Then swaps food from main inventory into hotbar
4. Skips adult animals this client just fed for the normal love-mode window
5. Treats clicks with no valid adult targets as normal right-clicks
6. Stops when all valid adults are handled or food runs out

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 26.x |
| Fabric Loader | 0.19.3+ |
| Fabric API | Compatible build for your Minecraft 26.x version |
| Java | 25 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft 26.x version
2. Download the matching [Fabric API](https://modrinth.com/mod/fabric-api) build for your Minecraft 26.x version and place it in `.minecraft/mods/`
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

Requires Java 25 and a Gradle wrapper (included). The project uses Fabric Loom 1.17+ with Mojang mappings.

## License

MIT
