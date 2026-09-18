# Prison Plugin — Build & Install (no coding required)

Everything about your server — ranks, all 26 mines (walls, ore fill, entrances), sell signs,
the reset system — is built automatically by this plugin the first time it starts. You don't
place a single block or type a setup command by hand.

## Step 1 — Get the compiled .jar (via GitHub, no commands typed)

1. Go to https://github.com/new and create a new **empty** repository (any name, e.g. `my-prison-server`).
   Keep it Public (Private also works but uses your Actions minutes).
2. On the repo page, click **"uploading an existing file"** (or drag-and-drop) and upload every file
   and folder from this package, keeping the folder structure exactly as given
   (`pom.xml`, `src/`, `.github/`).
3. Commit the upload. GitHub will automatically start building — click the **Actions** tab on your
   repo to watch it (takes about a minute).
4. When it finishes (green check), click into that run → **Artifacts** → download **PrisonPlugin**.
   Unzip it — that's your `PrisonPlugin.jar`.

That's the only non-Minecraft step, and it's clicking buttons on a webpage, not writing code.

## Step 2 — Install on your server

1. Get a **Paper** server jar (1.21.x) from https://papermc.io/downloads and start it once so it
   generates its folders, then stop it.
2. Install **Vault** and **EssentialsX** (EssentialsX gives Vault an economy to hook into — Prison Plugin
   needs an economy plugin present, it doesn't include its own).
3. Drop `PrisonPlugin.jar` into `/plugins`.
4. Start the server.

On that first startup, the plugin builds the hub platform, all 26 mines (with walls, ore
distribution, and a sell sign at each entrance), and registers the rank/prestige system —
entirely on its own. Give it a minute on first boot; building 26 mines block-by-block takes
a little time.

## Player commands

- `/rank` — see current rank and cost to the next one
- `/rankup` — spend money to advance (if you can afford it)
- `/prestige` — once at Free, reset to A for a permanent prestige level

## What this first version does and doesn't include yet

**Included, fully automatic:**
- All 26 ranks (A–Z) + Free, with costs tuned for the 8–12 week grind we agreed on
- All 26 mines, physically built, walled, entrance carved, auto-resetting
- A working sell sign at every mine (right-click while holding an item to sell it)
- Mine access is locked to your current rank and below
- Prestige system (unlimited levels)

**Not yet included — tell me which to do next:**
- The physical hub/spawn area is a bare platform right now — no buildings, decoration, or
  cell blocks yet. I can generate those next.
- Donor ranks (VIP/MVP/Elite perks) — these need LuckPerms wired in; I left that out of this
  plugin to keep the first version simple and testable.
- Free World (the escape area at rank Free) isn't built yet.
- Token currency and the token shop aren't implemented yet — only Money is wired up.

This is a first working version — I haven't been able to compile and test-run it myself since
I don't have access to a live Minecraft server, so treat it as a strong starting point rather
than guaranteed bug-free. If anything errors on startup, paste me the console error and I'll
fix it.
