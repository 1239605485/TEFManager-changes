package eternal.future.tefmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.app.AppOpsManager

/** Starts the user-approved overlay only when Terraria is launched. */
object GameOverlayController {
    fun ensurePermission(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            Toast.makeText(context, "请允许 TEFManager 显示悬浮窗后，再次启动游戏", Toast.LENGTH_LONG).show()
            return false
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val usageAllowed = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
        if (!usageAllowed) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(context, "请允许 TEFManager 访问使用情况，以便离开游戏时自动关闭悬浮窗", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    fun startForGame(context: Context, gamePackage: String) {
        if (!Settings.canDrawOverlays(context)) return

        val serviceIntent = Intent(context, ModOverlaySettingsService::class.java)
            .putExtra(ModOverlaySettingsService.EXTRA_GAME_PACKAGE, gamePackage)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, ModOverlaySettingsService::class.java))
    }
}
