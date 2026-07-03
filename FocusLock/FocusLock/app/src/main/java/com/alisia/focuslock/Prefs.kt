package com.alisia.focuslock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * All app data lives here, stored on the phone itself.
 * - rules: which apps are limited, their daily limit and per-extension minutes
 * - usage: seconds spent per app per "day" (a day starts at the custom reset time)
 * - extra: extension seconds granted today per app
 * - warned / noLimit: per-day flags
 */
object Prefs {

    data class AppRule(val pkg: String, val limitMin: Int, val extensionMin: Int)

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("focuslock", Context.MODE_PRIVATE)

    // ---------- Rules ----------

    fun getRules(c: Context): List<AppRule> {
        val arr = JSONArray(sp(c).getString("rules", "[]") ?: "[]")
        val out = mutableListOf<AppRule>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(AppRule(o.getString("pkg"), o.getInt("limit"), o.getInt("ext")))
        }
        return out
    }

    fun saveRules(c: Context, rules: List<AppRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().put("pkg", r.pkg).put("limit", r.limitMin).put("ext", r.extensionMin))
        }
        sp(c).edit().putString("rules", arr.toString()).apply()
    }

    fun ruleFor(c: Context, pkg: String): AppRule? = getRules(c).find { it.pkg == pkg }

    // ---------- Daily reset time ----------

    fun getResetHour(c: Context): Int = sp(c).getInt("resetHour", 4)
    fun getResetMinute(c: Context): Int = sp(c).getInt("resetMinute", 0)

    fun setResetTime(c: Context, hour: Int, minute: Int) {
        sp(c).edit().putInt("resetHour", hour).putInt("resetMinute", minute).apply()
    }

    /** The date that "today" belongs to, given the custom reset time. */
    fun currentDayDate(c: Context): LocalDate {
        val now = LocalDateTime.now()
        val cutoff = now.toLocalDate().atTime(getResetHour(c), getResetMinute(c))
        return if (now.isBefore(cutoff)) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    fun dayKey(c: Context): String = currentDayDate(c).toString()

    // ---------- Usage (seconds per day per app) ----------

    private fun readMap(c: Context, key: String): JSONObject =
        JSONObject(sp(c).getString(key, "{}") ?: "{}")

    private fun writeMap(c: Context, key: String, obj: JSONObject) {
        sp(c).edit().putString(key, obj.toString()).apply()
    }

    fun addUsage(c: Context, pkg: String, seconds: Int) {
        val all = readMap(c, "usage")
        val day = dayKey(c)
        val dayObj = all.optJSONObject(day) ?: JSONObject()
        dayObj.put(pkg, dayObj.optInt(pkg, 0) + seconds)
        all.put(day, dayObj)
        pruneOldDays(all)
        writeMap(c, "usage", all)
    }

    fun getUsageSecs(c: Context, day: String, pkg: String): Int =
        readMap(c, "usage").optJSONObject(day)?.optInt(pkg, 0) ?: 0

    private fun pruneOldDays(all: JSONObject) {
        val cutoff = LocalDate.now().minusDays(14).toString()
        val stale = mutableListOf<String>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k < cutoff) stale.add(k)
        }
        stale.forEach { all.remove(it) }
    }

    // ---------- Extensions granted (seconds per day per app) ----------

    fun addExtra(c: Context, pkg: String, seconds: Int) {
        val all = readMap(c, "extra")
        val day = dayKey(c)
        val dayObj = all.optJSONObject(day) ?: JSONObject()
        dayObj.put(pkg, dayObj.optInt(pkg, 0) + seconds)
        all.put(day, dayObj)
        pruneOldDays(all)
        writeMap(c, "extra", all)
    }

    fun getExtraSecs(c: Context, day: String, pkg: String): Int =
        readMap(c, "extra").optJSONObject(day)?.optInt(pkg, 0) ?: 0

    // ---------- Per-day flags ----------

    private fun flagKey(c: Context, pkg: String) = "${dayKey(c)}|$pkg"

    private fun getSet(c: Context, key: String): MutableSet<String> =
        (sp(c).getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()

    fun markWarned(c: Context, pkg: String) {
        val s = getSet(c, "warned"); s.add(flagKey(c, pkg))
        sp(c).edit().putStringSet("warned", s).apply()
    }

    fun clearWarned(c: Context, pkg: String) {
        val s = getSet(c, "warned"); s.remove(flagKey(c, pkg))
        sp(c).edit().putStringSet("warned", s).apply()
    }

    fun isWarned(c: Context, pkg: String): Boolean =
        getSet(c, "warned").contains(flagKey(c, pkg))

    fun setNoLimitToday(c: Context, pkg: String) {
        val s = getSet(c, "noLimit"); s.add(flagKey(c, pkg))
        sp(c).edit().putStringSet("noLimit", s).apply()
    }

    fun isNoLimitToday(c: Context, pkg: String): Boolean =
        getSet(c, "noLimit").contains(flagKey(c, pkg))
}
