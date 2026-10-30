package org.itzmr.moderatio.staff;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Model representing an active cheat check session.
 */
public class CheckSession {

    private final UUID moderatorUuid;
    private final String moderatorName;
    private final UUID suspectUuid;
    private final String suspectName;
    private final long startTime;
    private final int totalSeconds;
    private int remainingSeconds;
    private BukkitTask timerTask;
    private BossBar bossBar;
    private boolean hasProvidedContact;
    private String providedContact;
    private String providedService;

    public CheckSession(UUID moderatorUuid, String moderatorName, UUID suspectUuid, String suspectName, int totalSeconds) {
        this.moderatorUuid = moderatorUuid;
        this.moderatorName = moderatorName;
        this.suspectUuid = suspectUuid;
        this.suspectName = suspectName;
        this.startTime = System.currentTimeMillis();
        this.totalSeconds = totalSeconds;
        this.remainingSeconds = totalSeconds;
        this.hasProvidedContact = false;
    }

    public UUID getModeratorUuid() {
        return moderatorUuid;
    }

    public String getModeratorName() {
        return moderatorName;
    }

    public UUID getSuspectUuid() {
        return suspectUuid;
    }

    public String getSuspectName() {
        return suspectName;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getTotalSeconds() {
        return totalSeconds;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int decrementSeconds() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }
        return remainingSeconds;
    }

    public BukkitTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public boolean hasProvidedContact() {
        return hasProvidedContact;
    }

    public void setHasProvidedContact(boolean hasProvidedContact) {
        this.hasProvidedContact = hasProvidedContact;
    }

    public String getProvidedContact() {
        return providedContact;
    }

    public void setProvidedContact(String providedContact) {
        this.providedContact = providedContact;
    }

    public String getProvidedService() {
        return providedService;
    }

    public void setProvidedService(String providedService) {
        this.providedService = providedService;
    }

    public void stopTimer() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
            timerTask = null;
        }
        hideBossBar();
    }

    public void hideBossBar() {
        if (bossBar != null) {
            Player mod = Bukkit.getPlayer(moderatorUuid);
            if (mod != null && mod.isOnline()) {
                mod.hideBossBar(bossBar);
            }
            Player sus = Bukkit.getPlayer(suspectUuid);
            if (sus != null && sus.isOnline()) {
                sus.hideBossBar(bossBar);
            }
            bossBar = null;
        }
    }
}
