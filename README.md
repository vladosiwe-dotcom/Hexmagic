# Hex Magic — Hex Staff

Minecraft 1.21.1 / NeoForge 21.1.x. A staff that raises honeycomb barriers and bends them into weapons:
shield walls, a spinning drill, spiked walls, launch platforms, projectile reflection, homing mini-drills and a closing prison.

Optional companion: Iron's Spells 'n Spellbooks (the staff becomes one of its staves and reflects its spell projectiles).

## Build

```
./gradlew build            # jar in build/libs
./gradlew runGameTestServer # server-side game tests
./gradlew runClient
```

A local `libs/irons_spellbooks-*.jar` is used for compilation when present; otherwise the jar is fetched from Iron's maven.
