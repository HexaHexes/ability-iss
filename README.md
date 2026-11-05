# Ability ISS

An Ability Hotbar provider for Iron's Spells 'n Spellbooks: wear a spellbook
in the Curios slot and cast its spells from a cycleable provider bar - the
same non-assignable content-bar model as Pseudore Curios' core bar.

> **Status**: Phase 0 - the ISS API investigation (issue #1) before any
> gameplay code; the design issue follows its findings.

## What it does (planned)

- Reads the spellbook worn in Curios and exposes its spells as the cells of
  a non-assignable Ability Hotbar bar: one cell per spell, the cell count
  taken from the equipped book's own capacity.
- Casting dispatches through Ability Core with the cell index as the
  activation's slot context, so the bar always names its target explicitly
  and stale cells refuse fail-closed.
- Per-spell cooldown and mana project into the bar's overlays (pending
  ability-hotbar's per-cell availability).

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft | - |
| Required | NeoForge | - |
| Required | Ability Hotbar | bar registration, content provider, seeded assignments |
| Required | Ability Core | activation request/result and availability |
| Required | Iron's Spells 'n Spellbooks | spellbook contents and spell casting |
| Required | Curios | locating the equipped spellbook |

## In-game config editing

Mods built on this template can be edited in-game with
[Configured](https://www.curseforge.com/minecraft/mc-mods/configured) - install
it like any other mod; it adds no code to this one. Open the mod list, select a
mod, press Config, and change values directly: Configured writes through
NeoForge's config system, so a reload applies the new values without restarting
the game.

This mod ships the config-screen background pre-wired in
`src/main/templates/META-INF/neoforge.mods.toml`
(`[modproperties.${mod_id}] configuredBackground`) - point it at any texture you
like. Note Configured only shows a menu for mods that actually register a
config: a mod with no registered settings has nothing to show.

## Credits

- Built on [NeoForge](https://neoforged.net/).

## License

GNU Lesser General Public License v2.1 - see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
