# Ability ISS

A provider addon for [Ability Hotbar](https://github.com/HexaHexes/ability-hotbar)
that gives [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)
a second hotbar: wear a spellbook in the Curios `spellbook` slot and its
spells appear as a cycleable, non-assignable bar - spell icons, per-spell
cooldowns and mana - cast through ISS's own pipeline, so the bar and ISS's
own wheel, keybinds and UI coexist instead of competing. Built for the
Tensura RPG pack.

## What it does

- **The bar** (`ability_iss:spellbook`): one cell per slot of the equipped
  book, the cell count taken from the book's own capacity and clamped to
  `[1, 18]`. The bar never appears in the assignment menu, the wheel cycles
  to it like any other bar, and one selection frame marks the cell a press
  acts on. Without a worn book the bar counts as empty and the wheel skips
  it.
- **Cells draw the spells themselves**: each cell shows the spell's own
  icon texture (spells have no item form), plus a per-spell cooldown sweep
  and a dim for a locked spell or mana below its cost - all read from
  ISS's own client-synced state, so the bar can never disagree with ISS's
  HUD. No cost badge is drawn over spell icons (the badge is an
  unannotated strip; REQ-HOT-08 prefers no overlay to noise).
- **Casting** dispatches through Ability Core with the cell index as the
  activation's slot context, then delegates to ISS's own quick-cast: mana,
  cooldown, cast-in-progress and every refusal message stay ISS's pipeline,
  and the bar only carries the outcome (hotbar fires intents only). Slot
  dispatch runs fail-closed through Ability Core's slot gate - a stale
  cell, a missing context or an empty book refuses instead of guessing.
- **Costs quote live** (`LiveCosts`): rows and the pane re-read the price
  at display time, and with `tensura_iron_spells` installed that price is
  the spell's **magicule** charge - its conversion config loads after
  every RegisterEvent, so a baked quote could only ever carry native mana.
- **Mastery** (REQ-CORE-12): every successful cast - bar, native keybind
  or wheel, they share one initiation funnel - grants the spell its
  configured XP (`mastery.spellXpGain`, server config, read live at
  award time) and routes `mastery.schoolShare` (default 20%) to its
  school's mastery ability, through core's opt-in declarations. Packs
  retune the share or any other spec field through core's
  `mastery_spec` datapack overlay. **The school masteries** are passive
  rows (NONE activation - the pane never mentions activation) listed on
  the Spells tab under their school's chip: casting any spell of a
  school **permanently unlocks** that school's mastery - the row's
  grey, the assignment gate and the slot reconciler all read the same
  verdict - and its pane carries the four-axis bonus curve at the
  school's own level plus a flourish naming the school and how it
  feeds back. **Mastery improves the spells**: every spell declares
  COST, CAST_TIME and POWER on core's default curve - the discount
  bites at the affordability bar, the posted cast charge and the row
  quote (rounded identically, so they can never disagree), the cast
  time at `getEffectiveCastTime`, the power at `getSpellPower`.
  `docs/spell-inventory.md` categorises all 113 spells by activation
  and effect family (issue #28 closed by these seams; DURATION stays
  undeclared - no caster-scoped seam exists yet).
- **The bar tracks the book**: content re-seeds on login, whenever the
  book's spells change (inscription), and whenever the spellbook is
  equipped or unequipped - per slot, so a removed spell clears its own cell
  and never a prefix of them.
- **Coexistence is deliberate**: ISS's spell wheel, cast keybinds and
  screens are untouched - the bar is additive. If this bar should be the
  only spell display, turn ISS's own spell bar off in its client config
  (`config/irons_spellbooks-client.toml`, `[UI.SpellBar]`,
  `spellBarDisplay = "Never"`).
- Books larger than nine slots render as nine-slot segments with one
  selector, like every other bar (hotbar#86).

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft | - |
| Required | NeoForge | - |
| Required | Curios | The `spellbook` slot the book wears in |
| Required | Iron's Spells 'n Spellbooks | Spellbook contents (`ISpellContainer`) and the cast itself (`serverSideInitiateQuickCast`) - the addon delegates, it never reimplements |
| Required | Ability Hotbar | The bar: registration, the content-provider cells, seeded verbs, and the icon/overlay hooks the cells draw through |
| Required | Ability Core | The activation seam: slot gate, request/result vocabulary, availability tuple |

Ability Core and Ability Hotbar resolve from this org's GitHub Packages
maven (they are not published to CurseForge or Modrinth), so CI needs the
workflow token (already wired in `ci.yml`) and a local build needs
`gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`.

## Credits

- Built on [NeoForge](https://neoforged.net/).
- Derived from [neoforge-mod-template](https://github.com/HexaHexes/neoforge-mod-template).

## License

GNU Lesser General Public License v2.1 - see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
