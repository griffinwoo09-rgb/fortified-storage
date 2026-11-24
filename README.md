# Fortified Storage

A Fabric mod for Minecraft Java **26.3** that adds bigger chests, trapped chests, barrels and shulker boxes in four metal tiers. Their textures are **generated from your active resource packs**, so they match Faithful or any other pack without that pack knowing this mod exists.

## Tiers

| Tier      | Slots | Layout  | Double chest | Hardness / blast resistance | Notes |
|-----------|------:|---------|-------------:|-----------------------------|-------|
| Iron      | 54    | 9 × 6   | 108          | 5 / 6                       | |
| Gold      | 81    | 9 × 9   | 162          | 3 / 6                       | |
| Diamond   | 120   | 12 × 10 | 240          | 5 / 6                       | |
| Netherite | 165   | 15 × 11 | 330          | 50 / 1200 (like obsidian)   | Survives TNT. The item is fire-resistant and floats in lava. |

There is no copper tier: vanilla already has a copper chest.

## Blocks

- **Fortified Chest / Trapped Chest**: work like vanilla chests. Lid animation and sounds, waterlogging, blocked by a solid block or a sitting cat, hoppers, comparators, piglins. Two chests of the same kind and tier side by side become a double chest. A trapped chest outputs redstone equal to the number of players looking inside.
- **Fortified Barrel**: faces any of the six directions, opens and closes like a vanilla barrel, works with hoppers and comparators.
- **Fortified Shulker Box**: undyed plus 16 colours. Keeps its contents when broken, shows the contents tooltip, and can't hold other shulker boxes (also enforced for hoppers and the Transfer API). Dye it in a crafting grid, or wash it in a water cauldron.

## Recipes

| Result | Recipe |
|---|---|
| Iron chest / trapped chest / barrel / shulker box | 8 `#c:ingots/iron` around the vanilla block |
| Gold … | 8 `#c:ingots/gold` around the iron one |
| Diamond … | 8 `#c:gems/diamond` around the gold one |
| Netherite … | Smithing table: netherite upgrade template + diamond one + `#c:ingots/netherite` |
| Fortified trapped chest | Fortified chest of that tier + tripwire hook (shapeless) |
| Iron / gold / diamond upgrade kit | 8 of the tier's material around a stick |
| Netherite upgrade kit | Smithing table: template + diamond kit + netherite ingot |
| Dyed shulker box | Any shulker box of a tier + dye (contents kept) |

All recipes use common (`c:`) tags and appear in the recipe book.

## Creative menu

Everything is in the **Fortified Storage** tab. Mod tabs are on the second page of creative tabs, reached with the arrows at the top right. The chests, trapped chests, barrels and undyed shulker boxes are also in **Functional Blocks** right after the vanilla barrel, and the upgrade kits are in **Tools & Utilities**. Search finds everything.

## Upgrade kits

Sneak and right-click (use) a container with a kit to convert it in place. The **iron kit** converts vanilla chests, trapped chests, barrels and shulker boxes (the block tag `fortified_storage:upgradeable/vanilla`; the copper chest is deliberately left out). Each later kit converts the tier before it: iron to gold, gold to diamond, diamond to netherite.

A conversion keeps items, facing, waterlogging, custom name, shulker colour and double-chest pairing. A double chest is converted as a pair and costs one kit per half. Kits are not used up in creative mode. Nothing happens while any player has the container open.

## Resource packs

Every fortified texture is built on each resource reload (including F3+T and pack changes) from:

1. the active pack's **vanilla texture** (for example `minecraft:entity/chest/normal`),
2. the active pack's **metal block** (`minecraft:block/iron_block`, `gold_block`, `diamond_block`, `netherite_block`),
3. a layout that says where the metal goes.

**Chests** are fully clad in metal: every outside face of the lid and body is the pack's metal block (its inside, tiled as on a placed block), with a darker band of the same metal along each face's edge so the lid and body stay distinct. The inside of the lid and the floor are shadowed metal. Only the latch comes from the pack's chest texture. **Barrels and shulker boxes** get a metal trim (rims, hoops and edges) over the pack's texture. That keeps the 16 shulker colours distinguishable.

Everything is laid out in vanilla texels and scaled to the pack's resolution, so 16x, 32x and 64x packs all line up. Tested with vanilla, Faithful 32x, Faithful 64x and a generated 64x pack.

### For pack authors: overriding

If a resource pack contains the exact file below, it is used as-is and nothing is generated for that texture. The mod itself ships **no** files at these paths, so any file found there comes from a pack.

`<tier>` is `iron`, `gold`, `diamond` or `netherite`. `<color>` is a vanilla dye name such as `red` or `light_blue`.

| Texture | Path in your pack | Layout matches vanilla |
|---|---|---|
| Chest | `assets/fortified_storage/textures/entity/chest/<tier>.png` | `entity/chest/normal.png` |
| Double chest halves | `…/entity/chest/<tier>_left.png`, `<tier>_right.png` | `normal_left.png`, `normal_right.png` |
| Trapped chest | `…/entity/chest/<tier>_trapped.png`, `_trapped_left`, `_trapped_right` | `trapped*.png` |
| Shulker box (undyed) | `assets/fortified_storage/textures/entity/shulker/<tier>.png` | `entity/shulker/shulker.png` |
| Shulker box (dyed) | `…/entity/shulker/<tier>_<color>.png` | `shulker_<color>.png` |
| Barrel | `assets/fortified_storage/textures/block/<tier>_barrel_side.png`, `_top`, `_top_open`, `_bottom` | `block/barrel_*.png` |
| Chest break particles | `…/block/<tier>_chest.png`, `<tier>_trapped_chest.png` | chest entity texture |
| Shulker break particles | `…/block/<tier>_shulker_box.png`, `<tier>_<color>_shulker_box.png` | `block/shulker_box.png` |
| Upgrade kit | `assets/fortified_storage/textures/item/<tier>_upgrade_kit.png` | any item texture |

