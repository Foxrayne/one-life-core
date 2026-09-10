# One/Life Core branding

The branded layer deliberately changes only user-facing Workshop and launcher surfaces. Internal
`Storm` package names, system properties, jar names and install paths stay unchanged so upstream
updates remain easy to merge.

## Visual system

- Near black: `#0c090f`
- Magenta: `#e60069`
- Violet: `#b40074`
- Highlight pink: `#ff4397`
- Text white: `#faf7fc`
- Muted text: `#ab97b3`
- Style: distressed post-apocalyptic texture, restrained neon glow, and CRT/glitch treatment only
  on display branding. Body text remains clean and readable.

## Assets

- `one-life-core-square-master.png`: source for the 512×512 mod poster, 256×256 Workshop preview,
  launcher icon and featured-community badge.
- `one-life-core-banner-master.png`: source for the launcher header and splash banner.
- Root `poster.png` and `preview.png`: generated delivery assets consumed by the existing build.
- Launcher resources under `launcher/src/main/resources/io/pzstorm/launcher/ui/`: scaled delivery
  assets loaded by `OneLifeBrand`.

The generated art uses the supplied One/Life heart-and-knife references and the exact text
`ONE/LIFE CORE`. It intentionally contains no server address so it can be reused if hosting moves.

## Community endpoints

- Server: `172.240.18.253:16261`
- Discord: `https://discord.gg/qccG89rvzT`

## Update and privacy policy

- Storm core CDN updates remain enabled for upstream engine fixes.
- Launcher CDN updates are disabled until One/Life owns an update endpoint; an upstream launcher
  update would replace this branding.
- The bootstrap forces `DISABLE_ANALYTICS=true`, including for a CDN-updated core.
- Remote log upload code and entry points are removed. Crash detection reads only the local tail of
  the current game log.
