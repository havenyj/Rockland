package com.example.rockland.data.repository

import com.example.rockland.data.model.ChatConversation
import com.example.rockland.data.model.ChatMessage
import com.example.rockland.data.model.FriendRelation
import com.example.rockland.data.model.FriendRequest
import com.example.rockland.data.model.UserSummary
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreFriendsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : FriendsDataRepository {

    private val usersRef = db.collection("users")
    private val friendRequestsRef = db.collection("friendRequests")
    private val friendshipsRef = db.collection("friendships")
    private val conversationsRef = db.collection("conversations")
    private val notificationsRepository = ContentReviewRepository()

    override suspend fun searchUsers(currentUserId: String, query: String): List<UserSummary> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val byEmail = usersRef
            .orderBy("email")
            .startAt(q)
            .endAt(q + "\uf8ff")
            .limit(20)
            .get()
            .await()
        val byFirstName = usersRef
            .orderBy("firstName")
            .startAt(q)
            .endAt(q + "\uf8ff")
            .limit(20)
            .get()
            .await()
        val byLastName = usersRef
            .orderBy("lastName")
            .startAt(q)
            .endAt(q + "\uf8ff")
            .limit(20)
            .get()
            .await()
        val seen = mutableSetOf<String>()
        fun mapDoc(doc: com.google.firebase.firestore.DocumentSnapshot): UserSummary? {
            val uid = doc.getString("userId") ?: doc.id
            if (uid == currentUserId || seen.contains(uid)) return null
            val role = doc.getString("role").orEmpty().lowercase()
            if (role == "admin") return null
            seen.add(uid)
            val first = doc.getString("firstName").orEmpty()
            val last = doc.getString("lastName").orEmpty()
            val displayName = listOf(first, last)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { doc.getString("email").orEmpty() }
            return UserSummary(
                userId = uid,
                displayName = displayName,
                email = doc.getString("email").orEmpty(),
                profilePictureUrl = doc.getString("profilePictureUrl").orEmpty()
            )
        }
        val list = mutableListOf<UserSummary>()
        for (doc in byEmail.documents) mapDoc(doc)?.let { list.add(it) }
        if (list.size < 20) for (doc in byFirstName.documents) mapDoc(doc)?.let { list.add(it); if (list.size >= 20) return list }
        if (list.size < 20) for (doc in byLastName.documents) mapDoc(doc)?.let { list.add(it); if (list.size >= 20) return list }
        return list.take(20)
    }

    override fun getFriendsFlow(userId: String): Flow<List<FriendRelation>> = callbackFlow {
        val reg: ListenerRegistration = friendshipsRef
            .whereArrayContains("users", userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                // Fetch friend profiles so the Friends list can show names instead of raw userIds.
                launch {
                    try {
                        val relations = docs.mapNotNull { d ->
                            val users = d.get("users") as? List<*>
                            val otherId = users
                                ?.filterIsInstance<String>()
                                ?.find { it != userId }
                                ?: return@mapNotNull null

                            val profile = runCatching { usersRef.document(otherId).get().await() }.getOrNull()
                            val first = profile?.getString("firstName").orEmpty()
                            val last = profile?.getString("lastName").orEmpty()
                            val displayName = listOf(first, last)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { profile?.getString("email").orEmpty().ifBlank { otherId } }
                            val email = profile?.getString("email").orEmpty()
                            val profilePicUrl = profile?.getString("profilePictureUrl").orEmpty()

                            FriendRelation(
                                friendUserId = otherId,
                                friendDisplayName = displayName,
                                friendEmail = email,
                                friendProfilePictureUrl = profilePicUrl
                            )
                        }
                        trySend(relations)
                    } catch (e: Exception) {
                        android.util.Log.e("FirestoreFriendsRepository", "getFriendsFlow error", e)
                        trySend(emptyList())
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    override fun getOutgoingRequestsFlow(userId: String): Flow<List<FriendRequest>> = callbackFlow {
        val reg: ListenerRegistration = friendRequestsRef
            .whereEqualTo("fromUserId", userId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreFriendsRepository", "getOutgoingRequestsFlow listener error", error)
                    launch { trySend(emptyList()) }
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                launch {
                    try {
                        val list = docs.map { d ->
                            val toId = d.getString("toUserId").orEmpty()
                            val profile = runCatching { usersRef.document(toId).get().await() }.getOrNull()
                            val toAvatar = profile?.getString("profilePictureUrl").orEmpty()
                            FriendRequest(
                                id = d.id,
                                fromUserId = d.getString("fromUserId").orEmpty(),
                                toUserId = toId,
                                fromDisplayName = d.getString("fromDisplayName").orEmpty(),
                                toDisplayName = d.getString("toDisplayName").orEmpty(),
                                createdAtMillis = (d.getLong("createdAtMillis") ?: 0L),
                                toProfilePictureUrl = toAvatar
                            )
                        }
                        trySend(list)
                    } catch (e: Exception) {
                        android.util.Log.e("FirestoreFriendsRepository", "getOutgoingRequestsFlow error", e)
                        trySend(emptyList())
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    override fun getIncomingRequestsFlow(userId: String): Flow<List<FriendRequest>> = callbackFlow {
        val reg: ListenerRegistration = friendRequestsRef
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreFriendsRepository", "getIncomingRequestsFlow listener error", error)
                    launch { trySend(emptyList()) }
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                launch {
                    try {
                        val list = docs.map { d ->
                            val fromId = d.getString("fromUserId").orEmpty()
                            val profile = runCatching { usersRef.document(fromId).get().await() }.getOrNull()
                            val fromAvatar = profile?.getString("profilePictureUrl").orEmpty()
                            FriendRequest(
                                id = d.id,
                                fromUserId = fromId,
                                toUserId = d.getString("toUserId").orEmpty(),
                                fromDisplayName = d.getString("fromDisplayName").orEmpty(),
                                toDisplayName = d.getString("toDisplayName").orEmpty(),
                                createdAtMillis = (d.getLong("createdAtMillis") ?: 0L),
                                fromProfilePictureUrl = fromAvatar
                            )
                        }
                        trySend(list)
                    } catch (e: Exception) {
                        android.util.Log.e("FirestoreFriendsRepository", "getIncomingRequestsFlow error", e)
                        trySend(emptyList())
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    override fun getAcceptFriendRequestsFlow(userId: String): Flow<Boolean> = callbackFlow {
        val reg: ListenerRegistration = usersRef.document(userId).addSnapshotListener { snap, error ->
            if (error != null) {
                android.util.Log.e("FirestoreFriendsRepository", "getAcceptFriendRequestsFlow listener error", error)
                trySend(true)
                return@addSnapshotListener
            }
            try {
                val accept = snap?.getBoolean("acceptFriendRequests") ?: true
                trySend(accept)
            } catch (e: Exception) {
                android.util.Log.e("FirestoreFriendsRepository", "getAcceptFriendRequestsFlow error", e)
                trySend(true)
            }
        }
        awaitClose { reg.remove() }
    }

    override suspend fun sendFriendRequest(
        fromUserId: String,
        fromDisplayName: String,
        toUserId: String,
        toDisplayName: String
    ): Result<Unit> {
        return runCatching {
            val accept = usersRef.document(toUserId).get().await().getBoolean("acceptFriendRequests") ?: true
            if (!accept) throw Exception("User is not accepting friend requests")

            val id = "${fromUserId}_${toUserId}"
            val existing = friendRequestsRef.document(id).get().await()
            if (existing.exists()) {
                val status = existing.getString("status").orEmpty()
                if (status == "accepted" || status == "rejected") {
                    friendRequestsRef.document(id).delete().await()
                }
            }

            val payload = mapOf(
                "fromUserId" to fromUserId,
                "toUserId" to toUserId,
                "fromDisplayName" to fromDisplayName,
                "toDisplayName" to toDisplayName,
                "status" to "pending",
                "createdAtMillis" to System.currentTimeMillis()
            )

            friendRequestsRef.document(id).set(payload).await()

            try {
                notificationsRepository.addUserNotification(
                    userId = toUserId,
                    title = "New friend request",
                    message = "$fromDisplayName sent you a friend request.",
                    targetTab = "friends",
                    type = "friend_request"
                )
            } catch (_: Exception) {
                // Ignore notification failures in dev/test.
            }
        }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }

    override suspend fun deleteFriendRequest(requestId: String, currentUserId: String): Result<Unit> {
        return runCatching {
            val doc = friendRequestsRef.document(requestId).get().await()
            if (doc.getString("fromUserId") != currentUserId) throw Exception("Not allowed")
            friendRequestsRef.document(requestId).delete().await()
        }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }

    override suspend fun acceptFriendRequest(requestId: String, acceptorUserId: String): Result<Unit> {
        return runCatching {
            val doc = friendRequestsRef.document(requestId).get().await()
            val from = doc.getString("fromUserId").orEmpty()
            val to = doc.getString("toUserId").orEmpty()
            if (to != acceptorUserId) throw Exception("Not allowed")

            val users = listOf(from, to).sorted()
            val friendshipId = users.joinToString("_")

            val createdNew: Boolean = db.runTransaction { tx ->
                val friendshipRef = friendshipsRef.document(friendshipId)
                val friendshipSnap = tx.get(friendshipRef)

                // create friendship only if it doesn't already exist
                if (!friendshipSnap.exists()) {
                    tx.set(
                        friendshipRef,
                        mapOf(
                            "users" to users,
                            "createdAtMillis" to System.currentTimeMillis()
                        )
                    )
                }

                // mark request accepted
                tx.update(friendRequestsRef.document(requestId), "status", "accepted")

                // return whether friendship was newly created
                !friendshipSnap.exists()
            }.await()

            // award ONLY the acceptor w friend_count +1 (the current signed-in user)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }


    override suspend fun rejectFriendRequest(requestId: String, rejectorUserId: String): Result<Unit> {
        return runCatching {
            val doc = friendRequestsRef.document(requestId).get().await()
            val to = doc.getString("toUserId").orEmpty()
            if (to != rejectorUserId) throw Exception("Not allowed")
            friendRequestsRef.document(requestId).update("status", "rejected").await()
        }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }

    override suspend fun deleteFriend(userId: String, friendUserId: String): Result<Unit> {
        return runCatching {
            val users = listOf(userId, friendUserId).sorted()
            val friendshipId = users.joinToString("_")
            friendshipsRef.document(friendshipId).delete().await()
            val convId = conversationId(userId, friendUserId)
            conversationsRef.document(convId).delete().await()
        }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }

    override suspend fun setAcceptFriendRequests(userId: String, accept: Boolean) {
        usersRef.document(userId).update("acceptFriendRequests", accept).await()
    }

    override fun conversationId(userId1: String, userId2: String): String =
        listOf(userId1, userId2).sorted().joinToString("|")

    override fun getConversationsFlow(userId: String): Flow<List<ChatConversation>> = callbackFlow {
        val reg: ListenerRegistration = conversationsRef
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreFriendsRepository", "getConversationsFlow listener error", error)
                    launch {
                        trySend(emptyList())
                    }
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                launch {
                    try {
                        val readSnapshot = usersRef.document(userId)
                            .collection("chatRead")
                            .get()
                            .await()
                        val readByConv = readSnapshot.documents.associate { doc ->
                            doc.id to (doc.getLong("lastSeenAtMillis") ?: 0L)
                        }
                        val conversations = docs.map { d ->
                            val participants = (d.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()
                            val otherId = participants.find { it != userId }.orEmpty()

                            val profile = runCatching { usersRef.document(otherId).get().await() }.getOrNull()
                            val first = profile?.getString("firstName").orEmpty()
                            val last = profile?.getString("lastName").orEmpty()
                            val displayName = listOf(first, last)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { profile?.getString("email").orEmpty().ifBlank { otherId } }
                            val otherProfilePicUrl = profile?.getString("profilePictureUrl").orEmpty()

                            val lastMessageAt = d.getLong("lastMessageAtMillis") ?: 0L
                            val lastMessageSenderId = d.getString("lastMessageSenderId").orEmpty()
                            val lastSeen = readByConv[d.id] ?: 0L
                            val unread = if (lastMessageAt > lastSeen && lastMessageSenderId.isNotBlank() && lastMessageSenderId != userId) {
                                try {
                                    val snap = conversationsRef.document(d.id)
                                        .collection("messages")
                                        .whereGreaterThan("sentAtMillis", lastSeen)
                                        .get()
                                        .await()
                                    snap.documents.count { doc ->
                                        val sender = doc.getString("senderId").orEmpty()
                                        val sentAt = doc.getLong("sentAtMillis") ?: 0L
                                        sender != userId && sentAt > lastSeen
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("FirestoreFriendsRepository", "Error counting unread messages", e)
                                    0
                                }
                            } else {
                                0
                            }

                            ChatConversation(
                                id = d.id,
                                otherUserId = otherId,
                                otherDisplayName = displayName,
                                otherProfilePictureUrl = otherProfilePicUrl,
                                lastMessagePreview = d.getString("lastMessageText").orEmpty(),
                                lastMessageAtMillis = lastMessageAt,
                                unreadCount = unread
                            )
                        }
                        trySend(conversations)
                    } catch (e: Exception) {
                        android.util.Log.e("FirestoreFriendsRepository", "getConversationsFlow error", e)
                        trySend(emptyList())
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    override fun getMessagesFlow(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg: ListenerRegistration = conversationsRef.document(conversationId)
            .collection("messages")
            .orderBy("sentAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreFriendsRepository", "getMessagesFlow listener error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                try {
                    val list = snap?.documents.orEmpty().map { d ->
                        ChatMessage(
                            id = d.id,
                            conversationId = conversationId,
                            senderId = d.getString("senderId").orEmpty(),
                            text = d.getString("text").orEmpty(),
                            sentAtMillis = d.getLong("sentAtMillis") ?: 0L,
                            isDeleted = d.getBoolean("isDeleted") ?: false
                        )
                    }.filter { !it.isDeleted }
                    trySend(list)
                } catch (e: Exception) {
                    android.util.Log.e("FirestoreFriendsRepository", "getMessagesFlow error", e)
                    trySend(emptyList())
                }
            }
        awaitClose { reg.remove() }
    }

    override suspend fun sendMessage(conversationId: String, senderId: String, text: String): ChatMessage {
        val msgPayload = mapOf(
            "senderId" to senderId,
            "text" to text.trim(),
            "sentAtMillis" to System.currentTimeMillis(),
            "isDeleted" to false
        )
        val msgRef = conversationsRef.document(conversationId).collection("messages").document()
        msgRef.set(msgPayload).await()
        conversationsRef.document(conversationId).update(
            mapOf(
                "lastMessageText" to text.trim(),
                "lastMessageAtMillis" to msgPayload["sentAtMillis"] as Long,
                "lastMessageSenderId" to senderId
            )
        ).await()
        return ChatMessage(
            id = msgRef.id,
            conversationId = conversationId,
            senderId = senderId,
            text = text.trim(),
            sentAtMillis = msgPayload["sentAtMillis"] as Long
        )
    }

    override suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        return runCatching {
            conversationsRef.document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("isDeleted", true)
                .await()
        }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }

    override suspend fun startConversation(userId: String, otherUserId: String): String {
        val convId = conversationId(userId, otherUserId)
        val doc = conversationsRef.document(convId).get().await()
        if (!doc.exists()) {
            val payload = mapOf(
                "participants" to listOf(userId, otherUserId),
                "createdAtMillis" to System.currentTimeMillis(),
                "lastMessageText" to "",
                "lastMessageAtMillis" to 0L,
                "lastMessageSenderId" to ""
            )
            conversationsRef.document(convId).set(payload).await()
        }
        return convId
    }

    override suspend fun markConversationRead(userId: String, conversationId: String, lastSeenAtMillis: Long) {
        if (userId.isBlank() || conversationId.isBlank()) return
        usersRef.document(userId)
            .collection("chatRead")
            .document(conversationId)
            .set(mapOf("lastSeenAtMillis" to lastSeenAtMillis), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return runCatching {
            conversationsRef.document(conversationId).delete().await()
        }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }
}

