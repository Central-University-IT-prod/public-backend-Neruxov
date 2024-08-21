package xyz.neruxov.prodcontestbot.util.logic

import com.pengrad.telegrambot.request.SendMessage

data class EmptyMessage(
    val chatId: Long,
) : SendMessage(chatId, "")
