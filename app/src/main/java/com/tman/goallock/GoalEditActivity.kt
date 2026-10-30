package com.tman.goallock

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class GoalEditActivity : Activity() {

    private lateinit var store: Store
    private var chosenPkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val existing = store.goal(intent.getStringExtra(EXTRA_GOAL))
        title = if (existing == null) "New goal" else "Edit goal"
        chosenPkg = existing?.packageName

        val root = screen()

        root.text("Goal name", bold = true)
        val name = root.input("e.g. Read a Book of Mormon chapter", existing?.name ?: "")

        root.text("App used for this goal", bold = true).setPadding(0, dp(14), 0, 0)
        lateinit var appBtn: Button
        appBtn = root.button(chosenPkg?.let { appLabel(this, it) } ?: "Choose app…") {
            pickApp { pkg ->
                chosenPkg = pkg
                appBtn.text = appLabel(this, pkg)
            }
        }

        root.text("Minutes needed in the app", bold = true).setPadding(0, dp(14), 0, 0)
        val minutes = root.input("10", existing?.minutes?.toString() ?: "10", number = true)

        root.text("Journal question (answered after the time is reached)", bold = true)
            .setPadding(0, dp(14), 0, 0)
        val prompt = root.input("What did you learn?",
            existing?.prompt ?: "What did you learn today?", multiline = true)

        root.button("💾 Save") {
            val n = name.text.toString().trim()
            val m = minutes.text.toString().toIntOrNull() ?: 0
            val pkg = chosenPkg
            when {
                n.isEmpty() -> toast("Give the goal a name")
                pkg == null -> toast("Choose an app")
                m !in 1..240 -> toast("Minutes must be between 1 and 240")
                else -> {
                    store.upsertGoal(Goal(existing?.id ?: store.newId(), n, pkg, m,
                        prompt.text.toString().trim().ifEmpty { "What did you learn today?" }))
                    // A goal app must never be blocked, or you could lock yourself out.
                    store.setBlocked(store.blocked() - pkg)
                    finish()
                }
            }
        }

        if (existing != null) {
            root.button("🗑 Delete goal") {
                AlertDialog.Builder(this)
                    .setMessage("Delete \"${existing.name}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        store.deleteGoal(existing.id)
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun pickApp(onPicked: (String) -> Unit) {
        // Suggested study apps first, then everything else.
        val suggested = listOf(PKG_GOSPEL_LIBRARY, PKG_ANKI, PKG_TAKOBOTO)
        val apps = launchableApps(this).sortedBy { app ->
            val i = suggested.indexOf(app.pkg)
            if (i >= 0) i else suggested.size
        }
        val labels: Array<CharSequence> = apps.map { app ->
            val label: CharSequence = (if (app.pkg in suggested) "⭐ " else "") + app.label
            label
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose the app")
            .setItems(labels) { _, which -> onPicked(apps[which].pkg) }
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_GOAL = "goal"
    }
}
