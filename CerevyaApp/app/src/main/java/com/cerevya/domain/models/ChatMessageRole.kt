package com.cerevya.domain.models

/**
 * Papel da mensagem na conversa
 */
enum class ChatMessageRole {
    USER,       // Mensagem do usuário
    ASSISTANT,  // Mensagem da IA
    SYSTEM      // Mensagem do sistema
}
