package com.alisia.focuslock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val tickMs = 2000L
    private var lastForegroundPkg: String? = null
    private var lastEventQueryTime = 0L

    private var overlayView: View? = null
    private var lockedPkg: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val notif = NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("FocusLock is watching your limits")
            .setContentText("Keeping track of your screen time.")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 1, notif,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        lastEventQueryTime = System.currentTimeMillis() - 5000
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    private val ticker = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (_: Exception) {
                // Never let one bad tick kill the service.
            }
            handler.postDelayed(this, tickMs)
        }
    }

    private fun tick() {
        val fg = currentForegroundPackage()
        val rule = fg?.let { Prefs.ruleFor(this, it) }

        // If the locked app is no longer in front, drop the lock screen.
        if (overlayView != null && fg != lockedPkg) hideOverlay()

        if (fg == null || rule == null) return
        if (Prefs.isNoLimitToday(this, fg)) return

        Prefs.addUsage(this, fg, (tickMs / 1000).toInt())

        val day = Prefs.dayKey(this)
        val used = Prefs.getUsageSecs(this, day, fg)
        val allowed = rule.limitMin * 60 + Prefs.getExtraSecs(this, day, fg)
        val remaining = allowed - used

        if (remaining in 1..300 && !Prefs.isWarned(this, fg)) {
            Prefs.markWarned(this, fg)
            showWarning(appLabel(fg), remaining / 60 + 1)
        }

        if (remaining <= 0 && overlayView == null) {
            showLockScreen(rule, appLabel(fg))
        }
    }

    /** Figure out which app is currently on screen. */
    private fun currentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastEventQueryTime, now)
        lastEventQueryTime = now
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundPkg = event.packageName
            }
        }
        return lastForegroundPkg
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    // ---------- Notifications ----------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Background monitoring", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Time warnings", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun showWarning(label: String, minutesLeft: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$label: about $minutesLeft min left")
            .setContentText("Your limit is almost up. Wrap it up or plan your extension.")
            .setAutoCancel(true)
            .build()
        nm.notify(label.hashCode(), notif)
    }

    // ---------- Lock screen overlay ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showLockScreen(rule: Prefs.AppRule, label: String) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F2101418")) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(96), dp(28), dp(48))
        }
        root.addView(column)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )

        lockedPkg = rule.pkg
        overlayView = root
        wm.addView(root, params)
        renderReasonStage(column, rule, label)
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        lockedPkg = null
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    private fun subtitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#B0BEC5"))
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(0, dp(10), 0, dp(24))
    }

    private fun bigButton(text: String, bg: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(bg))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
        }

    /** Stage 1: "What are you doing?" preset reasons. */
    private fun renderReasonStage(column: LinearLayout, rule: Prefs.AppRule, label: String) {
        column.removeAllViews()
        column.addView(title("Time's up for $label"))
        column.addView(subtitle("What are you doing right now?"))

        val reasons = listOf(
            "Searching for something specific",
            "Replying to a message",
            "Checking one particular thing",
            "Taking a short break"
        )
        reasons.forEach { reason ->
            column.addView(bigButton(reason, "#37474F") {
                renderPuzzleStage(column, rule, label, liftLimit = false)
            })
        }

        column.addView(bigButton("No limit for this app today", "#6D4C41") {
            renderPuzzleStage(column, rule, label, liftLimit = true)
        })
        column.addView(bigButton("Close the app", "#263238") {
            goHome()
            hideOverlay()
        })
    }

    /** Stage 2: solve a quick puzzle before time is granted. */
    private fun renderPuzzleStage(
        column: LinearLayout,
        rule: Prefs.AppRule,
        label: String,
        liftLimit: Boolean,
        retry: Boolean = false
    ) {
        column.removeAllViews()
        val puzzle = PuzzleFactory.next()
        column.addView(title(if (retry) "Not quite — try this one" else "Quick puzzle first"))
        column.addView(
            subtitle(
                if (liftLimit) "Solve it to lift the limit for today."
                else "Solve it to add ${rule.extensionMin} more minutes."
            )
        )
        column.addView(TextView(this).apply {
            text = puzzle.question
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        })

        puzzle.options.forEach { option ->
            column.addView(bigButton(option.toString(), "#37474F") {
                if (option == puzzle.answer) {
                    if (liftLimit) {
                        Prefs.setNoLimitToday(this, rule.pkg)
                    } else {
                        Prefs.addExtra(this, rule.pkg, rule.extensionMin * 60)
                        Prefs.clearWarned(this, rule.pkg)
                    }
                    hideOverlay()
                } else {
                    renderPuzzleStage(column, rule, label, liftLimit, retry = true)
                }
            })
        }

        column.addView(bigButton("Never mind — close the app", "#263238") {
            goHome()
            hideOverlay()
        })
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERTS = "alerts"
    }
}
