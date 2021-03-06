package com.zexfer.lufram

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.LifecycleObserver
import com.zexfer.lufram.Lufram.Companion.LUFRAM_PREFS
import com.zexfer.lufram.Lufram.Companion.PREF_CONFIG_INTERVAL_MILLIS
import com.zexfer.lufram.Lufram.Companion.PREF_CONFIG_RANDOMIZE_ORDER
import com.zexfer.lufram.Lufram.Companion.PREF_CONFIG_TYPE
import com.zexfer.lufram.Lufram.Companion.PREF_UPDATER_ID
import com.zexfer.lufram.Lufram.Companion.PREF_UPDATER_TIMESTAMP
import com.zexfer.lufram.Lufram.Companion.PREF_WALLPAPER_ID
import com.zexfer.lufram.Lufram.Companion.PREF_WAS_STOPPED
import com.zexfer.lufram.database.models.WallpaperCollection
import com.zexfer.lufram.database.tasks.DeleteWallpaperByIdTask
import com.zexfer.lufram.database.tasks.DeleteWallpaperTask
import com.zexfer.lufram.database.tasks.PutWallpaperTask

object LuframRepository : LifecycleObserver {

    val luframPrefs: SharedPreferences = Lufram.instance.getSharedPreferences(LUFRAM_PREFS, 0)

    fun preferredWallpaperId() =
        luframPrefs.getInt(PREF_WALLPAPER_ID, -1)

    val configMode: Int
        get() = luframPrefs.getInt(PREF_CONFIG_TYPE, CONFIG_PERIODIC)

    val updateIntervalMillis: Long
        get() = luframPrefs.getLong(PREF_CONFIG_INTERVAL_MILLIS, 0)

    val lastUpdateId: Int
        get() = luframPrefs.getInt(PREF_UPDATER_ID, -1)

    val lastUpdateTimestamp: Long
        get() = luframPrefs.getLong(PREF_UPDATER_TIMESTAMP, 0)

    val isRandomized: Boolean
        get() = (configMode == CONFIG_PERIODIC) && luframPrefs.getBoolean(
            PREF_CONFIG_RANDOMIZE_ORDER,
            false
        )

    fun deleteWallpaper(wallpaper: WallpaperCollection) {
        if (preferredWallpaperId() == wallpaper.rowId)
            stopWallpaper()
        DeleteWallpaperTask().execute(wallpaper)
    }

    fun deleteWallpaper(id: Int) {
        if (preferredWallpaperId() == id)
            stopWallpaper()
        DeleteWallpaperByIdTask().execute(id)
    }

    fun putWallpaper(wallpaper: WallpaperCollection) {
        PutWallpaperTask().execute(wallpaper)
    }

    /**
     * Commit a periodic configuration.
     *
     * @param config - the settings to save
     * @param modeSwitch - whether to activate these settings if not in periodic
     *      mode already.
     */
    fun commitConfig(config: PeriodicConfig, modeSwitch: Boolean = true): Boolean {
        val isModeDiff = configMode != CONFIG_PERIODIC
        val isIntervalDiff = updateIntervalMillis != config.intervalMillis

        luframPrefs.edit().apply {
            if (modeSwitch && isModeDiff) {
                putInt(PREF_CONFIG_TYPE, CONFIG_PERIODIC)
            }

            putLong(PREF_CONFIG_INTERVAL_MILLIS, config.intervalMillis)
            putBoolean(PREF_CONFIG_RANDOMIZE_ORDER, config.randomizeOrder)
        }.apply()

        if ((!isModeDiff && isIntervalDiff) || (modeSwitch && isModeDiff)) {
            restartWallpaper() // changing randomize order doesn't need a restart!
            return true
        }

        return isIntervalDiff
    }

    /**
     * Commit a dynamic configuration.
     *
     * @param config - settings to save
     * @param modeSwitch - whether to activate these settings if not already in
     *      dynamic mode.
     */
    fun commitConfig(config: DynamicConfig, modeSwitch: Boolean = true): Boolean {
        val isModeDiff = configMode != CONFIG_DYNAMIC

        if (modeSwitch) {
            luframPrefs.edit().apply {
                putInt(PREF_CONFIG_TYPE, CONFIG_DYNAMIC)
            }.apply()
        }

        if (isModeDiff && modeSwitch) {
            restartWallpaper()
            return true
        }

        return false
    }

    /**
     * Prevents all future wallpaper updates
     *
     * @param writeUpdaterId - whether to update the updaterId in shared
     *      preferences. If false, it is expected you will do that on
     *      your own.
     */
    fun stopWallpaper(writeUpdaterId: Boolean = true): Int {
        val oldUpdaterId = luframPrefs.getInt(PREF_UPDATER_ID, 0)

        if (writeUpdaterId) {
            luframPrefs.edit().apply {
                putInt(PREF_UPDATER_ID, oldUpdaterId + 1)
                putInt(PREF_WALLPAPER_ID, -1)
                putBoolean(PREF_WAS_STOPPED, true)
            }.apply()
        }

        // Also prevent updater beforehand!
        (Lufram.instance.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(
                PendingIntent.getBroadcast(
                    Lufram.instance,
                    oldUpdaterId,
                    Intent(
                        Lufram.instance,
                        WallpaperUpdateReceiver::class.java
                    ),
                    0
                )
            )

        return oldUpdaterId
    }

    fun restartWallpaper() {
        if (preferredWallpaperId() != -1)
            WallpaperUpdateController.targetId = preferredWallpaperId()
    }

    fun safeStopWallpaper(updaterId: Int, writeUpdaterId: Boolean = true) {
        if (preferredWallpaperId() == updaterId)
            stopWallpaper(writeUpdaterId)
    }

    /**
     * Checks if the currently preferred (if it exists) is being updated
     * or if the updater is dead.
     *
     * @return whether wallpaper updates are still running. If wallpaper,
     *  was stopped, it returns true (wallpaper doesn't exist)
     */
    fun isUpdaterAlive() =
        luframPrefs.getBoolean(PREF_WAS_STOPPED, true) ||
                PendingIntent.getBroadcast(
                    Lufram.instance,
                    luframPrefs.getInt(PREF_UPDATER_ID, 0),
                    Intent(
                        Lufram.instance,
                        WallpaperUpdateReceiver::class.java
                    ),
                    PendingIntent.FLAG_NO_CREATE
                ) != null

    interface Config

    data class PeriodicConfig(
        var intervalMillis: Long,
        var randomizeOrder: Boolean
    ) : Config {
        val hr: Int
            get() =
                (intervalMillis / 3600000).toInt()

        val min: Int
            get() =
                ((intervalMillis % 3600000) / 60000).toInt()

        fun formattedIntervalString(): String =
            String.format("%02d : %02d", hr, min)

        companion object {
            fun formattedIntervalString(hr: Int, min: Int): String =
                String.format("%02d : %02d", hr, min)
        }
    }

    data class DynamicConfig(
        // We are not using these fields
        var dayRange: Int,
        var timeZoneAdjustEnabled: Boolean
    ) : Config

    val CONFIG_PERIODIC = 0xff1
    val CONFIG_DYNAMIC = 0xff2
}