package ca.rtp.satisfaction

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log

/** Required so the app can be made device owner:  adb shell dpm set-device-owner ca.rtp.satisfaction/.KioskAdminReceiver */
class KioskAdminReceiver : DeviceAdminReceiver()

/**
 * Kiosk lockdown.
 *  - Device owner: full lock task mode (no Home/Recents/notifications/power menu/Settings), survives reboot.
 *  - Not device owner: optional screen pinning (user can still escape with Back+Recents).
 */
object DeviceLock {
    private const val TAG = "DeviceLock"

    private val restrictions = listOf(
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_CREATE_WINDOWS,
        UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS,
    )

    fun admin(ctx: Context) = ComponentName(ctx, KioskAdminReceiver::class.java)
    fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    fun isDeviceOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun isLocked(a: Activity): Boolean =
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState !=
                ActivityManager.LOCK_TASK_MODE_NONE

    /** Applies the lockdown policies (device owner only) and enters lock task / pinning as configured. */
    fun engage(a: Activity, cfg: KioskConfig) {
        if (isDeviceOwner(a) && cfg.lockDevice) {
            applyOwnerPolicies(a, cfg)
            if (!isLocked(a)) tryStart(a)
        } else if (cfg.screenPinning) {
            if (!isLocked(a)) tryStart(a)
        }
    }

    private fun tryStart(a: Activity) {
        try { a.startLockTask() } catch (e: Exception) { Log.w(TAG, "startLockTask failed", e) }
    }

    /** Temporarily leave kiosk mode (admin). Policies stay; the next engage() re-locks. */
    fun release(a: Activity) {
        try { if (isLocked(a)) a.stopLockTask() } catch (e: Exception) { Log.w(TAG, "stopLockTask failed", e) }
        if (isDeviceOwner(a)) {
            val d = dpm(a); val c = admin(a)
            try { d.setStatusBarDisabled(c, false) } catch (_: Exception) {}
        }
    }

    private fun applyOwnerPolicies(ctx: Context, cfg: KioskConfig) {
        val d = dpm(ctx); val c = admin(ctx)
        try {
            d.setLockTaskPackages(c, arrayOf(ctx.packageName))
            if (Build.VERSION.SDK_INT >= 28) {
                // No Home, Recents, notifications, status bar info, or power-button menu while locked.
                d.setLockTaskFeatures(c, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            d.setStatusBarDisabled(c, true)
            d.setKeyguardDisabled(c, true)       // power button: screen off/on goes straight back to the app
            restrictions.forEach { d.addUserRestriction(c, it) }
            if (cfg.keepAwake) {
                val plugged = BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB or
                        BatteryManager.BATTERY_PLUGGED_WIRELESS
                d.setGlobalSetting(c, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, plugged.toString())
            }
            // Become the permanent Home screen so the kiosk comes back after a reboot.
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            d.addPersistentPreferredActivity(c, home, ComponentName(ctx, MainActivity::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Applying device-owner policies failed", e)
        }
    }

    /** Admin "decommission": removes every policy and gives up device ownership. */
    fun removeDeviceOwner(a: Activity) {
        release(a)
        if (!isDeviceOwner(a)) return
        val d = dpm(a); val c = admin(a)
        try {
            restrictions.forEach { d.clearUserRestriction(c, it) }
            d.setKeyguardDisabled(c, false)
            d.setStatusBarDisabled(c, false)
            d.setGlobalSetting(c, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
            d.clearPackagePersistentPreferredActivities(c, a.packageName)
            d.setLockTaskPackages(c, arrayOf())
            @Suppress("DEPRECATION")
            d.clearDeviceOwnerApp(a.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Removing device owner failed", e)
        }
    }

    fun describe(a: Activity, cfg: KioskConfig): String = when {
        isDeviceOwner(a) && cfg.lockDevice -> if (isLocked(a)) "Full lockdown (device owner)" else "Device owner - currently unlocked"
        isDeviceOwner(a) -> "Device owner, lock_device = false"
        cfg.screenPinning -> if (isLocked(a)) "Screen pinning" else "Screen pinning (not active)"
        else -> "Not locked (app is not device owner - see README)"
    }
}
