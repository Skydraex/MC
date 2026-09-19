# Setting up, start to finish (Windows)

Two separate things, in this order. The server works without the bot; the bot is only useful
once the server is up.

- **Part A** — get the server running. No Git needed.
- **Part B** — the bot that lets Claude join and walk around.

---

## Part A — the server

### A1. Install Java 25

**This matters and is easy to get wrong.** The plugin is compiled for Java 25:

```
pom.xml:  <maven.compiler.target>25</maven.compiler.target>
```

On Java 21 the server starts, then refuses the plugin with `UnsupportedClassVersionError`
and nothing else works. Get **JDK 25** from https://adoptium.net (Temurin, Windows x64, .msi).

Check it in a terminal — Git Bash, PowerShell or Command Prompt, any is fine:

```
java -version
```

You want `version "25"` or higher. If you had an older Java installed, the installer may not
have replaced it on PATH; uninstall the old one or fix PATH before continuing.

### A2. Make a server folder

Anywhere you like, e.g. `C:\prison-server`. Inside it:

1. Download **Paper 26.2** from https://papermc.io/downloads — put `paper.jar` in that folder.
2. Make a file called `start.bat` containing:

   ```bat
   @echo off
   java -Xms2G -Xmx4G -jar paper.jar nogui
   pause
   ```

   2–4 GB is right for this build. First boot generates about 1.1 million blocks.

3. Double-click `start.bat`. It will stop and tell you to accept the EULA.
4. Open `eula.txt`, change `eula=false` to `eula=true`, save.
5. Run `start.bat` again, let it finish, then type `stop` in the console.

You now have a `plugins` folder.

### A3. Plugins

Into `C:\prison-server\plugins`:

| Plugin | Where | Why |
|---|---|---|
| **Vault** | https://www.spigotmc.org/resources/vault.34315/ | the economy bridge — required |
| **EssentialsX** | https://essentialsx.net/downloads.html | the actual economy — required |
| **PrisonPlugin.jar** | see below | this server |
| **ViaVersion** | https://hangar.papermc.io/ViaVersion/ViaVersion | only needed for Part B |
| **ViaBackwards** | https://hangar.papermc.io/ViaVersion/ViaBackwards | only needed for Part B |

**Getting PrisonPlugin.jar:** go to
https://github.com/Skydraex/MC/actions — click the newest run with a green tick →
scroll to **Artifacts** → download **PrisonPlugin** → unzip → `PrisonPlugin.jar`.

### A4. First run

Start the server. On the first boot it builds the world — the console will say so and it
takes a little while. Watch for this near the end:

```
Startup audit: PASS - world and plugin both check out.
```

If it says `Startup audit: N problem(s) found` instead, the lines under it say exactly what
is wrong and where. Send me those lines.

Join at `localhost`. In game, `/padmin audit` re-runs the same check on demand.

**To rebuild the world later:** stop the server, delete the `prison` folder, start it again.
That is all — no command needed.

---

## Part B — the bot

Only worth doing once Part A works.

### B1. Node.js

Install **Node 20.10 or newer** from https://nodejs.org (LTS, Windows .msi). Check:

```
node --version
```

### B2. Let the bot's version of Minecraft in

The bot speaks Minecraft **1.21.11**. This server is **26.2**. Without help it simply cannot
connect — wrong protocol, not a warning you can click past.

Put **ViaVersion** and **ViaBackwards** in `plugins` (both — ViaBackwards depends on
ViaVersion). ViaBackwards is the one that lets older clients onto a newer server.

### B3. Let the bot log in

The bot has no Mojang account. In `server.properties`:

```
online-mode=false
```

**Only do this on a local test server.** With it off, anyone who can reach the server can
join as any username, including yours.

### B4. Clone the repo and open it in Claude Code

Git Bash is fine for this — or GitHub Desktop, or the Claude Code app's own folder picker.
In Git Bash:

```bash
cd /c/
git clone https://github.com/Skydraex/MC.git
```

That gives you `C:\MC`.

Then in the **Claude Code desktop app**, open a session pointed at `C:\MC`. This is the step
that matters: it must be a *local* session on your machine, not a cloud one. You can tell
which you have by asking it to run `pwd` — a local session shows `/c/MC` or `C:\MC`, a cloud
session shows `/home/user/MC`.

### B5. The MCP config

**Already done** — `.mcp.json` is committed at the root of the repo, so a local Claude Code
session in `C:\MC` picks it up automatically. You will be asked to approve the server the
first time; say yes.

Note this is *not* `claude_desktop_config.json` — that file is for the Claude chat app.
Claude Code reads `.mcp.json` from the project instead.

If the server is not on the same machine, edit `.mcp.json` and change `localhost` to its
address.

### B6. Go

With the Minecraft server running, start the Claude Code session and ask it to connect.
`ClaudeBot` will appear in the player list.

---

## What the bot can and cannot tell you

**Can:** whether a door opens, whether a path connects, whether a room is sealed, whether a
drop is survivable — anything structural.

**Cannot:** whether something looks right. ViaBackwards translates packets, it does not
backport blocks, so materials newer than 1.21.11 — cherry and mangrove wood,
`CHISELED_BOOKSHELF`, `PINK_PETALS`, `TINTED_GLASS` — reach the bot as substitutes. What it
sees is not what you see.

For structure, `/padmin audit` is better than the bot anyway: no version mismatch, no
substituted blocks, and it checks all 26 mines in seconds rather than the two you would have
the patience to walk.