You can also replace the trim masks to change the barrel, shulker and kit trim for all tiers at once (the chest cladding is built in code from the vanilla chest layout). They live in `assets/fortified_storage/textures/mask/` (`shulker`, `shulker_block`, `barrel_side`, `barrel_top`, `barrel_top_open`, `barrel_bottom`, `upgrade_kit`). They use the vanilla 16x / 64x UV layout. Alpha is trim coverage, and gray is brightness, where 200 means the metal at normal brightness.

### How it hooks in

The mod registers an atlas sprite source type, `fortified_storage:tiered`, and adds it to the vanilla `chests`, `shulker_boxes`, `blocks` and `items` atlases (`assets/minecraft/atlases/*.json`; atlas files from all packs are merged). This is the same system vanilla uses for armour trim palettes, so it needs no mixins.

## GUI

The container screen is drawn from vanilla pieces: the generic container panel (nine-sliced) and the `container/slot` sprite, so a pack's GUI style carries over.

The grid always has the container's real shape, in full rows: each tier is wider and taller than the last, from 9 × 6 for iron to 15 × 11 for netherite, and a double chest shows its two halves side by side (30 × 11 for netherite). A barrel or shulker box has exactly the same size and shape as the single chest of its tier. If the screen doesn't fit at your GUI scale, it lowers the GUI scale just enough while it is open and puts it back when you close it. For example, a double netherite chest at GUI scale 4 on a 1080p screen opens at scale 3. It only scrolls if it can't fit even at GUI scale 1. All vanilla slot behaviour still works: shift-click, double-click to collect, number keys, and the creative destroy slot.

## Config

`config/fortified_storage.json` (client):

```json
{ "scrollGui": true }
```

`scrollGui` only matters when the grid doesn't fit even at GUI scale 1. Set it to `false` to show every row anyway (the window can then run off screen).

## Compatibility

- Item transfer uses Fabric's Transfer API: `ItemStorage.SIDED` picks up all three block entities through its container fallback, which includes double chests. The shulker no-nesting rule applies there too.
- Blocks are in `c:chests`, `c:barrels`, `c:shulker_boxes`, `minecraft:guarded_by_piglins` and `minecraft:mineable/pickaxe`. Fortified shulker boxes are **not** in `minecraft:shulker_boxes`, so vanilla dye recipes can't turn a 165-slot box into a 27-slot one.
- The menu is a normal `AbstractContainerMenu` with plain slots at real grid positions, which is what inventory tweak and sorting mods expect.

### Tested with popular mods (Minecraft 26.3, November 2026)

An automated client test loads these alongside Fortified Storage, fills a netherite chest and a double diamond chest with a jumbled mix of items, and checks on the server that sorting groups them **without losing or duplicating anything**. It also sorts a double chest in a window so small that the grid has to scroll, to check that rows out of view get sorted too.

| Mod | Version | Result |
|---|---|---|
| ClientSort | 3.104.1 | Sorts single and double chests (pressing its Sort key). Its sort button shows next to the grid. |
| Inventory Profiles Next (+ libIPN) | 2.3.8 | Sorts single and double chests. Its buttons sit above the grid. |
| Mouse Tweaks | 2.31 | Scroll-wheel transfer moves items into the chest. |
| JEI | 31.18.0 | Item list sits beside the screen without overlapping, even for the 30-column double chest. |
| REI | 26.3.824 | Same as JEI. |
| Jade | 26.3.5 | Looking at a chest shows its name, contents and "Fortified Storage". |
| WTHIT | 21.0.0 | Same as Jade. |
| AppleSkin, Mod Menu | 3.0.10, 21.0.1 | Load and run alongside without problems. |

EMI has no build for 26.3 yet (its latest is for 1.21.1), so it couldn't be tested.

One detail: Inventory Profiles Next doesn't receive the test harness's simulated mouse clicks, even on a vanilla chest. So the test calls the same sort routine its Sort button runs, after checking that the routine sorts a vanilla chest. ClientSort is driven through its real keybind.

## Building and testing

Requires JDK 25.

```bash
./gradlew build                # jar in build/libs
./gradlew runDatagen           # regenerates src/main/generated
./gradlew runGameTest          # server game tests
./gradlew runClientGameTest    # client scenario + screenshots in build/run/clientGameTest/screenshots
```

To rerun the compatibility checks, put the other mods' jars (and their libraries) in a folder and pass it in. Test sorting mods in separate runs (ClientSort in one, Inventory Profiles Next in another), and recipe viewers likewise (JEI or REI):

```bash
./gradlew runClientGameTest -PcompatMods=/path/to/mods-folder
```

To also screenshot a real third-party pack (tested with Faithful 32x and 64x, `java-latest` branches, Nov 2026), unzip it somewhere and point the client test at the folder. The pack is not bundled.

```bash
FS_THIRD_PARTY_PACK=/path/to/Faithful-32x-Java ./gradlew runClientGameTest
```

To regenerate the masks:

```bash
java tools/GenMasks.java src/main/resources/assets/fortified_storage/textures/mask
```

Versions: Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Loom 1.19.4, Gradle 9.8.1.
