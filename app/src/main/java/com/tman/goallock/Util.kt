package com.tman.goallock

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------- apps ----------------

data class AppInfo(val pkg: String, val label: String)

fun launchableApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .filter { it.pkg != ctx.packageName }
        .sortedBy { it.label.lowercase() }
}

fun appLabel(ctx: Context, pkg: String): String {
    val pm = ctx.packageManager
    return try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        when (pkg) {
            PKG_GOSPEL_LIBRARY -> "Gospel Library (not installed)"
            PKG_ANKI -> "AnkiDroid (not installed)"
            PKG_TAKOBOTO -> "Takoboto (not installed)"
            else -> "$pkg (not installed)"
        }
    }
}

fun launchApp(ctx: Context, pkg: String): Boolean {
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
    return true
}

fun goHome(ctx: Context) {
    ctx.startActivity(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun isBlockerEnabled(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val me = ComponentName(ctx, BlockerService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

fun fmtTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFmt)

fun fmtDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFmt)

fun hourLabel(h: Int): String = when {
    h == 0 -> "12:00 AM (midnight)"
    h < 12 -> "$h:00 AM"
    h == 12 -> "12:00 PM (noon)"
    else -> "${h - 12}:00 PM"
}

// ---------------- tiny UI helpers (no XML layouts needed) ----------------

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

val GREEN = 0xFF2E9E5B.toInt()
val RED = 0xFFD64545.toInt()
val ORANGE = 0xFFE08A1E.toInt()
val GRAY = 0xFF8A8A8A.toInt()

/** Sets a scrollable vertical screen as the content view and returns its column. */
fun Activity.screen(): LinearLayout {
    val scroll = ScrollView(this)
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(32))
    }
    scroll.addView(root)
    setContentView(scroll)
    return root
}

private fun LinearLayout.childParams(): LinearLayout.LayoutParams =
    if (orientation == LinearLayout.HORIZONTAL)
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = context.dp(6)
        }
    else
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

fun LinearLayout.text(
    t: CharSequence,
    size: Float = 15f,
    bold: Boolean = false,
    color: Int? = null,
): TextView {
    val tv = TextView(context)
    tv.text = t
    tv.textSize = size
    if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
    if (color != null) tv.setTextColor(color)
    tv.setPadding(0, context.dp(3), 0, context.dp(3))
    addView(tv, childParams())
    return tv
}

fun LinearLayout.button(t: String, onClick: () -> Unit): Button {
    val b = Button(context)
    b.text = t
    b.isAllCaps = false
    b.setOnClickListener { onClick() }
    val lp = childParams()
    lp.topMargin = context.dp(6)
    addView(b, lp)
    return b
}

fun LinearLayout.card(tint: Int = 0x1F808080): LinearLayout {
    val c = LinearLayout(context)
    c.orientation = LinearLayout.VERTICAL
    val p = context.dp(16)
    c.setPadding(p, context.dp(12), p, context.dp(14))
    c.background = GradientDrawable().apply {
        cornerRadius = context.dp(20).toFloat()
        setColor(tint)
    }
    val lp = childParams()
    lp.topMargin = context.dp(12)
    addView(c, lp)
    return c
}

fun LinearLayout.row(): LinearLayout {
    val r = LinearLayout(context)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    addView(r, childParams())
    return r
}

fun LinearLayout.input(
    hint: String,
    value: String = "",
    multiline: Boolean = false,
    number: Boolean = false,
): EditText {
    val e = EditText(context)
    e.hint = hint
    e.setText(value)
    e.inputType = when {
        number -> InputType.TYPE_CLASS_NUMBER
        multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    if (multiline) {
        e.minLines = 4
        e.gravity = Gravity.TOP or Gravity.START
    }
    addView(e, childParams())
    return e
}
