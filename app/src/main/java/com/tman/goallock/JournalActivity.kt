package com.tman.goallock

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Write today's entry for a goal (when opened with a goal), and read past entries. */
class JournalActivity : Activity() {

    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        title = "Journal"
        render()
    }

    private fun render() {
        val root = screen()
        val goal = store.goal(intent.getStringExtra(EXTRA_GOAL))

        if (goal != null && !store.journalDoneToday(goal.id)) {
            val c = root.card()
            c.text(goal.name, 18f, true)
            if (!store.timeMet(goal)) {
                val mins = store.progressMs(goal.id) / 60_000L
                c.text("Spend ${goal.minutes} min in ${appLabel(this, goal.packageName)} first " +
                    "($mins so far). You can still write now and it'll count once the time is done.",
                    14f, color = ORANGE)
            }
            c.text(goal.prompt, 16f)
            val entry = c.input("Write what you learned…", multiline = true)
            c.button("Save entry") {
                val t = entry.text.toString().trim()
                if (t.length < MIN_JOURNAL_CHARS) {
                    Toast.makeText(this, "Write a little more (at least $MIN_JOURNAL_CHARS characters)",
                        Toast.LENGTH_SHORT).show()
                } else {
                    store.addJournal(goal, t)
                    val msg = when {
                        store.allGoalsDone() -> "🎉 All goals done — apps unlocked for today!"
                        store.goalDone(goal) -> "✅ Goal complete!"
                        else -> "Saved. Finish the time to complete the goal."
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    intent.removeExtra(EXTRA_GOAL)
                    render()
                }
            }
        }

        root.text("Past entries", 20f, true).setPadding(0, dp(18), 0, 0)
        val entries = store.journal()
        if (entries.isEmpty()) root.text("Nothing yet. Entries you write appear here.", color = GRAY)
        for (e in entries) {
            if (e.type == TYPE_BYPASS) {
                root.text("🚨 ${fmtDateTime(e.time)} — ${e.text}", 13f, color = GRAY)
                    .setPadding(dp(4), dp(10), 0, 0)
            } else {
                val c = root.card()
                c.text("${fmtDateTime(e.time)} · ${e.goalName}", 13f, color = GRAY)
                c.text(e.text, 16f)
            }
        }
    }

    companion object {
        const val EXTRA_GOAL = "goal"
    }
}
