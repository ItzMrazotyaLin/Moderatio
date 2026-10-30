package org.itzmr.moderatio.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * Utility for serializing and deserializing Bukkit Location objects to/from configuration strings.
 */
public final class LocationUtils {

    private LocationUtils() {
    }

    /**
     * Serializes a Location to "world,x,y,z,yaw,pitch" string.
     */
    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return String.format(Locale.US, "%s,%.2f,%.2f,%.2f,%.2f,%.2f",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch()
        );
    }

    /**
     * Deserializes a "world,x,y,z,yaw,pitch" string to a Bukkit Location.
     */
    public static Location deserialize(String str) {
        if (str == null || str.isBlank() || str.equalsIgnoreCase("null")) {
            return null;
        }

        String[] parts = str.split(",");
        if (parts.length < 4) {
            return null;
        }

        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }

        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
