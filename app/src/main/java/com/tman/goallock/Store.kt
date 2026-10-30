package com.tman.goallock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.UUID

const val PKG_GOSPEL_LIBRARY = "org.lds.ldssa"
const val PKG_ANKI = "com.ichi2.anki"
const val PKG_TAKOBOTO = "jp.takoboto"

const val TYPE_GOAL = "goal"
const val TYPE_BYPASS = "bypass"

const val BYPASS_MINUTES = 15
const val MIN_JOURNAL_CHARS = 15

/** Social media + video/streaming apps that are blocked by default (only matters if installed). */
val DEFAULT_BLOCKED = listOf(
    "com.instagram.android", "com.instagram.barcelona",          // Instagram, Threads
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",      // TikTok
    "com.facebook.katana", "com.facebook.lite",                  // Facebook
    "com.twitter.android",                                       // X
    "com.snapchat.android", "com.reddit.frontpage", "com.pinterest",
    "com.google.android.youtube",                                // YouTube
    "com.netflix.mediaclient", "com.disney.disneyplus", "com.hulu.plus",
    "com.amazon.avod.thirdpartyclient", "com.wbd.stream",        // Prime Video, Max
    "com.crunchyroll.crunchyroid", "tv.twitch.android.app",
)

data class Goal(
    val id: String,
    val name: String,
    val packageName: String,
    val minutes: Int,
    val prompt: String,
)

data class JournalEntry(
    val time: Long,
    val day: String,
    val goalId: String,
    val goalName: String,
    val text: String,
    val type: String,
)

/** All app data lives in one private SharedPreferences file on the phone. Nothing leaves the device. */
class Store(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("goallock", Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean("initialized", false)) seedDefaults()
    }

    private fun seedDefaults() {
        saveGoals(
            listOf(
                Goal(newId(), "Book of Mormon chapter", PKG_GOSPEL_LIBRARY, 10,
                    "What chapter did you read, and what stood out to you?"),
                Goal(newId(), "Study 15 Japanese words", PKG_ANKI, 10,
                    "Write 3 words you studied today and what they mean."),
            )
        )
        prefs.edit()
            .putStringSet("blocked", HashSet(DEFAULT_BLOCKED))
            .putBoolean("initialized", true)
            .apply()
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------- day handling ----------
    var resetHour: Int
        get() = prefs.getInt("resetHour", 4)
        set(value) = prefs.edit().putInt("resetHour", value).apply()

    /** "Today" starts at the reset hour (default 4 AM), so late-night use counts as the previous day. */
    fun today(): String =
        LocalDateTime.now().minusHours(resetHour.toLong()).toLocalDate().toString()

    // ---------- goals ----------
    fun goals(): List<Goal> {
        val arr = JSONArray(prefs.getString("goals", null) ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Goal(
                id = o.getString("id"),
                name = o.getString("name"),
                packageName = o.getString("pkg"),
                minutes = o.getInt("minutes"),
                prompt = o.optString("prompt", ""),
            )
        }
    }

    fun goal(id: String?): Goal? = if (id == null) null else goals().firstOrNull { it.id == id }

    fun saveGoals(list: List<Goal>) {
        val arr = JSONArray()
        for (g in list) {
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("pkg", g.packageName)
                    .put("minutes", g.minutes)
                    .put("prompt", g.prompt)
            )
        }
        prefs.edit().putString("goals", arr.toString()).apply()
    }

    fun upsertGoal(goal: Goal) {
        val list = goals().toMutableList()
        val i = list.indexOfFirst { it.id == goal.id }
        if (i >= 0) list[i] = goal else list.add(goal)
        saveGoals(list)
    }

    fun deleteGoal(id: String) = saveGoals(goals().filter { it.id != id })

    fun goalPackages(): Set<String> = goals().map { it.packageName }.toSet()

    // ---------- progress (time spent in goal apps) ----------
    fun progressMs(goalId: String, day: String = today()): Long =
        prefs.getLong("p_${day}_$goalId", 0L)

    fun addProgress(goalId: String, ms: Long) {
        val day = today()
        prefs.edit().putLong("p_${day}_$goalId", progressMs(goalId, day) + ms).apply()
    }

    fun timeMet(g: Goal): Boolean = progressMs(g.id) >= g.minutes * 60_000L

    fun goalDone(g: Goal): Boolean = timeMet(g) && journalDoneToday(g.id)

    fun allGoalsDone(): Boolean = goals().all { goalDone(it) }

    // ---------- emergency bypass ----------
    var bypassUntil: Long
        get() = prefs.getLong("bypassUntil", 0L)
        set(value) = prefs.edit().putLong("bypassUntil", value).apply()

    fun bypassActive(): Boolean = System.currentTimeMillis() < bypassUntil

    fun startBypass() {
        bypassUntil = System.currentTimeMillis() + BYPASS_MINUTES * 60_000L
        addJournal(null, "Emergency unlock ($BYPASS_MINUTES min)", TYPE_BYPASS)
    }

    fun isLocked(): Boolean = !allGoalsDone() && !bypassActive()

    // ---------- blocked apps ----------
    fun blocked(): Set<String> = prefs.getStringSet("blocked", null)?.toSet() ?: emptySet()

    fun setBlocked(pkgs: Set<String>) {
        prefs.edit().putStringSet("blocked", HashSet(pkgs)).apply()
    }

    // ---------- journal ----------
    fun journal(): List<JournalEntry> {
        val arr = JSONArray(prefs.getString("journal", null) ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            JournalEntry(
                time = o.getLong("time"),
                day = o.getString("day"),
                goalId = o.optString("goalId", ""),
                goalName = o.optString("goalName", ""),
                text = o.getString("text"),
                type = o.optString("type", TYPE_GOAL),
            )
        }.sortedByDescending { it.time }
    }

    fun addJournal(goal: Goal?, text: String, type: String = TYPE_GOAL) {
        val arr = JSONArray(prefs.getString("journal", null) ?: "[]")
        arr.put(
            JSONObject()
                .put("time", System.currentTimeMillis())
                .put("day", today())
                .put("goalId", goal?.id ?: "")
                .put("goalName", goal?.name ?: "")
                .put("text", text)
                .put("type", type)
        )
        prefs.edit().putString("journal", arr.toString()).apply()
    }

    fun journalDoneToday(goalId: String): Boolean {
        val day = today()
        return journal().any { it.day == day && it.goalId == goalId && it.type == TYPE_GOAL }
    }
}
