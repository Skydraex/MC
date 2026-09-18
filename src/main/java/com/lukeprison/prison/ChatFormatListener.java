package com.lukeprison.prison;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;

/**
 * Chat and tab-list formatting. Every message carries the player's rank and prestige, which is
 * how a prison server communicates the pecking order at a glance:  [P3 ★] [K] Name: hello
 */
public class ChatFormatListener implements Listener {

    private final PrisonPlugin plugin;

    public ChatFormatListener(PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /** The coloured rank tag: letter ranks fade from grey through green, gold, aqua, purple. */
    public Component rankTag(Player p) {
        String rank = plugin.ranks().getRank(p);
        int prestige = plugin.ranks().getPrestige(p);

        NamedTextColor colour;
        if (rank.equals("FREE")) colour = NamedTextColor.LIGHT_PURPLE;
        else {
            char c = rank.charAt(0);
            if (c <= 'F') colour = NamedTextColor.GRAY;
            else if (c <= 'L') colour = NamedTextColor.GREEN;
            else if (c <= 'R') colour = NamedTextColor.GOLD;
            else colour = NamedTextColor.AQUA;
        }

        Component tag = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(rank, colour, TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));

        if (prestige > 0) {
            tag = Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text("P" + prestige + " ★", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(tag);
        }
        return tag;
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Component tag = rankTag(p);
        e.renderer((source, displayName, message, viewer) ->
                tag.append(Component.text(" "))
                        .append(Component.text(source.getName(), NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                        .append(message.color(NamedTextColor.GRAY)));
    }

    /** Refreshes the tab-list name; called on join and after every rank/prestige change. */
    public void updateTab(Player p) {
        p.playerListName(rankTag(p).append(Component.text(" " + p.getName(), NamedTextColor.WHITE)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        updateTab(p);

        boolean firstTime = !plugin.ranks().hasReceivedKit(p);
        Component main = Component.text("SKY PRISON", NamedTextColor.AQUA, TextDecoration.BOLD);
        Component sub = firstTime
                ? Component.text("Your sentence starts now.", NamedTextColor.GOLD)
                : Component.text("Welcome back, inmate.", NamedTextColor.GRAY);
        p.showTitle(Title.title(main, sub, Title.Times.times(
                Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));

        e.joinMessage(Component.text("+ ", NamedTextColor.GREEN)
                .append(rankTag(p))
                .append(Component.text(" " + p.getName(), NamedTextColor.GRAY)));
    }
}
