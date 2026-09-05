# Task 0 platform migration record

This supersedes the audit's deferred Fabric decision: the accepted Task 0 scope is now pure Forge 1.20.1, Java 17. No worldgen algorithm redesign is authorized.

## Source family proof

Searches covered all Java files in common/forge/fabric, fully qualified and wildcard imports, resource/manifests/datapacks, registry bootstrap, GUI/export, mixins and datagen entry points. No consumer outside the removed family referenced its qualified classes. No resource selected those Java classes. Resource IDs are not being deleted.

The active chain is `RTFForge -> RTFCommon -> RTFRegistries.PRESET (preset.settings.Preset)`, and `PresetConfigScreen -> Datapacks.makePreset -> settings.Preset.buildPatch -> preset.Preset*` generators. `MixinRandomState` and `MixinNoiseChunk` use the same settings type. The older family contains 19 top-level data/worldgen classes and 12 settings classes under data/worldgen/preset; all 31 were inactive. No active consumer needed migration.

All six original errors are classified **duplicate/inactive source removal**:

| Old file and lines | Error | Resolution |
| --- | --- | --- |
| `data/worldgen/BiomeModifierData.java:59,69` | Two stale `BiomeModifiers.add` overloads missing filter behavior | Remove inactive generator; retain active `PresetBiomeModifierData` with explicit filter behavior |
| `data/worldgen/preset/Preset.java:39,47` | Two incompatible uses of old `Preset` with registry of `settings.Preset` | Remove old type; preserve registered settings type and codec |
| `data/worldgen/RTFConfiguredFeatures.java:68,72` | Obsolete zero-argument erosion and two-argument snow configs | Remove inactive generator; active `PresetConfiguredFeatures` already supplies current configs |

Pre-conversion gate: `-PminimalRuntime=true :common:compileJava :forge:compileJava :forge:build` reported BUILD SUCCESSFUL. Its client launch exposed a pre-existing development refmap error (`MixinUtil` resolving Fabric intermediary `net/minecraft/class_156`). Build success alone was not treated as runtime success.

## Platform migration map (before moves)

| Existing item | Classification | Destination/semantics |
| --- | --- | --- |
| common Java/resources | MOVE INTO MAIN SOURCE | Root src/main, preserve packages and asset bytes |
| Forge entrypoints/client preset editor/server template manager/reload hooks | MOVE INTO MAIN SOURCE | Same Forge event/constructor hooks |
| ConfigUtil + Forge ConfigUtilImpl | INLINE FORGE IMPLEMENTATION | ConfigUtil directly uses FMLPaths.CONFIGDIR |
| ModLoaderUtil + Forge ModLoaderUtilImpl | INLINE FORGE IMPLEMENTATION | Preserve early FMLLoader loading-list check for optional TB mixin gating |
| DataGenUtil + Forge DataGenUtilImpl | INLINE FORGE IMPLEMENTATION | Minecraft RegistriesDatapackGenerator; no loader dispatch |
| RegistryUtil + Forge RegistryUtilImpl | INLINE FORGE IMPLEMENTATION | Existing DeferredRegister / DataPackRegistryEvent behavior in RegistryUtil |
| DeferredRegistry | KEEP AS INTERNAL NON-PLATFORM ABSTRACTION | Preserve delayed registry ownership; move alongside RegistryUtil |
| RegistryUtil.getBiomeModifierRegistry | REMOVE AS FABRIC ONLY | No call sites and no Forge implementation |
| BiomeModifiers + Forge BiomeModifiersImpl | INLINE FORGE IMPLEMENTATION | Existing Forge AddModifier/ReplaceModifier construction and codecs |
| BiomeModifier, Filter, Order, ForgeBiomeModifier | KEEP AS INTERNAL NON-PLATFORM ABSTRACTION | Preserve feature policy/codecs; no tree-policy changes |
| ExpectPlatform | REMOVE AS FABRIC ONLY | Remove all five dispatch owners' annotations/imports after inlining |
| Fabric entrypoint/platform/modifiers/mixins/metadata/datagen | REMOVE AS FABRIC ONLY | No Forge consumer relies on them |
| Network abstractions | None found | No networking migration needed |
| Architectury plugin/Loom/transformed source sets/shadow wiring | REPLACE WITH FORGE API | Single-project ForgeGradle, official 1.20.1 mappings, reobfJar |
| Common + Forge mixin manifests | MOVE INTO MAIN SOURCE | Preserve two non-overlapping manifests and original target packages, one Forge refmap; no need to rename injection classes |
| Optional TB mixins | KEEP AS INTERNAL NON-PLATFORM ABSTRACTION | Preserve ModLoaderUtil gating; no selection changes |
| 65 access-widener entries | REPLACE WITH FORGE API | Carry forward Loom's generated SRG access transformer, one-to-one, then verify compile/runtime and artifact inclusion |
| User-added optional development mods | MOVE INTO MAIN SOURCE | Preserve artifact coordinates behind development runtime switch; standalone run excludes them |

Access audit policy: retain every existing access initially, including compatibility-only members, to avoid removing required access during a loader conversion. The generated AT is the exact Forge equivalent already shipped by the repaired Loom baseline, not guessed Mojang-to-SRG names. The baseline record will contain the entry inventory and verification result.

Native build/access conventions were checked against the [Forge 1.20.1 MDK](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/mdk/build.gradle) and [Forge access-transformer documentation](https://docs.minecraftforge.net/en/1.20.x/advanced/accesstransformers/).
