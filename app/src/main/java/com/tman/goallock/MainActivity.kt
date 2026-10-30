package com.tman.goallock

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        title = "GoalLock"
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val root = screen()

        // ----- status -----
        when {
            store.allGoalsDone() -> root.card(0x332E9E5B).text(
                "✅ All goals done — apps unlocked for today", 18f, true)
            store.bypassActive() -> root.card(0x33E08A1E).text(
                "🚨 Emergency unlock until ${fmtTime(store.bypassUntil)}", 18f, true)
            else -> root.card(0x33D64545).text(
                "🔒 Social & streaming apps are locked until today's goals are done", 18f, true)
        }

        renderSetup(root)

        // ----- goals -----
        root.text("Today's goals", 20f, true).setPadding(0, dp(18), 0, 0)
        val goals = store.goals()
        if (goals.isEmpty()) root.text("No goals yet — add one below.", color = GRAY)
        for (g in goals) renderGoal(root, g)

        root.button("＋ Add a goal") {
            startActivity(Intent(this, GoalEditActivity::class.java))
        }

        // ----- other -----
        root.text("More", 20f, true).setPadding(0, dp(18), 0, 0)
        root.button("🚫 Blocked apps (${store.blocked().size})") {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }
        root.button("📓 Journal") {
            startActivity(Intent(this, JournalActivity::class.java))
        }
        root.button("🕓 Goals reset daily at ${hourLabel(store.resetHour)}") { pickResetHour() }
    }

    private fun renderSetup(root: LinearLayout) {
        val needsBlocker = !isBlockerEnabled(this)
        val power = getSystemService(PowerManager::class.java)
        val needsBattery = !power.isIgnoringBatteryOptimizations(packageName)
        val needsNotif = Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            PackageManager.PERMISSION_GRANTED
        if (!needsBlocker && !needsBattery && !needsNotif) return

        val c = root.card()
        c.text("Finish setup", 17f, true)
        if (needsBlocker) {
            c.text("1. Turn on GoalLock in Accessibility so it can lock apps.\n" +
                "Samsung: Installed apps → GoalLock → On.\n" +
                "If the switch is greyed out: Settings → Apps → GoalLock → ⋮ (top right) → " +
                "Allow restricted settings, then try again.")
            c.button("Turn on app locking") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        if (needsBattery) {
            c.text("2. Let GoalLock run in the background so Samsung doesn't put it to sleep.")
            c.button("Allow background running") {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
        if (needsNotif) {
            c.text("3. Allow notifications so GoalLock can tell you when a goal's time is reached.")
            c.button("Allow notifications") {
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
            }
        }
    }

    private fun renderGoal(root: LinearLayout, g: Goal) {
        val c = root.card()
        val done = store.goalDone(g)
        val timeMet = store.timeMet(g)
        val mins = (store.progressMs(g.id) / 60_000L).toInt()

        c.text((if (done) "✅ " else "") + g.name, 17f, true)
        val step = when {
            done -> "Done for today"
            timeMet -> "Time reached — write your journal entry to finish"
            else -> "Spend ${g.minutes} min in the app, then write what you learned"
        }
        c.text("${appLabel(this, g.packageName)} · ${minOf(mins, g.minutes)}/${g.minutes} min\n$step",
            14f, color = if (done) GREEN else if (timeMet) ORANGE else null)

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = g.minutes
        bar.progress = minOf(mins, g.minutes)
        c.addView(bar)

        val r = c.row()
        if (!done && !timeMet) {
            r.button("▶ Open app") {
                if (!launchApp(this, g.packageName))
                    Toast.makeText(this, "That app isn't installed", Toast.LENGTH_SHORT).show()
            }
        }
        if (timeMet && !done) {
            r.button("✍ Write entry") {
                startActivity(Intent(this, JournalActivity::class.java)
                    .putExtra(JournalActivity.EXTRA_GOAL, g.id))
            }
        }
        r.button("Edit") {
            startActivity(Intent(this, GoalEditActivity::class.java)
                .putExtra(GoalEditActivity.EXTRA_GOAL, g.id))
        }
    }

    private fun pickResetHour() {
        val labels: Array<CharSequence> = (0..23).map { h ->
            val label: CharSequence = hourLabel(h)
            label
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Reset goals every day at…")
            .setItems(labels) { _, which ->
                store.resetHour = which
                render()
            }
            .show()
    }
}
