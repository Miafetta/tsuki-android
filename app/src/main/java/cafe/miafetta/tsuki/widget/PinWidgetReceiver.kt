package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.StorageContract as Storage

import androidx.core.net.toUri
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import cafe.miafetta.tsuki.storageCall
import java.util.UUID
import java.util.concurrent.Executors

class PinWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (id !in StatusWidgetProvider.owned(context)) return
        val config = intent.getBundleExtra(Storage.CONFIG) ?: return
        config.putInt(Storage.WIDGET_ID, id)
        val pending = goAsync()
        worker.execute {
            try {
                val result = context.storageCall(Storage.CONFIGURE, config)
                if (result.getBoolean(Storage.ERROR)) Log.w("TsukiWidget", "Unable to save pinned widget id=$id")
                context.contentResolver.notifyChange(WidgetStorageProvider.URI, null)
            } finally { pending.finish() }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor()

        internal fun request(context: Context, config: Bundle): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val token = UUID.randomUUID().toString()
            val selection = Bundle(config).apply { putString(Storage.PIN_TOKEN, token) }
            if (context.storageCall(Storage.PREPARE_PIN, selection).getBoolean(Storage.ERROR)) return false
            val intent = Intent(context, PinWidgetReceiver::class.java)
                .setAction("cafe.miafetta.tsuki.action.PIN_WIDGET")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .setData("tsuki://pin/$token".toUri())
                .putExtra(Storage.CONFIG, selection)
            // The launcher fills EXTRA_APPWIDGET_ID into this explicit, one-shot callback.
            val callback = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT)
            val theme = cafe.miafetta.tsuki.core.WidgetTheme.valueOf(requireNotNull(config.getString(Storage.THEME)))
            val accepted = try {
                manager.requestPinAppWidget(ComponentName(context, StatusWidgetProvider.providerClass(theme)), null, callback)
            } catch (failure: RuntimeException) {
                context.storageCall(Storage.CLEAR_PIN, selection)
                callback.cancel()
                throw failure
            }
            if (!accepted) {
                context.storageCall(Storage.CLEAR_PIN, selection)
                callback.cancel()
            }
            return accepted
        }
    }
}
