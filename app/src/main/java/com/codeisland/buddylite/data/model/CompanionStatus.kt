package com.codeisland.buddylite.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompanionStatus(
    val label: String,
    val shortLabel: String
) {
    IDLE("空闲", "空闲"),
    PROCESSING("处理中", "处理"),
    RUNNING("运行中", "运行"),
    WAITING_APPROVAL("等待批准", "批准"),
    WAITING_QUESTION("等待回答", "问题");

    val isWaiting: Boolean
        get() = this == WAITING_APPROVAL || this == WAITING_QUESTION

    val isActive: Boolean
        get() = this == PROCESSING || this == RUNNING

    companion object {
        fun fromString(value: String): CompanionStatus = when (value.lowercase()) {
            "idle" -> IDLE
            "processing" -> PROCESSING
            "running" -> RUNNING
            "waitingapproval", "waiting_approval" -> WAITING_APPROVAL
            "waitingquestion", "waiting_question" -> WAITING_QUESTION
            else -> IDLE
        }
    }
}

@Serializable
enum class PendingAction {
    APPROVAL,
    QUESTION;

    companion object {
        fun fromString(value: String?): PendingAction? = when (value?.lowercase()) {
            "approval" -> APPROVAL
            "question" -> QUESTION
            else -> null
        }
    }
}
