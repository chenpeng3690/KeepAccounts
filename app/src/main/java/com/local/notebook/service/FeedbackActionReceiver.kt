package com.local.notebook.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.local.notebook.data.repository.LedgerRepository
import com.local.notebook.service.feedback.FeedbackNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FeedbackActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: LedgerRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val txnId = intent.getLongExtra(EXTRA_TXN_ID, -1L)
        if (txnId <= 0) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_UNDO -> {
                        repository.softDelete(txnId)
                        NotificationManagerCompat.from(context)
                            .cancel(FeedbackNotifier.AUTO_NOTIFICATION_ID)
                    }
                    ACTION_CONFIRM -> {
                        repository.confirmPendingTransaction(txnId)
                        NotificationManagerCompat.from(context)
                            .cancel(FeedbackNotifier.pendingNotificationId(txnId))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UNDO = "com.local.notebook.feedback.UNDO"
        const val ACTION_CONFIRM = "com.local.notebook.feedback.CONFIRM"
        const val EXTRA_TXN_ID = "txn_id"
    }
}
