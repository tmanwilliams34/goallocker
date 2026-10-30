package com.tman.goallock

import android.app.Activity
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout

class BlockedAppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Blocked apps"
        val store = Store(this)
        val root = screen()

        root.text("Checked apps are locked until your goals are done. " +
            "Your goal apps, phone, and settings can't be blocked.", color = GRAY)

        val protectedPkgs = store.goalPackages() + setOf(
            "com.android.settings", "com.samsung.android.dialer",
            "com.google.android.dialer", "com.android.dialer",
        )
        val apps = launchableApps(this).filter { it.pkg !in protectedPkgs }
        val blocked = store.blocked()

        // Blocked apps on top so you can see them at a glance.
        val ordered = apps.filter { it.pkg in blocked } + apps.filter { it.pkg !in blocked }
        for (app in ordered) {
            val cb = CheckBox(this)
            cb.text = app.label
            cb.textSize = 16f
            cb.isChecked = app.pkg in blocked
            cb.setPadding(dp(8), dp(10), 0, dp(10))
            cb.setOnCheckedChangeListener { _, checked ->
                val now = store.blocked()
                store.setBlocked(if (checked) now + app.pkg else now - app.pkg)
            }
            root.addView(cb, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }
}
