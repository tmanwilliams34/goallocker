package com.tman.goallock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity

/** Full-screen "this app is locked" page shown when you open a blocked app. */
class BlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val store = Store(this)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val root = screen()
        root.setPadding(dp(24), dp(48), dp(24), dp(32))

        root.text("🔒", 56f).gravity = Gravity.CENTER_HORIZONTAL
        root.text("${appLabel(this, pkg).removeSuffix(" (not installed)")} is locked", 24f, true)
            .gravity = Gravity.CENTER_HORIZONTAL
        root.text("Finish today's goals to unlock it.", 16f, color = GRAY)
            .gravity = Gravity.CENTER_HORIZONTAL

        val c = root.card()
        for (g in store.goals()) {
            val mins = minOf(store.progressMs(g.id) / 60_000L, g.minutes.toLong())
            val state = when {
                store.goalDone(g) -> "✅"
                store.timeMet(g) -> "✍ journal needed"
                else -> "$mins/${g.minutes} min"
            }
            c.text("• ${g.name} — $state", 16f)
        }

        root.button("Do my goals") {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        root.button("🚨 Emergency unlock ($BYPASS_MINUTES min)") {
            store.startBypass()
            finish()
            if (pkg.isNotEmpty()) launchApp(this, pkg)
        }
        root.button("Go home") {
            goHome(this)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome(this)
        finish()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
