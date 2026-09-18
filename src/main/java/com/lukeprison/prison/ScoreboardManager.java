package com.lukeprison.prison;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Maintains the right-hand sidebar scoreboard for every online player: rank, balance, next rank cost. */
public class ScoreboardManager {

    private final PrisonPlugin plugin;

    public ScoreboardManager(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player p) {
        Scoreboard board = plugin.getServer().getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("prison", "dummy", "§6§lTHE PRISON");
        obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

        String rank = plugin.ranks().getRank(p);
        int prestige = plugin.ranks().getPrestige(p);
        RankMineData.Def def = RankMineData.RANKS.get(rank);
        double balance = plugin.economy().getBalance(p);

        String rankLine = prestige > 0 ? rank + " §7(P" + prestige + ")" : rank;
        String nextLine;
        if (def == null || def.next.equals(rank)) {
            nextLine = "§7Max rank!";
        } else {
            RankMineData.Def next = RankMineData.RANKS.get(def.next);
            nextLine = "§f" + next.rank + " §7- $" + format(next.cost);
        }

        setLine(board, obj, 6, "§7Rank: §e" + rankLine);
        setLine(board, obj, 5, "§7Balance: §a$" + format(balance));
        setLine(board, obj, 4, "§7Tokens: §b" + plugin.ranks().getTokens(p));
        setLine(board, obj, 3, "§7Next: " + nextLine);
        setLine(board, obj, 2, " ");
        setLine(board, obj, 1, "§7/prison for menu");

        p.setScoreboard(board);
    }

    private void setLine(Scoreboard board, Objective obj, int score, String text) {
        // Teams let two lines share the same visible text without colliding on the scoreboard.
        String entry = "§" + Integer.toHexString(score) + "§r";
        Team team = board.getTeam("line" + score);
        if (team == null) team = board.registerNewTeam("line" + score);
        team.addEntry(entry);
        team.setPrefix(text.length() > 64 ? text.substring(0, 64) : text);
        obj.getScore(entry).setScore(score);
    }

    private String format(double amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000);
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000) return String.format("%.1fK", amount / 1_000);
        return String.format("%.0f", amount);
    }
}
