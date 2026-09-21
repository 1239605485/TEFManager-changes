package eternal.future.tefmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

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

        return true
    }

    fun startForGame(context: Context, gamePackage: String? = null) {
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
