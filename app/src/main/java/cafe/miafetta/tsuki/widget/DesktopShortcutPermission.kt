package cafe.miafetta.tsuki.widget

import androidx.core.net.toUri
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Locale

internal object DesktopShortcutPermission {
    enum class State { GRANTED, DENIED, UNKNOWN }

    fun check(context: Context): State {
        if (!isXiaomi(Build.MANUFACTURER)) return State.GRANTED
        return fromMode(readMode(context))
    }

    fun isXiaomi(manufacturer: String) =
        manufacturer.lowercase(Locale.ROOT) in setOf("xiaomi", "redmi", "poco")

    fun fromMode(mode: Int?): State = when (mode) {
        AppOpsManager.MODE_ALLOWED -> State.GRANTED
        AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> State.DENIED
        else -> State.UNKNOWN
    }

    @SuppressLint("PrivateApi")
    private fun readMode(context: Context): Int? {
        // shortcut: Xiaomi op 10017 is private; replace when a public permission API becomes available.
        return try {
            val method = AppOpsManager::class.java.getMethod("checkOpNoThrow",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            method.invoke(context.getSystemService(AppOpsManager::class.java),
                10017, Process.myUid(), context.packageName) as? Int
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun openSettings(activity: Activity): Boolean {
        val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setPackage("com.miui.securitycenter")
            .putExtra("extra_pkgname", activity.packageName)
            .putExtra("extra_package_uid", Process.myUid())
        if (launch(activity, miui)) return true
        return launch(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${activity.packageName}".toUri()))
    }

    private fun launch(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
