package com.example.rockland.data.repository

import com.example.rockland.data.model.ContentStatus
import com.example.rockland.data.model.HelpRequest
import com.example.rockland.data.model.HelpRequestStatus
import com.example.rockland.data.model.LocationComment
import com.example.rockland.data.model.LocationPhoto
import com.example.rockland.presentation.viewmodel.RockDictionaryRequest
import com.example.rockland.presentation.viewmodel.ExpertApplicationReviewItem
import com.example.rockland.data.datasource.remote.ApplicationStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class UserNotification(
    val id: String,
    val title: String,
    val message: String,
    val createdAt: Long,
    val isRead: Boolean,
    val targetTab: String? = null,
    val targetLocationId: String? = null,
    val type: String? = null
)

class ContentReviewRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val rockLocationsRef = firestore.collection("rockLocations")
    private val usersRef = firestore.collection("users")
    private val rockDictionaryRequestsRef = firestore.collection("rockDictionaryRequests")
    private val rocksRef = firestore.collection("rock")
    private val helpRequestsRef = firestore.collection("help_requests")

    suspend fun fetchPendingExpertApplications(): List<ExpertApplicationReviewItem> {
        return try {
            val snapshot = usersRef
                .whereEqualTo("expertApplication.status", ApplicationStatus.PENDING.name)
                .orderBy("expertApplication.submittedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            android.util.Log.d("ContentReviewRepository", "fetchPendingExpertApplications: found ${snapshot.documents.size} documents")
            snapshot.documents.map { doc -> expertApplicationFromUserDoc(doc) }
        } catch (e: Exception) {
            android.util.Log.e("ContentReviewRepository", "fetchPendingExpertApplications failed", e)
            emptyList()
        }
    }

    fun observePendingExpertApplications(): Flow<List<ExpertApplicationReviewItem>> = callbackFlow {
        val registration: ListenerRegistration = usersRef
            .whereEqualTo("expertApplication.status", ApplicationStatus.PENDING.name)
            .orderBy("expertApplication.submittedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ContentReviewRepository", "observePendingExpertApplications error", error)
                    android.util.Log.e("ContentReviewRepository", "Error code: ${error.code}, message: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                android.util.Log.d("ContentReviewRepository", "observePendingExpertApplications: found ${docs.size} documents")
                val list = docs.map { doc -> expertApplicationFromUserDoc(doc) }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    private fun expertApplicationFromUserDoc(doc: com.google.firebase.firestore.DocumentSnapshot): ExpertApplicationReviewItem {
        val submittedAtRaw = doc.get("expertApplication.submittedAt")
        val submittedAt = when (submittedAtRaw) {
            is Timestamp -> submittedAtRaw.toDate().time
            is Number -> submittedAtRaw.toLong()
            else -> 0L
        }
        val firstName = doc.getString("firstName") ?: ""
        val lastName = doc.getString("lastName") ?: ""
        val displayName = listOf(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { doc.getString("email") ?: "User" }

        val yearsOfExperienceRaw = doc.get("expertApplication.yearsOfExperience")
        val yearsOfExperience = when (yearsOfExperienceRaw) {
            is String -> yearsOfExperienceRaw
            is Number -> yearsOfExperienceRaw.toString()
            else -> ""
        }
        return ExpertApplicationReviewItem(
            userId = doc.getString("userId") ?: doc.id,
            userDisplayName = displayName,
            userEmail = doc.getString("email") ?: "",
            currentRole = doc.getString("role") ?: "nature_enthusiast",
            fullName = doc.getString("expertApplication.fullName") ?: "",
            expertise = doc.getString("expertApplication.expertise") ?: "",
            yearsOfExperience = yearsOfExperience,
            portfolioLink = doc.getString("expertApplication.portfolioLink") ?: "",
            notes = doc.getString("expertApplication.notes") ?: "",
            submittedAt = submittedAt
        )
    }

    suspend fun fetchPendingComments(): List<LocationComment> {
        val snapshot = firestore.collectionGroup("comments")
            .whereEqualTo("status", ContentStatus.PENDING.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()

        // cache to avoid duplicate rockLocations lookups
        val locationNameCache = mutableMapOf<String, String>()

        return snapshot.documents.map { doc ->
            val locationId = doc.getString("locationId") ?: ""
            val locationNameFromDoc = doc.getString("locationName").orEmpty()
            val effectiveLocationName =
                if (locationNameFromDoc.isNotBlank()) {
                    locationNameFromDoc
                } else {
                    getLocationName(locationId, locationNameCache)
                }

            LocationComment(
                commentId = doc.id,
                locationId = locationId,
                locationName = effectiveLocationName,
                userId = doc.getString("userId") ?: "",
                author = doc.getString("author") ?: "Unknown",
                text = doc.getString("text") ?: "",
                timestamp = doc.getLong("timestamp") ?: 0L,
                updatedAt = doc.getLong("updatedAt"),
                status = ContentStatus.PENDING,
                reviewedBy = doc.getString("reviewedBy"),
                reviewedAt = doc.getLong("reviewedAt")
            )
        }
    }

    suspend fun fetchPendingPhotos(): List<LocationPhoto> {
        val snapshot = firestore.collectionGroup("photos")
            .whereEqualTo("status", ContentStatus.PENDING.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()

        // cache to avoid duplicate rockLocations lookups
        val locationNameCache = mutableMapOf<String, String>()

        return snapshot.documents.map { doc ->
            val locationId = doc.getString("locationId") ?: ""
            val locationNameFromDoc = doc.getString("locationName").orEmpty()
            val effectiveLocationName =
                if (locationNameFromDoc.isNotBlank()) {
                    locationNameFromDoc
                } else {
                    getLocationName(locationId, locationNameCache)
                }

            LocationPhoto(
                locationPhotoId = doc.id,
                locationId = locationId,
                locationName = effectiveLocationName,
                commentId = doc.getString("commentId"),
                userId = doc.getString("userId") ?: "",
                author = doc.getString("author") ?: "Unknown",
                caption = doc.getString("caption") ?: "",
                imageUrl = doc.getString("imageUrl") ?: "",
                timestamp = doc.getLong("timestamp") ?: 0L,
                status = ContentStatus.PENDING,
                reviewedBy = doc.getString("reviewedBy"),
                reviewedAt = doc.getLong("reviewedAt")
            )
        }
    }

    fun observePendingComments(): Flow<List<LocationComment>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collectionGroup("comments")
            .whereEqualTo("status", ContentStatus.PENDING.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                launch {
                    val cache = mutableMapOf<String, String>()
                    val list = snapshot.documents.map { doc ->
                        val locationId = doc.getString("locationId") ?: ""
                        val locationNameFromDoc = doc.getString("locationName").orEmpty()
                        val effectiveLocationName =
                            if (locationNameFromDoc.isNotBlank()) {
                                locationNameFromDoc
                            } else {
                                getLocationName(locationId, cache)
                            }
                        LocationComment(
                            commentId = doc.id,
                            locationId = locationId,
                            locationName = effectiveLocationName,
                            userId = doc.getString("userId") ?: "",
                            author = doc.getString("author") ?: "Unknown",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            updatedAt = doc.getLong("updatedAt"),
                            status = ContentStatus.PENDING,
                            reviewedBy = doc.getString("reviewedBy"),
                            reviewedAt = doc.getLong("reviewedAt")
                        )
                    }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observePendingPhotos(): Flow<List<LocationPhoto>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collectionGroup("photos")
            .whereEqualTo("status", ContentStatus.PENDING.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                launch {
                    val cache = mutableMapOf<String, String>()
                    val list = snapshot.documents.map { doc ->
                        val locationId = doc.getString("locationId") ?: ""
                        val locationNameFromDoc = doc.getString("locationName").orEmpty()
                        val effectiveLocationName =
                            if (locationNameFromDoc.isNotBlank()) {
                                locationNameFromDoc
                            } else {
                                getLocationName(locationId, cache)
                            }
                        LocationPhoto(
                            locationPhotoId = doc.id,
                            locationId = locationId,
                            locationName = effectiveLocationName,
                            commentId = doc.getString("commentId"),
                            userId = doc.getString("userId") ?: "",
                            author = doc.getString("author") ?: "Unknown",
                            caption = doc.getString("caption") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            status = ContentStatus.PENDING,
                            reviewedBy = doc.getString("reviewedBy"),
                            reviewedAt = doc.getLong("reviewedAt")
                        )
                    }
                    trySend(list)
                }
            }
        awaitClose { registration.remove() }
    }

    private suspend fun getLocationName(
        locationId: String,
        cache: MutableMap<String, String>
    ): String {
        if (locationId.isBlank()) return ""
        cache[locationId]?.let { return it }
        val name = runCatching {
            rockLocationsRef.document(locationId).get().await().getString("name")
        }.getOrNull().orEmpty()
        cache[locationId] = name
        return name
    }

    suspend fun submitRockDictionaryRequest(
        requestType: String,
        rockID: Int,
        rockName: String,
        rockRarity: String,
        rockLocation: String,
        rockDesc: String,
        imageUrl: String,
        submittedBy: String,
        submittedById: String
    ) {
        if (submittedById.isBlank()) {
            throw IllegalArgumentException("Missing userId for rock dictionary request.")
        }
        val now = System.currentTimeMillis()
        val requestId = rockDictionaryRequestsRef.document().id
        val payload = mapOf(
            "requestId" to requestId,
            "requestType" to requestType,
            "status" to ContentStatus.PENDING.name,
            "rockID" to rockID,
            "rockName" to rockName,
            "rockRarity" to rockRarity,
            "rockLocation" to rockLocation,
            "rockDesc" to rockDesc,
            "imageUrl" to imageUrl,
            "submittedBy" to submittedBy,
            "submittedById" to submittedById,
            "createdAt" to now,
            "updatedAt" to now
        )
        usersRef.document(submittedById)
            .collection("rockDictionaryRequests")
            .document(requestId)
            .set(payload)
            .await()
    }

    suspend fun uploadRockDictionaryImage(userId: String, imageUri: Uri): String = suspendCoroutine { cont ->
        if (userId.isBlank()) {
            cont.resumeWithException(IllegalArgumentException("Missing userId for image upload."))
            return@suspendCoroutine
        }
        val storageRef = FirebaseStorage.getInstance()
            .reference
            .child("users/$userId/rockDictionary/${System.currentTimeMillis()}.jpg")
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl
                    .addOnSuccessListener { uri -> cont.resume(uri.toString()) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    suspend fun fetchPendingRockDictionaryRequests(): List<RockDictionaryRequest> {
        val snapshot = firestore.collectionGroup("rockDictionaryRequests")
            .whereEqualTo("status", ContentStatus.PENDING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.map { doc ->
            val createdAt = doc.getLong("createdAt") ?: 0L
            val status = runCatching {
                ContentStatus.valueOf(doc.getString("status") ?: ContentStatus.PENDING.name)
            }.getOrDefault(ContentStatus.PENDING)
            RockDictionaryRequest(
                id = doc.getString("requestId") ?: doc.id,
                requestPath = doc.reference.path,
                requestType = doc.getString("requestType") ?: "ADD",
                rockID = (doc.getLong("rockID") ?: 0L).toInt(),
                rockName = doc.getString("rockName") ?: "",
                rockRarity = doc.getString("rockRarity") ?: "",
                rockLocation = doc.getString("rockLocation") ?: "",
                rockDesc = doc.getString("rockDesc") ?: "",
                imageUrl = doc.getString("imageUrl") ?: "",
                submittedBy = doc.getString("submittedBy") ?: "Verified Expert",
                submittedById = doc.getString("submittedById") ?: "",
                createdAt = createdAt,
                status = status,
                reviewedBy = doc.getString("reviewedBy"),
                reviewedAt = doc.getLong("reviewedAt")
            )
        }
    }

    suspend fun approveExpertApplication(
        item: ExpertApplicationReviewItem,
        reviewerId: String?
    ) {
        if (item.userId.isBlank()) return
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "role" to "verified_expert",
            "expertApplication.status" to ApplicationStatus.APPROVED.name,
            "expertApplication.reviewedAt" to now
        )
        if (!reviewerId.isNullOrBlank()) {
            updates["expertApplication.reviewedBy"] = reviewerId
        }
        usersRef.document(item.userId).update(updates).await()
        addUserNotification(
            userId = item.userId,
            title = "Expert application approved",
            message = "Your verified expert application has been approved. You are now a verified expert.",
            targetTab = "inbox",
            type = "expert_application_approved"
        )
    }

    suspend fun rejectExpertApplication(
        item: ExpertApplicationReviewItem,
        reviewerId: String?
    ) {
        if (item.userId.isBlank()) return
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "expertApplication.status" to ApplicationStatus.REJECTED.name,
            "expertApplication.reviewedAt" to now
        )
        if (!reviewerId.isNullOrBlank()) {
            updates["expertApplication.reviewedBy"] = reviewerId
        }
        usersRef.document(item.userId).update(updates).await()
        addUserNotification(
            userId = item.userId,
            title = "Expert application rejected",
            message = "Your verified expert application has been rejected by an admin.",
            targetTab = "inbox",
            type = "expert_application_rejected"
        )
    }

    suspend fun updateCommentStatus(
        locationId: String,
        commentId: String,
        status: ContentStatus,
        reviewerId: String?
    ) {
        if (locationId.isBlank() || commentId.isBlank()) return
        val updates = mapOf(
            "status" to status.name,
            "reviewedBy" to reviewerId,
            "reviewedAt" to System.currentTimeMillis()
        )
        rockLocationsRef.document(locationId)
            .collection("community")
            .document("default")
            .collection("comments")
            .document(commentId)
            .update(updates)
            .await()
    }

    suspend fun updatePhotoStatus(
        locationId: String,
        photoId: String,
        status: ContentStatus,
        reviewerId: String?
    ) {
        if (locationId.isBlank() || photoId.isBlank()) return
        val updates = mapOf(
            "status" to status.name,
            "reviewedBy" to reviewerId,
            "reviewedAt" to System.currentTimeMillis()
        )
        rockLocationsRef.document(locationId)
            .collection("community")
            .document("default")
            .collection("photos")
            .document(photoId)
            .update(updates)
            .await()
    }

    suspend fun approveRockDictionaryRequest(
        request: RockDictionaryRequest,
        reviewerId: String?
    ) {
        applyRockDictionaryApproval(request)
        updateRockDictionaryRequestStatus(request, ContentStatus.APPROVED, reviewerId)
    }

    suspend fun rejectRockDictionaryRequest(
        request: RockDictionaryRequest,
        reviewerId: String?
    ) {
        updateRockDictionaryRequestStatus(request, ContentStatus.REJECTED, reviewerId)
    }

    private suspend fun updateRockDictionaryRequestStatus(
        request: RockDictionaryRequest,
        status: ContentStatus,
        reviewerId: String?
    ) {
        val updates = mapOf(
            "status" to status.name,
            "reviewedBy" to reviewerId,
            "reviewedAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )
        if (request.requestPath.isNotBlank()) {
            firestore.document(request.requestPath).update(updates).await()
            return
        }
        if (request.id.isBlank()) return
        rockDictionaryRequestsRef.document(request.id).update(updates).await()
    }

    private suspend fun applyRockDictionaryApproval(request: RockDictionaryRequest) {
        if (request.requestType.uppercase() == "EDIT") {
            val doc = findRockDoc(request.rockID, request.rockName) ?: return
            val payload = mutableMapOf<String, Any>(
                "rockID" to (request.rockID.takeIf { it > 0 } ?: (doc.getLong("rockID") ?: 0L).toInt()),
                "rockName" to request.rockName,
                "rockRarity" to request.rockRarity,
                "rockLocation" to request.rockLocation,
                "rockDesc" to request.rockDesc
            )
            val imageUrl = if (request.imageUrl.isNotBlank()) {
                request.imageUrl
            } else {
                doc.getString("rockImageUrl") ?: ""
            }
            payload["rockImageUrl"] = imageUrl
            doc.reference.set(payload).await()
        } else {
            val nextId = if (request.rockID > 0) request.rockID else getNextRockId()
            val payload = mapOf(
                "rockID" to nextId,
                "rockName" to request.rockName,
                "rockRarity" to request.rockRarity,
                "rockLocation" to request.rockLocation,
                "rockDesc" to request.rockDesc,
                "rockImageUrl" to request.imageUrl
            )
            rocksRef.add(payload).await()
        }
    }

    private suspend fun findRockDoc(rockId: Int, rockName: String) = when {
        rockId > 0 -> {
            rocksRef.whereEqualTo("rockID", rockId).limit(1).get().await().documents.firstOrNull()
        }
        rockName.isNotBlank() -> {
            rocksRef.whereEqualTo("rockName", rockName).limit(1).get().await().documents.firstOrNull()
        }
        else -> null
    }

    private suspend fun getNextRockId(): Int {
        val snapshot = rocksRef.orderBy("rockID", Query.Direction.DESCENDING).limit(1).get().await()
        val current = snapshot.documents.firstOrNull()?.getLong("rockID") ?: 0L
        return (current + 1L).toInt()
    }

    data class InboxSeenState(
        val pendingCommentCount: Int = 0,
        val pendingPhotoCount: Int = 0,
        val pendingRockCount: Int = 0,
        val pendingHelpCount: Int = 0,
        val pendingExpertApplicationCount: Int = 0,
        val pendingExpertApplicationLastSeenAt: Long = 0L
    )

    suspend fun fetchInboxSeenState(userId: String): InboxSeenState {
        if (userId.isBlank()) return InboxSeenState()
        val doc = usersRef.document(userId).get().await()
        return InboxSeenState(
            pendingCommentCount = (doc.getLong("inboxSeenPendingCommentCount") ?: 0L).toInt(),
            pendingPhotoCount = (doc.getLong("inboxSeenPendingPhotoCount") ?: 0L).toInt(),
            pendingRockCount = (doc.getLong("inboxSeenPendingRockCount") ?: 0L).toInt(),
            pendingHelpCount = (doc.getLong("inboxSeenPendingHelpCount") ?: 0L).toInt(),
            pendingExpertApplicationCount = (doc.getLong("inboxSeenPendingExpertApplicationCount") ?: 0L).toInt(),
            pendingExpertApplicationLastSeenAt = (doc.getLong("inboxSeenPendingExpertApplicationLastSeenAt") ?: 0L)
        )
    }

    suspend fun updateInboxSeenState(
        userId: String,
        pendingCommentCount: Int? = null,
        pendingPhotoCount: Int? = null,
        pendingRockCount: Int? = null,
        pendingHelpCount: Int? = null,
        pendingExpertApplicationCount: Int? = null,
        pendingExpertApplicationLastSeenAt: Long? = null
    ) {
        if (userId.isBlank()) return
        val updates = mutableMapOf<String, Any>()
        pendingCommentCount?.let { updates["inboxSeenPendingCommentCount"] = it }
        pendingPhotoCount?.let { updates["inboxSeenPendingPhotoCount"] = it }
        pendingRockCount?.let { updates["inboxSeenPendingRockCount"] = it }
        pendingHelpCount?.let { updates["inboxSeenPendingHelpCount"] = it }
        pendingExpertApplicationCount?.let { updates["inboxSeenPendingExpertApplicationCount"] = it }
        pendingExpertApplicationLastSeenAt?.let { updates["inboxSeenPendingExpertApplicationLastSeenAt"] = it }
        if (updates.isEmpty()) return
        usersRef.document(userId).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    fun observeUserNotifications(userId: String): Flow<List<UserNotification>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration = usersRef.document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("ContentReviewRepository", "observeUserNotifications listener error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                try {
                    val docs = snapshot?.documents.orEmpty()
                    val list = docs.map { doc -> userNotificationFromDoc(doc) }
                    trySend(list)
                } catch (e: Exception) {
                    android.util.Log.e("ContentReviewRepository", "observeUserNotifications error", e)
                    trySend(emptyList())
                }
            }
        awaitClose { registration.remove() }
    }

    private fun userNotificationFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot): UserNotification {
        val createdAt = when (val raw = doc.get("createdAt")) {
            is Number -> raw.toLong()
            is Timestamp -> raw.toDate().time
            else -> 0L
        }
        return UserNotification(
            id = doc.id,
            title = doc.getString("title") ?: "",
            message = doc.getString("message") ?: "",
            createdAt = createdAt,
            isRead = doc.getBoolean("isRead") ?: false,
            targetTab = doc.getString("targetTab"),
            targetLocationId = doc.getString("targetLocationId"),
            type = doc.getString("type")
        )
    }

    suspend fun addUserNotification(
        userId: String,
        title: String,
        message: String,
        targetTab: String? = null,
        targetLocationId: String? = null,
        type: String? = null
    ) {
        if (userId.isBlank()) return
        val payload = mutableMapOf(
            "title" to title,
            "message" to message,
            "createdAt" to System.currentTimeMillis(),
            "isRead" to false
        )
        if (!targetTab.isNullOrBlank()) payload["targetTab"] = targetTab
        if (!targetLocationId.isNullOrBlank()) payload["targetLocationId"] = targetLocationId
        if (!type.isNullOrBlank()) payload["type"] = type
        usersRef.document(userId)
            .collection("notifications")
            .add(payload)
            .await()
    }

    suspend fun markNotificationRead(userId: String, notificationId: String) {
        if (userId.isBlank() || notificationId.isBlank()) return
        usersRef.document(userId)
            .collection("notifications")
            .document(notificationId)
            .update("isRead", true)
            .await()
    }

    suspend fun clearNotifications(userId: String) {
        if (userId.isBlank()) return
        val snapshot = usersRef.document(userId)
            .collection("notifications")
            .get()
            .await()
        val batch = firestore.batch()
        snapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    suspend fun deleteNotification(userId: String, notificationId: String) {
        if (userId.isBlank() || notificationId.isBlank()) return
        usersRef.document(userId)
            .collection("notifications")
            .document(notificationId)
            .delete()
            .await()
    }

    // --------------- Help requests ---------------

    suspend fun submitHelpRequest(
        userId: String,
        userDisplayName: String,
        subject: String,
        details: String
    ) {
        if (userId.isBlank()) return
        val now = System.currentTimeMillis()
        val payload = mapOf(
            "userId" to userId,
            "userDisplayName" to userDisplayName,
            "subject" to subject,
            "details" to details,
            "status" to HelpRequestStatus.PENDING.name,
            "createdAt" to now
        )
        helpRequestsRef.add(payload).await()
    }

    suspend fun fetchPendingHelpRequests(): List<HelpRequest> {
        val snapshot = helpRequestsRef
            .whereEqualTo("status", HelpRequestStatus.PENDING.name)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.map { doc ->
            helpRequestFromDoc(doc.id, doc)
        }
    }

    // Updates the help request with reply and notifies the requesting user.

    suspend fun replyHelpRequest(
        requestId: String,
        replyText: String,
        repliedById: String,
        requestUserId: String
    ) {
        if (requestId.isBlank()) return
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "status" to HelpRequestStatus.REPLIED.name,
            "repliedAt" to now,
            "replyText" to replyText,
            "repliedBy" to repliedById
        )
        helpRequestsRef.document(requestId).update(updates).await()
        if (requestUserId.isNotBlank()) {
            notifyUserHelpReply(requestUserId, replyText)
        }
    }

    private fun helpRequestFromDoc(id: String, doc: com.google.firebase.firestore.DocumentSnapshot): HelpRequest {
        val createdAt = when (val raw = doc.get("createdAt")) {
            is Number -> raw.toLong()
            is Timestamp -> raw.toDate().time
            else -> 0L
        }
        val status = runCatching {
            HelpRequestStatus.valueOf(doc.getString("status") ?: HelpRequestStatus.PENDING.name)
        }.getOrDefault(HelpRequestStatus.PENDING)
        return HelpRequest(
            id = id,
            userId = doc.getString("userId") ?: "",
            userDisplayName = doc.getString("userDisplayName") ?: "",
            subject = doc.getString("subject") ?: "",
            details = doc.getString("details") ?: "",
            status = status,
            createdAt = createdAt,
            repliedAt = doc.getLong("repliedAt"),
            replyText = doc.getString("replyText") ?: "",
            repliedBy = doc.getString("repliedBy") ?: ""
        )
    }

    // Call after replyHelpRequest to notify the user who submitted the request.
    suspend fun notifyUserHelpReply(userId: String, fullReply: String) {
        addUserNotification(
            userId = userId,
            title = "Reply to your help request",
            message = fullReply,
            targetTab = "inbox",
            type = "help_reply"
        )
    }
}
