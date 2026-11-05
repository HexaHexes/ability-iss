# Repo rules - ability-iss

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/ability-iss` - **Mod ID:** `ability_iss` - **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 - **Jar:** `build/libs/ability_iss-<version>.jar`

An Ability Hotbar provider for Iron's Spells 'n Spellbooks: reads the spellbook
worn in Curios, renders its spells as the cells of a non-assignable provider
bar (cell count from the book's own capacity, clamped to ContentPlan's
`[1, 18]`), and casts through Ability Core with the cell index as the
activation's slot context. Sibling of pseudocore-curios' curios bar - same
model, different content source.

**Dependencies**
- Required: Minecraft, NeoForge, Ability Hotbar, Ability Core, Iron's Spells
  'n Spellbooks, Curios - all six are hard in the toml.
- Every optional-feeling seam still follows the `isLoaded()` gate rule from
  AGENTS.md for future soft dependencies; today nothing is optional.

**Test:** `./gradlew runGameTestServer` - `IssHotbarGameTests` (1 test).
`expectedTests = 1` in `build.gradle` must match the `@GameTest` count.

**Gotchas**
- `slotCount` must satisfy ContentPlan's `[1, 18]` on every call - book
  capacity clamps into that range, and no-book/zero-spells must go through
  `hasContent()` (bar hidden), never an out-of-range `slotCount`.
- Seeded cells are the activation path: a cell without a seeded ability is
  dead, so any book-content change must re-seed (see ability-hotbar#114's
  `seedAll` once it lands).
- Client-array mixins load nowhere in CI (gametests run a dedicated server):
  never `@Shadow` inherited Minecraft members, and launch `runClient` once
  before a release - full rules in `docs/mixin-rules.md`.
- Version bumps ride CI/CD post-merge (never bundle `gradle.properties`
  with other changes in a PR).
