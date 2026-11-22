# Data format

The app keeps all its data in one JSON document. The export writes this document to a file. The import reads a file in this format and replaces the data in the app.

## Top level

```json
{
  "version": 1,
  "exportedAt": "2026-11-10T18:00:00Z",
  "settings": { "bodyweightKg": 70, "sex": "male", "birthYear": 2000, "warmups": [ <Warmup> ] },
  "exercises": [ <Exercise> ],
  "sessions": [ <Session> ],
  "runs": [ <Run> ]
}
```

## Settings

| Field | Values |
|---|---|
| `bodyweightKg` | Bodyweight in kg, or `null`. |
| `sex` | `male`, `female` or `null`. |
| `birthYear` | Year of birth, or `null`. The app stores the year and not the age, because the age changes. |
| `warmups` | Warmup templates. |

The app asks for `sex`, `bodyweightKg` and `birthYear` in Settings, and on the Today screen while the data has no sessions and no runs.

## Warmup

```json
{ "id": "circuit_1", "name": "Circuit 1", "blocks": [ <Block> ] }
```

A warmup is a named template. A session refers to it by `id` and does not copy its blocks. The import also reads the older `settings.warmup` array of blocks as one template with id `circuit_1` and name `Circuit 1`.

## Exercise

```json
{
  "id": "chinup",
  "name": "Chin-up",
  "category": "vertical_pull",
  "role": "main",
  "level": { "kind": "ss", "lift": "weighted_chinup" },
  "notes": "Supinated grip"
}
```

| Field | Values |
|---|---|
| `id` | Lower snake case, unique, never changes. |
| `category` | `squat`, `hinge`, `horizontal_push`, `horizontal_pull`, `vertical_push`, `vertical_pull`, `core`, `other` |
| `role` | `main`, `skill`, `accessory`, `warmup` |
| `level` | `null`, `{ "kind": "ss", "lift": <SsLift> }` or `{ "kind": "og", "value": <number> }` |
| `topReps` | Optional. Top of the rep range for the progression rule. Default 7. |

### Exercise catalog

The optional file `app/src/main/assets/exercises.json` holds a default exercise list in the same top-level format, with `version`, `exercises` and `ogLevelSource`. `ogLevelSource` is the citation for the `og` values in `level.value`, and the app does not read it. When the app loads its data or imports a file, it adds each catalog exercise whose `id` is not in the data yet. An exercise in the data always wins over a catalog exercise with the same `id`. The next save writes the added exercises to the data, so a later change to a catalog exercise does not reach an existing install.

## Progression rule

The app reads the previous item of the same exercise:

1. If 7 days or more passed since that item, the app tells you so and suggests repeating the same values.
2. If `lastReps` is set, the next target is `lastReps − 1` for all sets. If that target is above `topReps`, the app suggests more load or the next progression.
3. If `lastReps` is lower than `reps`, the app suggests repeating the same values.

`core` and `other` (mobility, prehab, conditioning) have no group chart. Accessories and warmup items never give a level.

## Session

```json
{
  "id": "2026-11-10-a",
  "date": "2026-11-10",
  "source": "excel",
  "notes": "sick",
  "warmup": "circuit_1",
  "blocks": [ <Block> ]
}
```

`source` is `excel`, `whatsapp` or `app`. `warmup` is the id of a warmup template, or `null`. A block holds the exercises that alternate (paired sets). A block with one item is a single exercise.

```json
{ "items": [ <Item>, <Item> ], "restSec": 120 }
```

## Item

```json
{
  "exerciseId": "chinup",
  "sets": 3,
  "reps": 5,
  "seconds": null,
  "loadKg": 15,
  "lastReps": 7,
  "lastSeconds": null,
  "dropReps": null,
  "extra": [ { "reps": 3, "seconds": null, "loadKg": 100 } ],
  "perSide": false,
  "setup": "strap 2",
  "toFailure": false,
  "note": null,
  "raw": "15x5 (7)",
  "parsed": true
}
```

| Field | Meaning |
|---|---|
| `sets` | Number of sets. `null` when unknown. |
| `reps` / `seconds` | Reps of each set, or hold time of each set. One of the two, or both for reps with a hold (`3x3s` = 3 reps of 3 s). |
| `loadKg` | Barbell or dumbbell weight, or added load on bodyweight exercises. `null` = no load. Negative = assistance in kg. |
| `lastReps` / `lastSeconds` | The absolute value of the last set when it differs from the others (`5 (7)` → `reps 5, lastReps 7`). |
| `dropReps` | Extra reps without load right after the set (`20x5+5` → `loadKg 20, reps 5, dropReps 5`). |
| `extra` | Further sets with a different load or reps, such as a top set (`80x5 (100x3)`) or a ramp (`70x5, 90x5, 100x3`). |
| `perSide` | `true` when reps count for each side (`5+5`, `sx/dx`). |
| `setup` | Free text for a progression setting that has no global scale: ring strap number, band colour, elevation in cm, PPPU distance in mm. Compare it only with the previous session of the same plan. |
| `toFailure` | `true` when the set ends at failure without a number (band and ring facepulls). |
| `raw` | The original text, for imported items. `null` for items logged in the app. |
| `parsed` | `false` when the import cannot read the numbers. The app shows `raw` and leaves the item out of charts, levels and suggestions. |

