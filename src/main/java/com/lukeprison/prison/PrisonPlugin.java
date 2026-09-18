package com.lukeprison.prison;

import org.bukkit.Bukkit;
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
    private PvpZoneManager pvpManager;

    public static PrisonPlugin get() { return instance; }
    public Economy economy() { return economy; }
    public RankManager ranks() { return rankManager; }
    public ScoreboardManager scoreboard() { return scoreboardManager; }
    public FishingManager fishing() { return fishingManager; }
    public WorldBuilder builder() { return worldBuilder; }
    public CrateListener crates() { return crateListener; }
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

        World world = Bukkit.getWorlds().get(0);

        worldBuilder = new WorldBuilder(this, world);
        // Bounds are registered every boot (cheap, no block placement). The full build runs
        // only on first boot — a marker file skips it afterwards, so restarts are fast.
        worldBuilder.registerBounds();
        if (worldBuilder.alreadyBuilt()) {
            getLogger().info("World already built \u2014 skipping construction.");
        } else {
            worldBuilder.buildAll();
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

        crateListener = new CrateListener(this);
        worldBuilder.getCrateLocations().forEach(crateListener::registerCrate);
        pvpManager = new PvpZoneManager(this);
        worldBuilder.getPvpZones().forEach(pvpManager::addZone);

        getServer().getPluginManager().registerEvents(ranksGUI, this);
        getServer().getPluginManager().registerEvents(enchantGUI, this);
        getServer().getPluginManager().registerEvents(menuGUI, this);
        getServer().getPluginManager().registerEvents(miningListener, this);
        getServer().getPluginManager().registerEvents(fishingCommands, this);
        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
        getServer().getPluginManager().registerEvents(npcManager, this);
        getServer().getPluginManager().registerEvents(crateListener, this);
        getServer().getPluginManager().registerEvents(pvpManager, this);
        getServer().getPluginManager().registerEvents(this, this);

        // Spawn quest NPCs a tick later so the world is fully ready.
        Bukkit.getScheduler().runTaskLater(this, npcManager::spawnNpcs, 40L);

        getCommand("rankup").setExecutor(new RankUpCommand(this, ranksGUI));
        getCommand("rank").setExecutor(new RankInfoCommand(this));
        getCommand("prestige").setExecutor(new PrestigeCommand(this));
        getCommand("prison").setExecutor(new SimpleCommands.PrisonMenu(menuGUI));
        getCommand("warps").setExecutor(new SimpleCommands.Warps(menuGUI));
        getCommand("enchant").setExecutor(new SimpleCommands.Enchant(enchantGUI));
        getCommand("tokens").setExecutor(new SimpleCommands.Tokens(this));
        getCommand("autosell").setExecutor(new SimpleCommands.AutoSell(this));
        getCommand("fishing").setExecutor(new FishingCommands.Fishing(fishingCommands));
        getCommand("sellfish").setExecutor(new FishingCommands.SellFish(this));
        getCommand("spawn").setExecutor(new FishingCommands.Spawn(this));

        resetTask = new MineResetTask(this, worldBuilder);
        resetTask.runTaskTimer(this, 20L * 60, 20L * 60 * 5); // check every 5 min, first check after 1 min

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                scoreboardManager.update(p);
                miningListener.applyHaste(p);
                miningListener.applyFlight(p);
            }
        }, 20L, 20L * 3); // refresh every 3 seconds

        getLogger().info("PrisonPlugin fully built and enabled — " + RankMineData.RANKS.size() + " ranks, "
                + worldBuilder.getMineBounds().size() + " mines constructed.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> scoreboardManager.update(e.getPlayer()), 5L);
    }

    @Override
    public void onDisable() {
        if (rankManager != null) rankManager.save();
        if (fishingManager != null) fishingManager.save();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}
