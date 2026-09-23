# native-recorder

**Plan only** — no Android app implementation lives in this repository yet.

A research-backed plan for a **minimal native Android recorder** with:

- On-device storage only
- Microphone + device/internal audio capture (where the platform allows)
- Home-screen widget that starts device-audio recording
- **Google Material 3 / Material You** light **and** dark themes
- **Standard Material components only** (no custom or third-party UI kits)

## Material 3 constraint (non-negotiable)

UI must look super native to Android:

- Theme via **Material 3** (`androidx.compose.material3`) with **dynamic color** (Material You) on Android 12+, plus static light/dark fallbacks.
- Use stock M3 components: `Scaffold`, `TopAppBar`, `FloatingActionButton`, `ListItem`, `FilledIconButton`, `AlertDialog`, `Snackbar`, `Card`, `FilterChip`, etc.
- Widget UI via **Jetpack Glance** + `glance-material3`.
- **Do not** use Compose Destinations UI skins, custom design systems, or third-party component libraries for UI.

Full plan: [`docs/PLAN.md`](docs/PLAN.md)

## Status

| Item | State |
|------|--------|
| UX research | Done (cited in plan) |
| Architecture / permissions plan | Done |
| App source | **Not started** (by design) |

## Repo

Personal plan repo for [shashankanil](https://github.com/shashankanil).

Description: Plan for a minimal Material 3 native Android recorder (on-device, mic + device audio, Glance widget).