## Run

```json
{
  "id": "run-1767258000",
  "startedAt": "2026-01-01T10:00:00+01:00",
  "durationSec": 1800,
  "distanceM": 5000,
  "avgHr": 140,
  "maxHr": 165,
  "type": "intervals",
  "title": "3x1km",
  "kms": [ { "kind": "normal", "sec": 360 }, { "kind": "fast", "sec": 300 } ],
  "elevationGainM": null,
  "notes": null,
  "source": "watch",
  "track": "_p~iF~ps|U_ulLnnqC"
}
```

| Field | Values |
|---|---|
| `type` | `easy`, `long`, `intervals`, `hills`, `race`, `recovery`, `other` |
| `kms` | One entry per km, in order. `kind` is `normal`, `fast`, `recovery`, `up` or `down`. `sec` is optional. Empty array when unknown. |
| `source` | `watch`, `excel`, `watch+excel`, `app` |
| `track` | Optional. GPS route as an encoded polyline (Google algorithm, precision 5). The run view draws it on a map and colours each km by its `kind`. Map tiles come from OpenStreetMap and are cached on the device. |

## Levels

The app shows one level number for each of the six groups. The number merges two scales:

- **Symmetric Strength (`ss`)** for loaded lifts. The thresholds depend on sex, bodyweight and age.
- **Overcoming Gravity 2 (`og`)** for bodyweight progressions. The value is the level of the progression in the exercise progression charts of Steven Low, *Overcoming Gravity*, 2nd edition (2016), on a scale of 1–16. `exercises.json` holds these values in `level.value` and the citation in `ogLevelSource`.

### SS thresholds

The thresholds come from `app/src/main/assets/levels.json`. They were collected from Symmetric Strength (https://symmetricstrength.com/standards) on 2026-11-22.

```json
{
  "source": "https://symmetricstrength.com/standards",
  "retrievedAt": "2026-11-22",
  "levels": ["Untrained", "Novice", "Intermediate", "Proficient", "Advanced", "Exceptional", "Elite", "World class"],
  "bodyweightsKg": [40, 50, ..., 150],
  "ages": [14, 16, 18, 20, 23, 25, 30, ..., 90],
  "lifts": { "<SsLift>": { "male": [ [ <cell> per age ] per bodyweight ], "female": [ ... ] } }
}
```

- The grid is sex × 12 bodyweights from 40 to 150 kg (step 10) × 19 ages from 14 to 90.
- `lifts[lift][sex][bodyweight index][age index]` is a cell. A cell is an array of 8 increasing 1RM thresholds in kg, index 1–8 = the names in `levels`, or `null`.
- Where the site shows an empty value, the file holds the linear interpolation between the neighbouring levels.
- Where the site shows fewer than two values for a row, the cell is `null` and gives no level.
- For `weighted_dip`, `weighted_chinup` and `weighted_pullup` the thresholds are the added load. They can be negative (assistance).

To get the thresholds for one item, the app:

1. Calculates the age as the year of the session date − `birthYear`.
2. Clamps bodyweight and age to the grid range.
3. Interpolates bilinearly between the cells around bodyweight and age. If one of these cells is `null`, the item has no `ss` level.

An item has no `ss` level when `sex`, `bodyweightKg` or `birthYear` is missing, or when `levels.json` is missing.

### Computation

1. **Estimated 1RM.** Use Epley: `w × (1 + reps / 30)`. Use only sets with 1–12 reps. For added-load lifts, `w = bodyweightKg + loadKg`, and the result is converted back to added load: `1RM − bodyweightKg`.
2. **SS index.** Interpolate the 1RM linearly between the thresholds. Below threshold 1, `index = 1RM / threshold1`, or `index = 1` when threshold 1 is 0 or negative. Above threshold 8, extrapolate with the last interval.
3. **Merged level.** `level = 1.5 × index + 1.5`. The mapping matches the OG2 bodyweight ratios for weighted pull-ups, dips and press: SS Novice ≈ OG 4.5, SS Intermediate ≈ OG 6, SS Proficient ≈ OG 7.5.
4. **OG items.** The level is the `value` of the exercise when the item has at least one complete set.
5. **Item level.** It is the best level over the main set, `lastReps` and each `extra` set. Items with `parsed: false` give no level.
6. **Group level on a date.** It is the highest item level of the group in the 28 days before that date. With no item in the window, the group has no value on that date and the chart shows a gap.
