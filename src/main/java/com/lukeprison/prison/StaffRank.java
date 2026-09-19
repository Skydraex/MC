package com.lukeprison.prison;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The staff ladder, and what each rung is trusted with.
 *
 * There used to be one permission, prison.admin, which meant a Helper brought on to answer
 * questions in chat could hand themselves a Warden's Pickaxe and a hundred crate keys. The
 * split below is the one classic prison servers settle on, and it exists to make that
 * impossible rather than merely forbidden:
 *
 *   Helper      answers questions. Can look things up and warn. Gives out nothing.
 *   Moderator   enforces. Can mute and kick.
 *   Admin       fixes. Can set ranks, reset players, run the map check.
 *   Super Admin creates. Can hand out keys, gear and money.
 *   Owner       everything, including rebuilding the world.
 *
 * Anything that puts an item or a balance into a player's hands is Super Admin or above,
 * deliberately, because that is the only category of abuse that cannot be undone by
 * reversing it — the items are already spent, traded or sold by the time anyone notices.
 */
public enum StaffRank {
    NONE("Player", null),
    HELPER("Helper", "prison.staff.helper"),
    MOD("Moderator", "prison.staff.mod"),
    ADMIN("Admin", "prison.staff.admin"),
    SUPERADMIN("Super Admin", "prison.staff.superadmin"),
    OWNER("Owner", "prison.staff.owner");

    public final String display;
    public final String permission;

    StaffRank(String display, String permission) {
        this.display = display;
        this.permission = permission;
    }

    public boolean atLeast(StaffRank other) {
        return ordinal() >= other.ordinal();
    }

    /**
     * The highest rung this sender holds.
     *
     * Console is always Owner — it is the server operator by definition. The legacy
     * prison.admin node maps to Admin, so an existing setup keeps working but does not
     * silently gain the power to hand out items.
     */
    public static StaffRank of(CommandSender s) {
        if (!(s instanceof Player)) return OWNER;
        StaffRank best = NONE;
        for (StaffRank r : values()) {
            if (r.permission != null && s.hasPermission(r.permission)) best = r;
        }
        if (best == NONE && s.hasPermission("prison.admin")) best = ADMIN;
        return best;
    }

    // ---- Mutes ------------------------------------------------------------------
    //
    // Kept here rather than in RankManager because a mute is a staff action, not part of a
    // player's progression, and it should not survive in the same file as their rank.

    private static final Map<UUID, Long> MUTED = new HashMap<>();

    public static void mute(UUID id, long minutes) {
        MUTED.put(id, minutes <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + minutes * 60_000L);
    }

    public static void unmute(UUID id) {
        MUTED.remove(id);
    }

    /** Minutes left, 0 if not muted, -1 if muted indefinitely. */
    public static long muteRemaining(UUID id) {
        Long until = MUTED.get(id);
        if (until == null) return 0;
        if (until == Long.MAX_VALUE) return -1;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            MUTED.remove(id);
            return 0;
        }
        return Math.max(1, left / 60_000L);
    }

    public static boolean isMuted(UUID id) {
        return muteRemaining(id) != 0;
    }
}
