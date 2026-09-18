package com.lukeprison.prison;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

/**
 * Floating text that's readable from every angle, not just face-on like a wall sign.
 * A vanilla sign's text is baked onto one flat face of the block, so it vanishes once you're
 * not roughly in front of it. An entity's name tag, by contrast, always billboards to face
 * whoever's looking at it — that's the same trick other prison servers' floating labels use,
 * and it needs no extra plugin, just an invisible marker entity per line of text.
 */
public class Hologram {

    private static final double LINE_SPACING = 0.28;

    /** Spawns one stacked line of floating text per string, tallest line on top. */
    public static void spawn(Location base, String... lines) {
        if (lines.length == 0) return;
        double y = base.getY() + (lines.length - 1) * LINE_SPACING;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                y -= LINE_SPACING;
                continue;
            }
            Location loc = base.clone();
            loc.setY(y);
            ArmorStand stand = (ArmorStand) base.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setCustomName(line);
            stand.setCustomNameVisible(true);
            stand.setPersistent(true);
            stand.setSilent(true);
            y -= LINE_SPACING;
        }
    }
}
