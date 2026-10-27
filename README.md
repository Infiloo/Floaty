# Floaty

A client-side Fabric mod for **Minecraft 26.2** that makes your character float and spread
their arms while **AFK Cinematics** is playing a cinematic.

As soon as a cinematic starts, the local player rises smoothly into the air, the arms rise with
them, and at roughly one block above the ground the arms form a straight horizontal line. The
character keeps hovering with a gentle up/down bob until the cinematic stops, then lowers back
down.

Floaty is **client-side only** — nothing is sent to the server and the real player position,
hitbox, and movement are untouched.

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.20.1 or newer |
| Fabric API | any version for 26.2 |
| Java | 25 |
| [AFK Cinematics](https://modrinth.com/mod/afk-cinematics) | optional (1.0.5+26.2 works) |

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and (optionally) AFK Cinematics into `mods/`.
3. Put `floaty-1.0.0.jar` into `mods/`.
4. Start the game. Floaty prints a line in the log telling you whether the AFK Cinematics link
   is active.

## How the AFK Cinematics link works

AFK Cinematics is All-Rights-Reserved and does not expose an API. Floaty therefore reads its
live "cinematic playing" flag reflectively. If the link ever breaks after an update, Floaty
falls back to its own idle detector (default: 25 seconds without input) so the animation still
works. You can turn that fallback off with `/floaty set fallback false`, or require the real
mod with `/floaty set requiremod true`.

## Commands

All settings are changed in-game and saved immediately to `config/floaty.json`.

```
/floaty                       show help and the current values
/floaty help                  same as above
/floaty status                what is happening right now (trigger, progress, height)
/floaty on | off | toggle     master switch
/floaty preview [seconds]     play the animation for testing (default 8, max 120)
/floaty stop                  cancel a preview
/floaty set <option> <value>  change and save a setting
/floaty get <option>          show one setting
/floaty reset [option]        reset everything, or one option
/floaty save | reload         manually write / re-read the config file
```

Tab completion lists every option. Examples:

```
/floaty set height 1.5
/floaty set armangle 75
/floaty set bob 0.12
/floaty set bobperiod 2.5
/floaty set rise 1
/floaty set easing sine
/floaty set armforward 20
/floaty preview 30
```

## Options

| Option | Default | Range / values | Description |
| --- | --- | --- | --- |
| `enabled` | `true` | true/false | Master switch |
| `height` | `1.0` | 0 – 8 | Float height in blocks |
| `rise` | `2.5` | 0.01 – 30 | Seconds to rise |
| `fall` | `1.6` | 0.01 – 30 | Seconds to lower |
| `easing` | `smoothstep` | `smoothstep`, `sine`, `cubic`, `linear` | Motion curve |
| `armangle` | `90` | 0 – 180 | Arm spread; 90 = straight out to the sides |
| `armforward` | `0` | −180 – 180 | Swing both arms forward/back |
| `armtwist` | `0` | −180 – 180 | Twist the arms |
| `syncarms` | `true` | true/false | Raise/lower arms together with the float |
| `armrise` | `2.5` | 0.01 – 30 | Arm raise time when `syncarms` is false |
| `armfall` | `1.6` | 0.01 – 30 | Arm lower time when `syncarms` is false |
| `bob` | `0.07` | 0 – 0.5 | Up/down bobbing amplitude in blocks |
| `bobperiod` | `3.2` | 0.2 – 20 | Seconds per bob cycle |
| `bobafterrise` | `true` | true/false | Only bob once the rise has finished |
| `bobfade` | `0.8` | 0.05 – 10 | Seconds for the bob to fade in |
| `ceiling` | `true` | true/false | Stop below ceilings instead of clipping into them |
| `fallback` | `true` | true/false | Use the idle detector when AFK Cinematics is unavailable |
| `idle` | `25` | 1 – 3600 | Idle seconds before the fallback starts |
| `requiremod` | `false` | true/false | Only animate while AFK Cinematics is really active |

`/floaty reset` restores all of these defaults.

## Notes

- The shadow stays on the ground; only the rendered body is lifted, because entity shadows are
  computed separately from the model.
- Armor, sleeves, capes, and held items follow the arms, since the pose is applied to the
  humanoid model and every humanoid render layer.
- Spectators, sleeping players, passengers, and dead players are never animated.
- The motion is driven by the render-state tick counter including the partial tick, so it
  stays smooth even at low frame rates.

## Building

```bash
./gradlew build --no-daemon
```

The remapped jar is written to `build/libs/floaty-1.0.0.jar`.

## License

MIT. AFK Cinematics is a separate All-Rights-Reserved mod by spunkyinsaan and is **not**
bundled with Floaty.
