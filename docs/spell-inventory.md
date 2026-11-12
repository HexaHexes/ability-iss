# ISS spell inventory (REQ-CORE-12 effects slice)

Every spell class Iron's Spells registers (irons_spellbooks-1.21.1-3.16.3.jar,
113 classes under `spells/`), categorised by **activation type** (ISS's own
`CastType`, which our catalog maps to core's INSTANT/CHARGED/CHANNELLED) and by
**effect family** - a bytecode heuristic over each class's cast body
(damage/heal/uff/shield/summon/move/`aoe`, `utility` = none of
those matched). The school column is ISS's package (its own family layout).

The classification is **heuristic by design** (no effect-type API exists in ISS):
it exists to decide where the mastery effect axes matter, and every row is
reviewable against its class.

## Distributions

- Activation: LONG ×54, INSTANT ×49, CONTINUOUS ×10
- Effects: utility ×37, damage ×26, buff ×15, move ×8, summon ×7, damage+move ×4, heal ×4, damage+buff ×3, buff+move ×3, damage+buff+move ×3, damage+summon+aoe ×1, damage+buff+summon ×1, summon+move ×1

## Mastery axes per this inventory

| Axis | Applies to | Seam |
|---|---|---|
| COST | every spell (mana price) | discount at the check, the charge and the row quote |
| CAST_TIME | the 64 LONG/CONTINUOUS spells (INSTANT = 0 ticks, no-op) | getEffectiveCastTime return |
| POWER | every spell (damage/heal scaling reads getSpellPower) | getSpellPower return |
| DURATION | buff/summon families | **deferred** - no caster-scoped duration seam exists yet (filed) |
| RANGE | the 11 registered `getRange` seam spells (rays, beams, Gust, Telekinesis, Stomp, ...) | one return-wrap per class (mixin) + RayOfSiphoning's two call-site wraps - every read of that number moves together (`SoulfireRaySpell` ships a getRange but is unregistered, so it has no spec) |

The three universal axes are declared on every spell spec with core's default
curve (server config: mastery.costReductionPerLevel etc.); RANGE joins only
for `SpellMastery.RANGE_SEAM` - the exact mixin coverage, so the pane's bonus
is never a promise without a seam. Packs retune or restrict them per spell
through core's mastery_spec overlay.

## Inventory

| School | Spell | Cast | Effects |
|---|---|---|---|
| blood | AcupunctureSpell | INSTANT | utility |
| blood | BloodNeedlesSpell | INSTANT | damage |
| blood | BloodSlashSpell | INSTANT | damage |
| blood | BloodStepSpell | INSTANT | buff+move |
| blood | DevourSpell | INSTANT | damage |
| blood | HeartstopSpell | INSTANT | buff |
| blood | RaiseDeadSpell | LONG | summon |
| blood | RayOfSiphoningSpell | CONTINUOUS | damage |
| blood | SacrificeSpell | INSTANT | damage+summon+aoe |
| blood | WitherSkullSpell | INSTANT | utility |
| eldritch | AbyssalShroudSpell | INSTANT | buff |
| eldritch | EldritchBlastSpell | INSTANT | damage |
| eldritch | PlanarSightSpell | INSTANT | buff |
| eldritch | PocketDimensionSpell | LONG | damage |
| eldritch | SculkTentaclesSpell | LONG | utility |
| eldritch | SonicBoomSpell | LONG | damage |
| eldritch | TelekinesisSpell | CONTINUOUS | buff+move |
| ender | ArcaneShackleSpell | LONG | utility |
| ender | BlackHoleSpell | LONG | utility |
| ender | CounterspellSpell | INSTANT | summon |
| ender | DragonBreathSpell | CONTINUOUS | damage |
| ender | EchoingStrikesSpell | INSTANT | damage+buff |
| ender | EvasionSpell | INSTANT | buff |
| ender | GravityFissureSpell | LONG | move |
| ender | MagicArrowSpell | LONG | utility |
| ender | MagicMissileSpell | INSTANT | utility |
| ender | PortalSpell | INSTANT | utility |
| ender | RecallSpell | LONG | damage+move |
| ender | ShadowSlashSpell | INSTANT | damage+buff+move |
| ender | StarfallSpell | CONTINUOUS | damage |
| ender | SummonEnderChestSpell | INSTANT | summon |
| ender | SummonSwordsSpell | LONG | summon |
| ender | TeleportSpell | INSTANT | move |
| evocation | ArrowVolleySpell | LONG | damage |
| evocation | ChainCreeperSpell | LONG | summon+move |
| evocation | FangStrikeSpell | LONG | utility |
| evocation | FangSwirlSpell | LONG | utility |
| evocation | FangWardSpell | LONG | utility |
| evocation | FirecrackerSpell | INSTANT | utility |
| evocation | GustSpell | LONG | damage+move |
| evocation | InvisibilitySpell | LONG | buff |
| evocation | LobCreeperSpell | INSTANT | damage+move |
| evocation | ScapegoatSpell | INSTANT | utility |
| evocation | ShieldSpell | INSTANT | utility |
| evocation | SlowSpell | LONG | damage+buff+summon |
| evocation | SpectralHammerSpell | INSTANT | utility |
| evocation | SummonHorseSpell | LONG | summon |
| evocation | SummonVexSpell | LONG | summon |
| evocation | ThrowSpell | LONG | utility |
| evocation | WololoSpell | LONG | utility |
| fire | BlazeStormSpell | CONTINUOUS | damage |
| fire | BurningDashSpell | INSTANT | damage+buff+move |
| fire | FireArrowSpell | LONG | utility |
| fire | FireballSpell | LONG | utility |
| fire | FireboltSpell | INSTANT | damage |
| fire | FireBreathSpell | CONTINUOUS | damage |
| fire | FlamingBarrageSpell | INSTANT | utility |
| fire | FlamingStrikeSpell | LONG | damage |
| fire | HeatSurgeSpell | LONG | damage+buff |
| fire | MagmaBombSpell | LONG | move |
| fire | RaiseHellSpell | LONG | utility |
| fire | ScorchSpell | LONG | damage |
| fire | SoulfireRaySpell | LONG | damage |
| fire | WallOfFireSpell | INSTANT | damage |
| holy | AngelWingsSpell | INSTANT | buff |
| holy | BlessingOfLifeSpell | LONG | heal |
| holy | CleanseSpell | LONG | utility |
| holy | CloudOfRegenerationSpell | CONTINUOUS | heal |
| holy | DivineSmiteSpell | LONG | damage |
| holy | FortifySpell | LONG | buff |
| holy | GreaterHealSpell | LONG | heal |
| holy | GuidingBoltSpell | INSTANT | utility |
| holy | HasteSpell | LONG | buff |
| holy | HealingCircleSpell | LONG | utility |
| holy | HealSpell | INSTANT | heal |
| holy | SunbeamSpell | INSTANT | utility |
| holy | WispSpell | LONG | utility |
| ice | BlizzardSpell | LONG | move |
| ice | ConeOfColdSpell | CONTINUOUS | damage |
| ice | FrostbiteSpell | INSTANT | buff |
| ice | FrostStepSpell | INSTANT | move |
| ice | FrostwaveSpell | LONG | damage+buff |
| ice | IceBlockSpell | LONG | damage |
| ice | IceSpikesSpell | INSTANT | utility |
| ice | IceTombSpell | INSTANT | move |
| ice | IcicleSpell | INSTANT | damage |
| ice | RayOfFrostSpell | INSTANT | damage |
| ice | SnowballSpell | LONG | move |
| ice | SummonPolarBearSpell | LONG | summon |
| lightning | AscensionSpell | INSTANT | damage+buff+move |
| lightning | BallLightningSpell | INSTANT | utility |
| lightning | ChainLightningSpell | INSTANT | utility |
| lightning | ChargeSpell | INSTANT | buff |
| lightning | ElectrocuteSpell | CONTINUOUS | damage |
| lightning | LightningBoltSpell | INSTANT | damage |
| lightning | LightningLanceSpell | LONG | utility |
| lightning | ShockwaveSpell | LONG | damage |
| lightning | ThunderStepSpell | INSTANT | damage+move |
| lightning | ThunderstormSpell | LONG | buff |
| lightning | VoltStrikeSpell | INSTANT | buff+move |
| nature | AcidOrbSpell | LONG | move |
| nature | BlightSpell | LONG | buff |
| nature | EarthquakeSpell | LONG | utility |
| nature | FireflySwarmSpell | LONG | utility |
| nature | GluttonySpell | INSTANT | buff |
| nature | OakskinSpell | INSTANT | buff |
| nature | PoisonArrowSpell | LONG | utility |
| nature | PoisonBreathSpell | CONTINUOUS | damage |
| nature | PoisonSplashSpell | LONG | utility |
| nature | RootSpell | LONG | utility |
| nature | SpiderAspectSpell | INSTANT | buff |
| nature | StompSpell | LONG | utility |
| nature | TouchDigSpell | INSTANT | utility |

