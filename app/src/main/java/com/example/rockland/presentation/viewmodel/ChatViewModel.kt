package com.example.rockland.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rockland.data.datasource.remote.UserData
import com.example.rockland.data.model.ChatConversation
import com.example.rockland.data.model.ChatMessage
import com.example.rockland.data.model.FriendRelation
import com.example.rockland.data.repository.FriendsDataRepository
import com.example.rockland.data.repository.FriendsRepositoryProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatInboxUiState(
    val currentUserId: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val activeConversationId: String? = null,
    val activeOtherDisplayName: String = "",
    val activeOtherProfilePictureUrl: String = "",
    val messages: List<ChatMessage> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: FriendsDataRepository = FriendsRepositoryProvider.get(),
    private val userDataFlow: StateFlow<UserData?>? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatInboxUiState())
    val uiState: StateFlow<ChatInboxUiState> = _uiState.asStateFlow()

    private val _boundUserId = MutableStateFlow("")
    private val _boundConversationId = MutableStateFlow<String?>(null)
    init {
        userDataFlow?.let { flow ->
            viewModelScope.launch {
                flow.collect { data ->
                    bindUser(data?.userId)
                }
            }
        }
        viewModelScope.launch {
            _boundUserId.flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList<ChatConversation>())
                else repository.getConversationsFlow(uid)
            }.collect { list ->
                try {
                    _uiState.update { it.copy(conversations = list) }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error updating conversations", e)
                    if (_boundUserId.value.isNotBlank()) {
                        _uiState.update { it.copy(conversations = emptyList()) }
                    }
                }
            }
        }
        viewModelScope.launch {
            _boundConversationId.flatMapLatest { convId ->
                if (convId == null) flowOf(emptyList<ChatMessage>())
                else repository.getMessagesFlow(convId)
            }.collect { list ->
                try {
                    _uiState.update { it.copy(messages = list) }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error updating messages", e)
                    if (_boundConversationId.value != null) {
                        _uiState.update { it.copy(messages = emptyList()) }
                    }
                }
            }
        }
    }

    fun bindUser(userId: String?) {
        val uid = userId.orEmpty()
        _uiState.update { it.copy(currentUserId = uid) }
        if (uid.isBlank()) {
            _boundConversationId.value = null
            _uiState.update {
                it.copy(
                    activeConversationId = null,
                    activeOtherDisplayName = "",
                    activeOtherProfilePictureUrl = "",
                    messages = emptyList()
                )
            }
        }
        _boundUserId.value = uid
    }

    fun startChat(friend: FriendRelation) {
        val uid = _uiState.value.currentUserId
        if (uid.isBlank()) return
        viewModelScope.launch {
            val convId = repository.startConversation(uid, friend.friendUserId)
            openConversation(convId, friend.friendDisplayName, friend.friendProfilePictureUrl)
        }
    }

    fun openFromConversation(conversation: ChatConversation) {
        openConversation(conversation.id, conversation.otherDisplayName, conversation.otherProfilePictureUrl)
    }

    private fun openConversation(conversationId: String, otherDisplayName: String, otherProfilePictureUrl: String = "") {
        val uid = _uiState.value.currentUserId
        val conv = _uiState.value.conversations.find { it.id == conversationId }
        val lastSeen = conv?.lastMessageAtMillis ?: System.currentTimeMillis()
        val cleared = _uiState.value.conversations.map { existing ->
            if (existing.id == conversationId) existing.copy(unreadCount = 0) else existing
        }
        _uiState.update {
            it.copy(
                activeConversationId = conversationId,
                activeOtherDisplayName = otherDisplayName,
                activeOtherProfilePictureUrl = otherProfilePictureUrl,
                conversations = cleared
            )
        }
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                repository.markConversationRead(uid, conversationId, lastSeen)
            }
        }
        _boundConversationId.value = conversationId
    }

    fun closeConversation() {
        _boundConversationId.value = null
        _uiState.update {
            it.copy(
                activeConversationId = null,
                activeOtherDisplayName = "",
                activeOtherProfilePictureUrl = "",
                messages = emptyList()
            )
        }
    }

    fun sendMessage(text: String) {
        val uid = _uiState.value.currentUserId
        val convId = _uiState.value.activeConversationId ?: return
        if (uid.isBlank() || text.isBlank()) return
        viewModelScope.launch { repository.sendMessage(convId, uid, text) }
    }

    fun deleteMessage(messageId: String) {
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch { repository.deleteMessage(convId, messageId) }
    }

    fun deleteConversation(conversationId: String) {
        val uid = _uiState.value.currentUserId
        if (uid.isBlank()) return
        viewModelScope.launch {
            repository.deleteConversation(conversationId).fold(
                onSuccess = {
                    if (_uiState.value.activeConversationId == conversationId) {
                        closeConversation()
                    }
                },
                onFailure = { }
            )
        }
    }

    class Factory(
        private val userDataFlow: StateFlow<UserData?>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(repository = FriendsRepositoryProvider.get(), userDataFlow = userDataFlow) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

