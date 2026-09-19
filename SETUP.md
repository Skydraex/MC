# Setting up, start to finish (Windows)

Two separate things, in this order. The server works without the bot; the bot is only useful
once the server is up.

- **Part A** — get the server running. No Git needed.
- **Part B** — the bot that lets Claude join and walk around.

---

## Already have a server running?

If you already have a Paper server with this plugin in it — a folder with `paper.jar`,
`plugins/` and a `prison` world folder — then **skip all of Part A**. Everything in it is
first-time setup you have already done.

To take a new build:

1. Go to https://github.com/Skydraex/MC/actions and click the newest run with a green tick.
2. Scroll to **Artifacts**, download **PrisonPlugin**, unzip it.
3. Stop the server if it is running.
4. Replace the old `PrisonPlugin.jar` in your `plugins` folder with the new one.
5. Delete the `prison` world folder. This is what forces the map to rebuild — the plugin
   checks the world itself, so deleting the folder is all that is needed.
6. Start the server.
7. Watch the console for:

   ```
   Startup audit: PASS - world and plugin both check out.
   ```

   If it says `Startup audit: N problem(s) found`, the lines under it say what and where.

Then go straight to **Part B** if you want the bot.

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

### A2. Make the server folder

1. Open **File Explorer**.
2. Click **This PC**, then double-click **Local Disk (C:)**.
3. Right-click in the empty space → **New** → **Folder**.
4. Name it `prison-server` and press Enter.

You now have `C:\prison-server`. Leave the window open.

### A3. Download Paper

1. Go to https://papermc.io/downloads/paper
2. The newest version is selected already. Click the big download button.
3. You get a file named something like `paper-26.2-124.jar`.
4. Move it into `C:\prison-server`.
5. Rename it to exactly `paper.jar`.

   *If you cannot see the `.jar` on the end:* in File Explorer click **View** →
   tick **File name extensions**. Without that, renaming can leave you with
   `paper.jar.jar`, which will not run.

### A4. Make the start file

This is the step people most often get wrong, because Notepad silently adds `.txt`.

1. Open **Notepad** (press the Windows key, type `notepad`, Enter).
2. Paste exactly this:

   ```bat
   @echo off
   java -Xms2G -Xmx4G -jar paper.jar nogui
   pause
   ```

3. Press **Ctrl+S**.
4. Navigate to `C:\prison-server`.
5. **Change "Save as type" from "Text Documents (*.txt)" to "All Files (*.*)".**
   Miss this and you get `start.bat.txt`, which does nothing when you click it.
6. In the File name box type `start.bat`
7. Click **Save**.

Back in File Explorer you should see `start.bat` with a gear/cog icon, **not** a
notepad icon. If it looks like a notepad file, repeat from step 3.

### A5. First run — accepting the EULA

1. Double-click `start.bat`.
2. A black window opens, runs for a few seconds, and stops with a message about the EULA.
3. Press a key to close it.
4. A new file `eula.txt` has appeared in `C:\prison-server`. Open it in Notepad.
5. Find the line `eula=false` and change it to `eula=true`.
6. Save (Ctrl+S) and close.

### A6. Second run — generating the server

1. Double-click `start.bat` again.
2. This time it keeps going. Wait for it to say `Done` — the first time takes a minute or two.
3. Type `stop` into the black window and press Enter.
4. Wait for it to close down, then press a key.

You now have a `plugins` folder inside `C:\prison-server`.

### A7. Download the plugins

You need three files to start with. Put all of them in `C:\prison-server\plugins`.

**Vault** (the economy bridge)
1. Go to https://www.spigotmc.org/resources/vault.34315/
2. Click **Download Now**.

**EssentialsX** (the actual economy)
1. Go to https://essentialsx.net/downloads.html
2. Download **EssentialsX** (the first one). You do not need the extra modules.

**PrisonPlugin** (this server)
1. Go to https://github.com/Skydraex/MC/actions
2. Click the newest run that has a **green tick**.
3. Scroll to the bottom, to **Artifacts**.
4. Click **PrisonPlugin** to download a `.zip`.
5. Right-click the zip → **Extract All** → you get `PrisonPlugin.jar`.

Move all three `.jar` files into `C:\prison-server\plugins`.

### A8. Build the world

1. Double-click `start.bat`.
2. This boot builds the whole prison — about 1.1 million blocks. It will say so and take
   noticeably longer than the last one. Let it finish.
3. Near the end, look for this line:

   ```
   Startup audit: PASS - world and plugin both check out.
   ```

   If it instead says `Startup audit: N problem(s) found`, the lines directly underneath
   name exactly what is wrong and where. Copy those lines and send them to me.

4. Leave the server running.

### A9. Join

1. Open Minecraft Java Edition.
2. **Multiplayer** → **Add Server**.
3. Server Address: `localhost`
4. Save, then join.

In game, type `/padmin audit` to run the same check again whenever you want.

**To rebuild the world from scratch later:** stop the server, delete the `prison` folder
inside `C:\prison-server`, start it again. Nothing else needed.

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
