package com.alisia.focuslock

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

object Permissions {

    fun hasUsageAccess(c: Context): Boolean {
        val appOps = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun allGranted(c: Context): Boolean = hasUsageAccess(c) && canDrawOverlays(c)
}
