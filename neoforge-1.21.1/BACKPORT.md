# Automagy for Minecraft 1.21.1

Same mod id (automagy), version (0.8.5) and features as the 26.1.2 build at the repo root. NeoForge 21.1.259,
Thaumaturge 1.21.1 1.1.0, curios 9.5.1, lithostitched 1.8.1. JEI 19.64 support kept.

Nothing is cut. What works differently underneath:
- Runic list whitelist/blacklist swaps use our own `automagia:list_transmute` recipe (1.21.1 has no
  `minecraft:crafting_transmute`).
- Item models are plain `models/item` files (no `items/` definitions); the three chests draw through their block entity
  renderer as vanilla chests do.
- Mirror links are checked with Thaumaturge 1.21.1's `linked` / `isLinkValid()`.
- Block removal side effects run from a small mixin on `onRemove` (1.21.1 has no `preRemoveSideEffects`).

Thaumaturge jar: put `thaumaturge-1.21.1-NeoForge-1.1.0.jar` in `libs/` (not committed).
