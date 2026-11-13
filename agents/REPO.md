# Repo rules - ability-iss

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/ability-iss` - **Mod ID:** `ability_iss` - **Branch:** `main`

An Ability Hotbar provider for Iron's Spells 'n Spellbooks: reads the spellbook
worn in Curios, renders its spells as the cells of a non-assignable provider
bar (cell count from the book's own capacity, clamped to ContentPlan's
`[1, 18]`), and casts through Ability Core with the cell index as the
activation's slot context. Sibling of pseudocore-curios' curios bar - same
model, different content source. Also registers the menu's **Spells tab**
(owner's tab brief): one ability per ISS spell (the ability-tensura
registration-timing pattern - raw write at ISS's own spell-registry event),
listed once ISS remembers the spell for the player (`AbstractSpell.isLearned`,
its own side-aware read), greyed unless the spell sits in the inventories
right now (main/armor/offhand, curios, any carried spell container - books and
scrolls both), plus the **school masteries** (both categories accepted, rows
file under their school's `type:` chip), a `type:<school>` strip ordered from
ISS's school registry, **informational for casting rows** (spells never slot;
NONE mastery rows keep their toggle and cycle but never slot either - hotbar's
assignment gate - and hide while still locked) through hotbar's tab API.

**Dependencies**
- Required: Minecraft, NeoForge, Ability Hotbar, Ability Core, Iron's Spells
  'n Spellbooks, Curios - all six are hard in the toml.
- Optional: **Tensura: Reincarnated** (first soft dependency) - the HUD
  swap's subject: with both ability mods loaded, ISS's overlays replace
  Tensura's ability charging row (`IssClientEvents`, dist-scoped,
  `isLoaded`-gated, toggle in `ability_iss-client.toml` /
  `hud.replaceTensuraCharging`); absence no-ops. Hotbar's floor is
  **alpha.13** (the `slotTooltip` hook this provider's pill hover
  overrides, plus the spell-tab API this mod consumes and the NONE
  informational-gate exemption the mastery rows' toggle and cycle ride).
- Every optional-feeling seam still follows the `isLoaded()` gate rule from
  AGENTS.md for future soft dependencies.

**Test:** `./gradlew runGameTestServer` - `IssHotbarGameTests` (16 tests).
`expectedTests = 16` in `build.gradle` must match the `@GameTest` count.

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
