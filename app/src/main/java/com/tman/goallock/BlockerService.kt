package com.tman.goallock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * The heart of GoalLock. Android tells this service whenever a new screen (Activity) opens.
 *  - If it's a blocked app and goals aren't done -> send the user home and show the lock screen.
 *  - If it's a goal app -> count the minutes (only while the screen is on and unlocked).
 */
class BlockerService : AccessibilityService() {

    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var lastTick = 0L
    private var lastBlockAt = 0L
    private val notified = mutableSetOf<String>()
    private val activityCache = HashMap<String, Boolean>()

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = Store(this)
        lastTick = SystemClock.elapsedRealtime()
        createChannel()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::store.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Only react to real app screens. Keyboards, the notification shade,
        // popups and floating windows are ignored so they don't confuse the timer.
        if (!isActivity(pkg, cls)) return

        accumulate()          // credit time to the app we are leaving
        currentPkg = pkg
        checkBlock(pkg)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun isActivity(pkg: String, cls: String): Boolean {
        val key = "$pkg/$cls"
        activityCache[key]?.let { return it }
        val result = try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
        activityCache[key] = result
        return result
    }

    private fun onTick() {
        accumulate()
        currentPkg?.let { checkBlock(it) }   // re-locks when an emergency unlock runs out
    }

    private fun accumulate() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastTick).coerceIn(0L, MAX_CREDIT_MS)
        lastTick = now
        val pkg = currentPkg ?: return

        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive || keyguard.isKeyguardLocked) return   // screen off = no credit

        for (g in store.goals()) {
            if (g.packageName != pkg) continue
            store.addProgress(g.id, elapsed)
            if (store.timeMet(g) && !store.journalDoneToday(g.id)) notifyGoalReady(g)
        }
    }

    private fun checkBlock(pkg: String) {
        if (pkg == packageName) return
        if (pkg !in store.blocked()) return
        if (pkg in store.goalPackages()) return      // never block a goal app
        if (!store.isLocked()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
        )
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Goal reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun notifyGoalReady(g: Goal) {
        val key = store.today() + "|" + g.id
        if (!notified.add(key)) return
        val intent = Intent(this, JournalActivity::class.java)
            .putExtra(JournalActivity.EXTRA_GOAL, g.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this, g.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("${g.name}: time reached ✅")
            .setContentText("Tap to write what you learned and finish this goal.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(g.id.hashCode(), n)
    }

    companion object {
        private const val CHANNEL = "goals"
        private const val TICK_MS = 5_000L
        private const val MAX_CREDIT_MS = 15_000L
    }
}
