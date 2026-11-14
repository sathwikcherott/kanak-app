package com.kanak.app.smartinbox

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs only after the user explicitly enables Android's notification access. */
class PaytmNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val text = listOfNotNull(
            extras.getCharSequence("android.title")?.toString(),
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString()
        ).joinToString(" ")
        val debit = PaytmNotificationParser.parse(sbn.packageName, text) ?: return
        scope.launch {
            (application as? com.kanak.app.KanakApp)?.smartInboxRepository?.capture(
                notificationKey = sbn.key, amountMinor = debit.amountMinor, merchant = debit.merchant,
                occurredAt = sbn.postTime
            )
        }
    }
}
