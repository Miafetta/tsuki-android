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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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

        internal suspend fun request(context: Context, config: Bundle): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!withContext(Dispatchers.IO) { manager.isRequestPinAppWidgetSupported }) return false
            val token = UUID.randomUUID().toString()
            val selection = Bundle(config).apply { putString(Storage.PIN_TOKEN, token) }
            val storage = context.applicationContext
            return withStagedPin(
                prepare = { !storage.storageCall(Storage.PREPARE_PIN, selection).getBoolean(Storage.ERROR) },
                request = { requestLauncher(context, manager, selection, token) },
                cancel = { storage.storageCall(Storage.CLEAR_PIN, selection) },
            )
        }

        private fun requestLauncher(context: Context, manager: AppWidgetManager, selection: Bundle, token: String): Boolean {
            val theme = cafe.miafetta.tsuki.core.WidgetTheme.valueOf(requireNotNull(selection.getString(Storage.THEME)))
            val provider = StatusWidgetProvider.providerClass(requireNotNull(selection.getString(Storage.FEATURE_ID)), theme)
            val intent = Intent(context, PinWidgetReceiver::class.java)
                .setAction("cafe.miafetta.tsuki.action.PIN_WIDGET")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .setData("tsuki://pin/$token".toUri())
                .putExtra(Storage.CONFIG, selection)
            // The launcher fills EXTRA_APPWIDGET_ID into this explicit, one-shot callback.
            val callback = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT)
            val accepted = try {
                manager.requestPinAppWidget(ComponentName(context, provider), null, callback)
            } catch (failure: RuntimeException) {
                callback.cancel()
                throw failure
            }
            if (!accepted) callback.cancel()
            return accepted
        }
    }
}

internal suspend fun withStagedPin(prepare: () -> Boolean, request: () -> Boolean, cancel: () -> Unit): Boolean {
    var accepted = false
    try {
        if (withContext(Dispatchers.IO) { prepare() }) accepted = request()
        return accepted
    } finally {
        // The prepare IPC can succeed even if cancellation drops its result before returning to the caller.
        if (!accepted) withContext(NonCancellable) { withContext(Dispatchers.IO) { cancel() } }
    }
}
