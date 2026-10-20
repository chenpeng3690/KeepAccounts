package com.keepaccounts.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.keepaccounts.KeepAccountsApp
import kotlinx.coroutines.launch

/**
 * 浮悬提示上的动作按钮：撤销 / 确认入账。
 *
 * 通知的 Action 按钮点下去进程可能都没起来，所以走 BroadcastReceiver，
 * 拿到 application 再丢到协程里执行，不阻塞 onReceive 主线程。
 */
class FeedbackActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_UNDO = "com.keepaccounts.action.UNDO"
        const val ACTION_CONFIRM = "com.keepaccounts.action.CONFIRM"
        const val EXTRA_TXN_ID = "txnId"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val txnId = intent.getLongExtra(EXTRA_TXN_ID, -1)
        if (txnId <= 0) return
        val app = context.applicationContext as? KeepAccountsApp ?: return
        app.container.applicationScope.launch {
            runCatching {
                when (intent.action) {
                    ACTION_UNDO -> app.container.txnRepo.softDelete(txnId)
                    ACTION_CONFIRM -> app.container.parseRepo.confirmPending(txnId)
                }
            }
        }
    }
}
