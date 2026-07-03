package com.alisia.focuslock

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Permissions.allGranted(this)) {
            ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
        }
    }
}

private data class InstalledApp(val pkg: String, val label: String)

private fun launchableApps(c: Context): List<InstalledApp> {
    val pm = c.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .filter { it.pkg != c.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun appLabel(c: Context, pkg: String): String = try {
    val pm = c.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    pkg
}

@Composable
private fun App() {
    val lifecycleOwner = LocalLifecycleOwner.current
    var screen by remember { mutableStateOf("home") }
    var refresh by remember { mutableIntStateOf(0) }

    // Re-check permissions & data every time the user comes back to this screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (screen) {
        "home" -> HomeScreen(
            refresh = refresh,
            onRefresh = { refresh++ },
            onAddApp = { screen = "picker" },
            onStats = { screen = "stats" }
        )
        "picker" -> PickerScreen(onDone = { refresh++; screen = "home" })
        "stats" -> StatsScreen(onBack = { screen = "home" })
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeScreen(refresh: Int, onRefresh: () -> Unit, onAddApp: () -> Unit, onStats: () -> Unit) {
    val ctx = LocalContext.current
    val rules = remember(refresh) { Prefs.getRules(ctx) }
    val hasUsage = remember(refresh) { Permissions.hasUsageAccess(ctx) }
    val hasOverlay = remember(refresh) { Permissions.canDrawOverlays(ctx) }
    var editing by remember { mutableStateOf<Prefs.AppRule?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text("FocusLock", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Your screen time, on your terms.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (!hasUsage) {
            PermissionCard(
                title = "Step 1: Allow usage access",
                body = "This lets FocusLock see which app is on screen so it can count your time."
            ) {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        if (!hasOverlay) {
            PermissionCard(
                title = if (hasUsage) "Step 2: Allow display over other apps" else "Step 2 (after step 1): Display over other apps",
                body = "This lets FocusLock show the lock screen on top of an app when time runs out."
            ) {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
            }
        }

        if (hasUsage && hasOverlay) {
            ResetTimeRow(refresh, onRefresh)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (rules.isEmpty()) {
                Text(
                    "No limited apps yet. Tap “Add app” to pick your first one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rules, key = { it.pkg }) { rule ->
                    RuleCard(rule = rule, onEdit = { editing = rule }, onRemove = {
                        Prefs.saveRules(ctx, rules.filter { it.pkg != rule.pkg })
                        onRefresh()
                    })
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAddApp, modifier = Modifier.weight(1f)) { Text("Add app") }
                OutlinedButton(onClick = onStats, modifier = Modifier.weight(1f)) { Text("Stats") }
            }
        }
    }

    editing?.let { rule ->
        EditRuleDialog(
            rule = rule,
            onDismiss = { editing = null },
            onSave = { updated ->
                Prefs.saveRules(ctx, Prefs.getRules(ctx).map { if (it.pkg == updated.pkg) updated else it })
                editing = null
                onRefresh()
            }
        )
    }
}

@Composable
private fun PermissionCard(title: String, body: String, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text("Open settings") }
        }
    }
}

@Composable
private fun ResetTimeRow(refresh: Int, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val hour = remember(refresh) { Prefs.getResetHour(ctx) }
    val minute = remember(refresh) { Prefs.getResetMinute(ctx) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(ctx, { _, h, m ->
                    Prefs.setResetTime(ctx, h, m)
                    onRefresh()
                }, hour, minute, true).show()
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Daily reset time", fontWeight = FontWeight.SemiBold)
            Text(
                "Timers start fresh at this time each day.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(String.format("%02d:%02d", hour, minute), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RuleCard(rule: Prefs.AppRule, onEdit: () -> Unit, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val day = Prefs.dayKey(ctx)
    val usedMin = Prefs.getUsageSecs(ctx, day, rule.pkg) / 60
    val extraMin = Prefs.getExtraSecs(ctx, day, rule.pkg) / 60
    val noLimit = Prefs.isNoLimitToday(ctx, rule.pkg)

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(appLabel(ctx, rule.pkg), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                when {
                    noLimit -> "No limit today (you lifted it)"
                    extraMin > 0 -> "Used $usedMin of ${rule.limitMin} min (+$extraMin extra) today"
                    else -> "Used $usedMin of ${rule.limitMin} min today"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Each extension adds ${rule.extensionMin} min",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun EditRuleDialog(rule: Prefs.AppRule, onDismiss: () -> Unit, onSave: (Prefs.AppRule) -> Unit) {
    val ctx = LocalContext.current
    var limit by remember { mutableIntStateOf(rule.limitMin) }
    var ext by remember { mutableIntStateOf(rule.extensionMin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appLabel(ctx, rule.pkg)) },
        text = {
            Column {
                Stepper("Daily limit", limit, "min", step = 5, min = 5, max = 600) { limit = it }
                Spacer(modifier = Modifier.height(12.dp))
                Stepper("Extension adds", ext, "min", step = 1, min = 1, max = 60) { ext = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rule.copy(limitMin = limit, extensionMin = ext)) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun Stepper(label: String, value: Int, unit: String, step: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("−") }
            Text(
                "$value $unit",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = { onChange((value + step).coerceAtMost(max)) }) { Text("+") }
        }
    }
}

// ---------------------------------------------------------------- Picker

@Composable
private fun PickerScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val existing = remember { Prefs.getRules(ctx).map { it.pkg }.toSet() }
    val apps = remember { launchableApps(ctx).filter { it.pkg !in existing } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text("Pick an app to limit", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "It starts with a 30 min limit and 5 min extensions — you can change both right after.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(apps, key = { it.pkg }) { app ->
                Text(
                    app.label,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Prefs.saveRules(
                                ctx,
                                Prefs.getRules(ctx) + Prefs.AppRule(app.pkg, limitMin = 30, extensionMin = 5)
                            )
                            onDone()
                        }
                        .padding(vertical = 14.dp)
                )
                HorizontalDivider()
            }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

// ---------------------------------------------------------------- Stats

@Composable
private fun StatsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rules = remember { Prefs.getRules(ctx) }
    val today = remember { Prefs.currentDayDate(ctx) }
    val days = remember { (6 downTo 0).map { today.minusDays(it.toLong()) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text("Last 7 days", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rules, key = { it.pkg }) { rule ->
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(appLabel(ctx, rule.pkg), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    var weekTotal = 0
                    days.forEach { d ->
                        val mins = Prefs.getUsageSecs(ctx, d.toString(), rule.pkg) / 60
                        weekTotal += mins
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.width(48.dp),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .height(14.dp)
                                    .width((mins.coerceAtMost(180) * 1.2f).dp.coerceAtLeast(2.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                            Text(
                                "  $mins min",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "This week: ${weekTotal / 60}h ${weekTotal % 60}m",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
