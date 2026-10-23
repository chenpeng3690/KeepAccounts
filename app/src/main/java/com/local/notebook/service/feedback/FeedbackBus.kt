package com.local.notebook.service.feedback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class FeedbackKind { AUTO, PENDING }

data class FeedbackEvent(
    val kind: FeedbackKind,
    val title: String,
    val body: String,
    val txnId: Long? = null,
    val pendingTxnId: Long? = null
)

/** 应用到前台的轻提示通道，避免同时弹系统横幅。 */
@Singleton
class FeedbackBus @Inject constructor() {
    private val _events = MutableSharedFlow<FeedbackEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<FeedbackEvent> = _events

    fun publish(event: FeedbackEvent) {
        _events.tryEmit(event)
    }
}
