# Hex Magic — Hex Staff

Minecraft 1.21.1 / NeoForge 21.1.x / Java 21.

A staff inspired by the honeycomb barriers of *Frieren*: every ability is built from the same glowing
hexagonal cells, and the barriers themselves become the weapons.

| # | Ability | Type | What it does |
|---|---------|------|--------------|
| 1 | Hexa Shield | active | Vertical wall of cells in front of you, fixed in place. 20 HP, 3 charges, one charge back every 7 s. |
| 2 | Hexa Drill | active (hold) | Cells gather above your head into a spinning cone; the longer you hold, the bigger and faster. Release to fire: bores through blocks, grinds into enemies (damage every 0.5 s), pushes bystanders aside with its air stream. 0.5/1/1.5/2/3 s → 4/6/8/10/12 damage. 5 s cooldown. |
| 3 | Hexa Spike | active | Spikes grow out of a wall (an existing one under the crosshair, or a fresh one), then the whole wall flies at your aim. Damage is provisional (config). |
| 4 | Hexa Launch | passive | A 4×4 platform forms under you and lifts you ~15 blocks (1 charge, 20 s lifetime, 20 HP). Casting is faster while standing on it. A cushion of cells catches you when you fall. |
| 5 | Hexa Reflection | passive | Arrows and Iron's spell projectiles bounce back at their caster at 2/3 damage. 15 s cooldown, no charge cost, a projectile can only be reflected once. |
| 6 | Hex Attack | passive | Small, already spinning drills circle above you. After 35 s without casting they lock on and fire one by one at the nearest hostile. Press the key / right click to volley at your aim. |
| 7 | Hex Prison | ultimate | Two walls snap shut beside you, then a roofless 4×3×4 room of cells closes around the target. On the 7th second the walls grind inward; at 10 s they meet for 10 pure damage. Your own drill opens a passage through the walls and closes it behind. |

If a shield or platform is destroyed (20 HP gone), the passive abilities switch off for 30 seconds.

## Controls

All keys are rebindable in *Options → Controls → Hex Staff*.

| Key | Action |
|-----|--------|
| Right mouse | Cast the selected ability (hold for Hexa Drill) |
| R | Select next ability for the right mouse button |
| G | Hexa Shield |
| V (hold) | Hexa Drill |
| B | Hexa Spike |
| C | Hexa Launch |
| X | Hex Attack |
| Z | Hex Prison |

With Iron's Spells 'n Spellbooks installed the selection list also has a *Spellbook* slot that hands the
right mouse button back to Iron's spell casting; the staff then behaves as one of its staves (spell power,
cooldown reduction and mana regen bonuses).

## Build & test

```
./gradlew build               # jar in build/libs
./gradlew runGameTestServer   # 9 server-side game tests
./gradlew runClient           # dev client
./gradlew runClient -PhexDemo=full   # scripted showcase that screenshots every ability
```

Balance values live in `config/hexmagic-server.toml`; everything the design fixed is the default, values
marked PROVISIONAL are open for tuning. A local `libs/irons_spellbooks-*.jar` is used for compilation when
present; otherwise it is fetched from Iron's maven.
