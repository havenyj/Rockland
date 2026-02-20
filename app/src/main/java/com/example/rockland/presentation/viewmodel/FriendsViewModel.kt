// Friends Management (FEATURE-12) and Chat (FEATURE-13) state and actions.
package com.example.rockland.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rockland.data.model.ChatMessage
import com.example.rockland.data.model.FriendRelation
import com.example.rockland.data.model.FriendRequest
import com.example.rockland.data.model.UserSummary
import com.example.rockland.data.repository.FriendsDataRepository
import com.example.rockland.data.repository.FriendsRepositoryProvider
import com.example.rockland.presentation.model.UiBanner
import com.example.rockland.presentation.model.UiBannerType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val currentUserId: String = "",
    val searchQuery: String = "",
    val searchResults: List<UserSummary> = emptyList(),
    val friends: List<FriendRelation> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val acceptFriendRequests: Boolean = true,
    val isSearching: Boolean = false,
    val chatConversationId: String? = null,
    val chatOtherDisplayName: String = "",
    val chatMessages: List<ChatMessage> = emptyList()
)

class FriendsViewModel(
    private val repository: FriendsDataRepository = FriendsRepositoryProvider.get()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private val _banners = MutableSharedFlow<UiBanner>(extraBufferCapacity = 1)
    val banners = _banners.asSharedFlow()

    private var currentUserId: String = ""
    private var currentDisplayName: String = ""
    private var searchJob: Job? = null
    private var friendsJob: Job? = null
    private var outgoingRequestsJob: Job? = null
    private var incomingRequestsJob: Job? = null
    private var acceptFriendRequestsJob: Job? = null

    fun bindUser(userId: String?, displayName: String) {
        friendsJob?.cancel()
        outgoingRequestsJob?.cancel()
        incomingRequestsJob?.cancel()
        acceptFriendRequestsJob?.cancel()
        
        currentUserId = userId.orEmpty()
        currentDisplayName = displayName.ifBlank { "Me" }
        _uiState.update { it.copy(currentUserId = currentUserId) }
        
        if (currentUserId.isBlank()) {
            _uiState.update {
                it.copy(
                    friends = emptyList(),
                    outgoingRequests = emptyList(),
                    incomingRequests = emptyList(),
                    acceptFriendRequests = true
                )
            }
            return
        }
        
        friendsJob = viewModelScope.launch {
            try {
                repository.getFriendsFlow(currentUserId).collect { list ->
                    if (_uiState.value.currentUserId == currentUserId) {
                        _uiState.update { it.copy(friends = list) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendsViewModel", "getFriendsFlow error", e)
                if (_uiState.value.currentUserId == currentUserId) {
                    _uiState.update { it.copy(friends = emptyList()) }
                }
            }
        }
        outgoingRequestsJob = viewModelScope.launch {
            try {
                repository.getOutgoingRequestsFlow(currentUserId).collect { list ->
                    if (_uiState.value.currentUserId == currentUserId) {
                        _uiState.update { it.copy(outgoingRequests = list) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendsViewModel", "getOutgoingRequestsFlow error", e)
                if (_uiState.value.currentUserId == currentUserId) {
                    _uiState.update { it.copy(outgoingRequests = emptyList()) }
                }
            }
        }
        incomingRequestsJob = viewModelScope.launch {
            try {
                repository.getIncomingRequestsFlow(currentUserId).collect { list ->
                    if (_uiState.value.currentUserId == currentUserId) {
                        _uiState.update { it.copy(incomingRequests = list) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendsViewModel", "getIncomingRequestsFlow error", e)
                if (_uiState.value.currentUserId == currentUserId) {
                    _uiState.update { it.copy(incomingRequests = emptyList()) }
                }
            }
        }
        acceptFriendRequestsJob = viewModelScope.launch {
            try {
                repository.getAcceptFriendRequestsFlow(currentUserId).collect { accept ->
                    if (_uiState.value.currentUserId == currentUserId) {
                        _uiState.update { it.copy(acceptFriendRequests = accept) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendsViewModel", "getAcceptFriendRequestsFlow error", e)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch()
        }
    }

    private fun performSearch() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            runCatching { repository.searchUsers(currentUserId, q) }
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                    _banners.emit(UiBanner(e.message ?: "Search failed", UiBannerType.Error))
                }
        }
    }

    fun sendFriendRequest(toUser: UserSummary) {
        viewModelScope.launch {
            repository.sendFriendRequest(
                fromUserId = currentUserId,
                fromDisplayName = currentDisplayName,
                toUserId = toUser.userId,
                toDisplayName = toUser.displayName
            ).fold(
                onSuccess = {
                    _banners.emit(UiBanner("Friend request sent to ${toUser.displayName}", UiBannerType.Success))
                    _uiState.update { it.copy(searchResults = it.searchResults.filter { u -> u.userId != toUser.userId }) }
                },
                onFailure = { e ->
                    _banners.emit(UiBanner(e.message ?: "Failed to send request", UiBannerType.Error))
                }
            )
        }
    }

    fun deleteFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            repository.deleteFriendRequest(request.id, currentUserId).fold(
                onSuccess = { _banners.emit(UiBanner("Request removed", UiBannerType.Success)) },
                onFailure = { e -> _banners.emit(UiBanner(e.message ?: "Failed", UiBannerType.Error)) }
            )
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            repository.acceptFriendRequest(request.id, currentUserId).fold(
                onSuccess = { _banners.emit(UiBanner("${request.fromDisplayName} is now a friend", UiBannerType.Success)) },
                onFailure = { e -> _banners.emit(UiBanner(e.message ?: "Failed", UiBannerType.Error)) }
            )
        }
    }

    fun rejectFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            repository.rejectFriendRequest(request.id, currentUserId).fold(
                onSuccess = { _banners.emit(UiBanner("Request declined", UiBannerType.Success)) },
                onFailure = { e -> _banners.emit(UiBanner(e.message ?: "Failed", UiBannerType.Error)) }
            )
        }
    }

    fun deleteFriend(friend: FriendRelation) {
        viewModelScope.launch {
            repository.deleteFriend(currentUserId, friend.friendUserId).fold(
                onSuccess = { _banners.emit(UiBanner("${friend.friendDisplayName} removed from friends", UiBannerType.Success)) },
                onFailure = { e -> _banners.emit(UiBanner(e.message ?: "Failed", UiBannerType.Error)) }
            )
        }
    }

    fun setAcceptFriendRequests(accept: Boolean) {
        viewModelScope.launch {
            repository.setAcceptFriendRequests(currentUserId, accept)
            _banners.emit(UiBanner(if (accept) "Accepting friend requests" else "Not accepting friend requests", UiBannerType.Success))
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FriendsViewModel::class.java)) {
                return FriendsViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
