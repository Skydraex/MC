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

    public static PrisonPlugin get() { return instance; }
    public Economy economy() { return economy; }
    public RankManager ranks() { return rankManager; }
    public ScoreboardManager scoreboard() { return scoreboardManager; }

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

        World world = Bukkit.getWorlds().get(0);

        worldBuilder = new WorldBuilder(this, world);
        // Builds every mine (walls + ore fill), the hub platform, and all sell signs.
        // Runs once automatically — this is the "build everything by code" step.
        worldBuilder.buildAll();

        sellSignListener = new SellSignListener(this, worldBuilder.getSellSigns());
        getServer().getPluginManager().registerEvents(sellSignListener, this);
        getServer().getPluginManager().registerEvents(new MineProtectionListener(this, worldBuilder.getMineBounds()), this);
        getServer().getPluginManager().registerEvents(new KitListener(this, worldBuilder.getHubSpawn()), this);

        scoreboardManager = new ScoreboardManager(this);
        ranksGUI = new RanksGUI(this);
        getServer().getPluginManager().registerEvents(ranksGUI, this);
        getServer().getPluginManager().registerEvents(this, this);

        getCommand("rankup").setExecutor(new RankUpCommand(this, ranksGUI));
        getCommand("rank").setExecutor(new RankInfoCommand(this));
        getCommand("prestige").setExecutor(new PrestigeCommand(this));

        resetTask = new MineResetTask(this, worldBuilder);
        resetTask.runTaskTimer(this, 20L * 60, 20L * 60 * 5); // check every 5 min, first check after 1 min

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) scoreboardManager.update(p);
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
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}
