# JJS Movesets (NeoForge 1.21.1)

Jujutsu Shenanigans' **Honored One**, **Vessel**, and **Toji / Hired Killer** in Minecraft. Install on client and server.

## Setup
`/jjs honored`, `/jjs vessel`, or `/jjs toji` picks a class. Other commands: `/jjs clear`, `/jjs awaken` (fill the awakening meter), `/jjs resetcd`.

While you have a class: health, hunger, armor, XP and the hotbar are hidden and replaced by the JJS HUD.
Max health is 100 (JJS scale) so the wiki damage numbers apply 1:1. Hunger never drops, natural regen is turned off
(world game rule), fall damage is off, and you can't mine or use vanilla attacks.

## Controls
| Key | Action |
|---|---|
| Left click | M1 (4-hit string, 4th hit knocks back) |
| Q | Dash. W = forward, A/D = side, S or no key = back |
| Hold F | Block (drains a guard meter, guard break stuns you) |
| 1 2 3 4 | Moves |
| R | Special |
| G | Awaken (when the purple bar is full) |
| X | Domain (awakened Vessel) |

## Black Flash (Vessel)
Not random. Press **3** (Divergent Fist), then press **3 again while the white flash shows on screen**.
The window is `BF_START..BF_END` in `Moves.java` (ticks 6-11 after the first press, 0.3 s). Pressing too early or late
locks Black Flash out for that cast. Hit = 10 damage, 20 if it interrupts an enemy's move (not through a block).

## Moves
Numbers are from the JJS fandom wiki figures (100 HP scale).

**Honored One** - Lapse Blue 12.5 / 13s, Reversal Red 12.5 (6.25 blocked) / 20s, Rapid Punches 17.25 / 15s,
Twofold Kick 10 / 18s. Awakened (Six Eyes): Blue MAX 44 / 17s, Red MAX 30-7 / 10s, Hollow Purple 70 / 40s, Infinite Void / 120s.

**Vessel** - Cursed Strikes 17.5 / 14s, Crushing Blow 12 / 15s, Divergent Fist 10 / 18s (+ Black Flash), Manji Kick 8.5 / 20s counter,
Combat Instincts (R) cancels a windup. Awakened (King of Curses, heals 45, 24-stud M1 slashes): Cleave 40% HP / 12s, Dismantle 17.5 / 13s,
Open 30 / 40s, Rush 25 / 15s, Malevolent Shrine (X) 78 slashes x 2 / 120s.

## Not 1:1 (be aware)
- **Estimated, wikis don't publish them:** M1 damage and timing, dash cooldowns/speed, block meter size, guard-break length,
  awakening meter fill rates, awakening duration, Infinite Void stun length, what Honored One's "Limitless" special actually does (I used a short blink).
- **Not implemented:** Black Flash chain, World Cutting Slash, Honored One 0.2 domain, Fallen transformation, ragdoll/wall-splat,
  throwable pickup variant of Combat Instincts, air variants, finishers, "+20.9 on kill" on Blue MAX.
- Black Flash timing is server-side, so very high ping will feel off.
- Studs are converted at 1 stud = 0.28 blocks.


## Toji / Hired Killer

Toji has three cursed tools. **R** cycles Split Soul Katana → Inverted Spear of Heaven → Chain of a Thousand Miles. Flourish follow-ups: Flourish again = **The Wrath**, R = **The Greed**, F = **The Pride**. Transcend again = **Ascend**, Rupture again = **Paradise**, and Whiplash again = **Twirling Strike**. G currently matches JJS's unfinished Puppet of Carnage awakening: 50 HP heal and cooldown reset.

- Katana: Flourish / Debilitate
- Spear: Transcend / Rupture
- Chain: Lasso / Whiplash

The move timings and hitboxes are Minecraft implementations and still require live in-game tuning; this is not a claim of frame-perfect parity.


## Toji / Hired Killer

Three cursed tools rotate with **R**: Split Soul Katana → Inverted Spear of Heaven → Chain of a Thousand Miles. Hold **F** while pressing R to cycle backwards. The equipped tool changes the two move slots and M1 reach/string. Flourish follow-ups: repeat Flourish = **The Wrath**, R = **The Greed**, F = **The Pride**. Repeat Transcend = **Ascend**, repeat Rupture = **Paradise**, R during Rupture = **Reincarnation**, and repeat Whiplash = **Twirling Strike**. G currently matches JJS's unfinished Puppet of Carnage state: 50 HP heal and cooldown reset.

- Katana: Flourish / Debilitate
- Spear: Transcend / Rupture
- Chain: Lasso / Whiplash

Toji has custom weapon-themed HUD icons and blade/chain attack posing. Move timings, hitboxes, and damage are approximations that still require in-game tuning; they are not claimed to be frame-perfect parity.
