package com.lukeprison.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

public class PrisonPlugin extends JavaPlugin implements Listener {

    private static PrisonPlugin instance;
    private Economy economy;
    private RankManager rankManager;
    private WorldBuilder worldBuilder;
    private MineResetTask resetTask;
    private SellSignListener sellSignListener;
    private ScoreboardManager scoreboardManager;
    private RanksGUI ranksGUI;
    private FishingManager fishingManager;
    private CrateListener crateListener;
    private JailManager jailManager;
    private CombatTagListener combatTag;
    private TeleportGuard teleportGuard;
    private HelpGUI helpGUI;
    private CoinflipManager coinflipManager;
    private ChatFormatListener chatFormat;
    private ProgressionFeatures progression;
    private CellManager cellManager;
    private PvpZoneManager pvpManager;

    public static PrisonPlugin get() { return instance; }
    public Economy economy() { return economy; }
    public RankManager ranks() { return rankManager; }
    public ScoreboardManager scoreboard() { return scoreboardManager; }
    public HelpGUI help() { return helpGUI; }
    public FishingManager fishing() { return fishingManager; }
    public WorldBuilder builder() { return worldBuilder; }
    public CrateListener crates() { return crateListener; }
    public JailManager jail() { return jailManager; }
    public CombatTagListener combat() { return combatTag; }
    public TeleportGuard teleports() { return teleportGuard; }
    public CoinflipManager coinflips() { return coinflipManager; }
    public ChatFormatListener chat() { return chatFormat; }
    public PvpZoneManager pvp() { return pvpManager; }

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("No Vault economy found! Install an economy plugin (e.g. EssentialsX). Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        rankManager = new RankManager(this);
        rankManager.load();
        fishingManager = new FishingManager(this);
        fishingManager.load();

        // The prison lives in its own void world so it floats in the sky with no terrain.
        // Created by the plugin itself, so no server config needs editing.
        World world = Bukkit.getWorld("prison");
        if (world == null) {
            // Real countryside rather than empty void, so nothing looks off the edge of the
            // world. Generated rather than built: chunks are lazy, so the land costs the first
            // boot nothing while extending as far as anyone walks.
            PrisonWorldGenerator gen = new PrisonWorldGenerator();
            world = new org.bukkit.WorldCreator("prison")
                    .generator(gen)
                    .biomeProvider(gen.getDefaultBiomeProvider(null))
                    .environment(World.Environment.NORMAL)
                    .createWorld();
        }
        if (world == null) {
            getLogger().severe("Could not create the prison world. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        world.setTime(6000);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);

        worldBuilder = new WorldBuilder(this, world);
        // Bounds are registered every boot (cheap, no block placement). The full build runs
        // only on first boot — a marker file skips it afterwards, so restarts are fast.
        worldBuilder.registerBounds();
        boolean firstBoot = !worldBuilder.alreadyBuilt();
        if (!firstBoot) {
            getLogger().info("World already built \u2014 skipping construction.");
        } else {
            getLogger().info("Building the prison. This takes a while on first boot.");
            long started = System.currentTimeMillis();
            try {
                worldBuilder.buildAll();
                getLogger().info("Prison build finished in "
                        + ((System.currentTimeMillis() - started) / 1000) + "s.");
            } catch (RuntimeException ex) {
                // Never let a half-finished build pass for a finished one. Without this the
                // marker would be missing but the failure silent, and the next boot would try
                // again on top of the wreckage with no clue in the log as to what went wrong.
                getLogger().severe("The prison build FAILED partway through: " + ex);
                ex.printStackTrace();
                getLogger().severe("Delete the 'prison' world folder and restart to try again.");
            }
        }

        sellSignListener = new SellSignListener(this, worldBuilder.getSellSigns());
        getServer().getPluginManager().registerEvents(sellSignListener, this);
        getServer().getPluginManager().registerEvents(new MineProtectionListener(this, worldBuilder.getMineBounds()), this);
        getServer().getPluginManager().registerEvents(new KitListener(this, worldBuilder.getStarterSpawn()), this);

        scoreboardManager = new ScoreboardManager(this);
        ranksGUI = new RanksGUI(this);
        EnchantGUI enchantGUI = new EnchantGUI(this);
        MenuGUI menuGUI = new MenuGUI(this, ranksGUI, enchantGUI, worldBuilder.getMineBounds());
        MiningListener miningListener = new MiningListener(this, worldBuilder.getMineBounds());
        FishingCommands fishingCommands = new FishingCommands(this);
        QuestNpcManager npcManager = new QuestNpcManager(this, worldBuilder);

        jailManager = new JailManager(this);
        combatTag = new CombatTagListener(this);
        teleportGuard = new TeleportGuard(this);
        getServer().getPluginManager().registerEvents(jailManager, this);
        getServer().getPluginManager().registerEvents(combatTag, this);
        getServer().getPluginManager().registerEvents(teleportGuard, this);

        crateListener = new CrateListener(this);
        for (String problem : CrateData.audit()) {
            getLogger().warning("Crate loot table: " + problem);
        }
        worldBuilder.getCrateLocations().forEach(crateListener::registerCrate);
        pvpManager = new PvpZoneManager(this);
        worldBuilder.getPvpZones().forEach(pvpManager::addZone);
        coinflipManager = new CoinflipManager(this);
        chatFormat = new ChatFormatListener(this);
        progression = new ProgressionFeatures(this);
        cellManager = new CellManager(this, worldBuilder.getCellRects());
        ShopGUI shopGui = new ShopGUI(this);

        getServer().getPluginManager().registerEvents(ranksGUI, this);
        getServer().getPluginManager().registerEvents(enchantGUI, this);
        getServer().getPluginManager().registerEvents(menuGUI, this);
        getServer().getPluginManager().registerEvents(miningListener, this);
        getServer().getPluginManager().registerEvents(fishingCommands, this);
        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
        getServer().getPluginManager().registerEvents(npcManager, this);
        getServer().getPluginManager().registerEvents(crateListener, this);
        getServer().getPluginManager().registerEvents(pvpManager, this);
        getServer().getPluginManager().registerEvents(coinflipManager, this);
        getServer().getPluginManager().registerEvents(new ShopSignListener(this), this);
        ProtectionListener protectionListener = new ProtectionListener(this, worldBuilder.getMineBounds());
        protectionListener.setLoggingBounds(worldBuilder.getLoggingBounds());
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(chatFormat, this);
        getServer().getPluginManager().registerEvents(cellManager, this);
        getServer().getPluginManager().registerEvents(shopGui, this);
        // The mine cages: the lift between each ward and its pit underground.
        getServer().getPluginManager().registerEvents(new LiftListener(this), this);
        // Replaces vanilla /help, which spans 21 pages of other plugins' commands.
        helpGUI = new HelpGUI();
        getServer().getPluginManager().registerEvents(helpGUI, this);

        // Night vision underground. The mines sit well below the surface with no daylight, and
        // lamps alone leave the ore face too dark to work by; the effect is refreshed while a
        // player is inside a pit chamber and cleared as soon as they leave.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (worldBuilder == null) return;
                var loc = p.getLocation();
                boolean inMine = loc.getWorld() != null
                        && loc.getWorld().equals(worldBuilder.getWorld())
                        && worldBuilder.isInAnyMine(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if (inMine) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
                } else if (p.hasPotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION)) {
                    p.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
                }
            }
        }, 40L, 40L);
        getServer().getPluginManager().registerEvents(this, this);

        // A moment after the world is ready: sync sign text to clients, then spawn NPCs.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            worldBuilder.applySigns();
            if (firstBoot) worldBuilder.spawnHolograms();
            npcManager.spawnNpcs();
        }, 40L);

        getCommand("rankup").setExecutor(new RankUpCommand(this, ranksGUI));
        getCommand("rank").setExecutor(new RankInfoCommand(this));
        getCommand("prestige").setExecutor(new PrestigeCommand(this));
        getCommand("prison").setExecutor(new SimpleCommands.PrisonMenu(menuGUI));
        getCommand("warps").setExecutor(new SimpleCommands.Warps(menuGUI));
        getCommand("mine").setExecutor(new SimpleCommands.Mine(menuGUI));
        getCommand("enchant").setExecutor(new SimpleCommands.Enchant(enchantGUI));
        getCommand("autosell").setExecutor(new SimpleCommands.AutoSell(this));
        getCommand("rules").setExecutor(new SimpleCommands.Rules());
        getCommand("vote").setExecutor(new SimpleCommands.Vote());
        getCommand("fishing").setExecutor(new FishingCommands.Fishing(fishingCommands));
        getCommand("sellfish").setExecutor(new FishingCommands.SellFish(this));
        getCommand("spawn").setExecutor(new FishingCommands.Spawn(this));
        getCommand("coinflip").setExecutor(new CoinflipManager.Cmd(coinflipManager));
        getCommand("prisonadmin").setExecutor(new AdminCommands(this));
        getCommand("daily").setExecutor(progression.new Daily());
        getCommand("top").setExecutor(progression.new Top());
        getCommand("sell").setExecutor(progression.new Sell());
        getCommand("cell").setExecutor(cellManager.new Cmd());
        getCommand("shop").setExecutor(new ShopGUI.Cmd(shopGui));

        resetTask = new MineResetTask(this, worldBuilder);
        resetTask.runTaskTimer(this, 20L * 60, 20L * 60 * 5); // check every 5 min, first check after 1 min

        Bukkit.getScheduler().runTaskTimer(this, cellManager::checkExpiredRentals, 20L * 60, 20L * 60 * 15); // every 15 min
        Bukkit.getScheduler().runTaskTimer(this, worldBuilder::regrowTrees, 20L * 60 * 3, 20L * 60 * 3); // every 3 min

        // Dropped items are the one thing a prison server produces in dangerous quantity:
        // a mine reset with a full server in it can leave thousands of them lying around.
        new GroundItemCleanup(this).start();

        // Audit on every boot, AFTER applySigns has run at tick 40. The first version ran at
        // tick 20 and reported all 209 signs missing, every single boot, because it was
        // looking for them a second before they were placed. An audit that cries wolf is
        // worse than no audit: the two real findings were buried under its noise.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            MapAuditor worldAudit = new MapAuditor(worldBuilder);
            worldAudit.runAll();
            SystemAudit system = new SystemAudit(this);
            system.runAll();
            int total = worldAudit.getFindings().size() + system.getFindings().size();
            if (total == 0) {
                getLogger().info("Startup audit: PASS - world and plugin both check out.");
            } else {
                getLogger().warning("Startup audit: " + total + " problem(s) found.");
                for (MapAuditor.Finding f : worldAudit.getFindings()) {
                    getLogger().warning("  [" + f.category() + "] " + f);
                }
                for (SystemAudit.Finding f : system.getFindings()) {
                    getLogger().warning("  [" + f.category() + "] " + f.detail());
                }
                getLogger().warning("Run /padmin audit in game for the same report.");
            }
        }, 60L);

        jailManager.start();
        new ServerAnnouncer(this).start();

        // Periodic autosave. Everything was written to disk in onDisable and nowhere else,
        // so a crash or a kill -9 lost every player's rank, cell and fishing progress since
        // the server started. Five minutes is the most anyone can now lose.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (rankManager != null) rankManager.save();
            if (fishingManager != null) fishingManager.save();
        }, 20L * 60 * 5, 20L * 60 * 5);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            int max = Bukkit.getMaxPlayers();
            net.kyori.adventure.text.Component bar = net.kyori.adventure.text.Component.text()
                    .append(net.kyori.adventure.text.Component.text("There are currently ", net.kyori.adventure.text.format.NamedTextColor.AQUA))
                    .append(net.kyori.adventure.text.Component.text(online + "/" + max, net.kyori.adventure.text.format.NamedTextColor.GREEN))
                    .append(net.kyori.adventure.text.Component.text(" inmates online", net.kyori.adventure.text.format.NamedTextColor.AQUA))
                    .build();
            for (Player p : Bukkit.getOnlinePlayers()) {
                scoreboardManager.update(p);
                miningListener.applyHaste(p);
                progression.checkMilestones(p);
                p.sendActionBar(bar);
            }
        }, 20L, 20L * 3); // refresh every 3 seconds

        // Server-level hardening. These matter more than any listener for a public server.
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
        world.setGameRule(org.bukkit.GameRule.DO_INSOMNIA, false);
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false);

        getLogger().info("PrisonPlugin fully built and enabled — " + RankMineData.RANKS.size() + " ranks, "
                + worldBuilder.getMineBounds().size() + " mines constructed.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Anyone who somehow isn't in the prison world (e.g. logged out mid-transfer) comes home.
        if (worldBuilder != null && !p.getWorld().equals(worldBuilder.getWorld())) {
            Location home = rankManager.hasReceivedKit(p) ? worldBuilder.getHubSpawn() : worldBuilder.getStarterSpawn();
            if (home != null) p.teleport(home);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> scoreboardManager.update(p), 5L);
    }

    /** Respawns go to the hub, or the bus for players who never finished intake. */
    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location home = rankManager.hasReceivedKit(p) ? worldBuilder.getHubSpawn() : worldBuilder.getStarterSpawn();
        if (home != null) e.setRespawnLocation(home);
    }

    /**
     * Flight is deliberately not part of this server. Walking between wards is how the map
     * reads as a place, so survival-mode players never keep flight, however they got it.
     */
    @EventHandler
    public void onToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);
    }

    @Override
    public void onDisable() {
        if (rankManager != null) rankManager.save();
        if (fishingManager != null) fishingManager.save();
        // Refund escrowed wagers so a restart never eats anyone's money.
        if (coinflipManager != null) coinflipManager.refundAll();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}
