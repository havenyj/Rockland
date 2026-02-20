// Displays a basic inbox view and routes to the profile screen.
package com.example.rockland.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rockland.data.datasource.remote.UserData
import com.example.rockland.data.repository.UserProfileRepository
import com.example.rockland.data.model.FaqItem
import com.example.rockland.data.model.FriendRelation
import com.example.rockland.data.model.FriendRequest
import com.example.rockland.data.model.HelpRequest
import com.example.rockland.data.model.ChatMessage
import com.example.rockland.data.model.ChatConversation
import com.example.rockland.data.model.UserSummary
import com.example.rockland.presentation.viewmodel.FaqViewModel
import com.example.rockland.presentation.viewmodel.ChatViewModel
import com.example.rockland.presentation.viewmodel.FriendsViewModel
import com.example.rockland.presentation.viewmodel.InboxNotification
import com.example.rockland.presentation.viewmodel.RockDictionaryRequest
import com.example.rockland.presentation.viewmodel.ExpertApplicationReviewItem
import com.example.rockland.presentation.viewmodel.ReviewContentViewModel
import com.example.rockland.presentation.model.UiBanner
import com.example.rockland.presentation.model.UiBannerType
import com.example.rockland.ui.components.TopBannerHost
import com.example.rockland.ui.theme.TextDark
import coil.compose.AsyncImage
import com.example.rockland.ui.theme.Rock1
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.rockland.data.repository.AwardsRepository
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty
import kotlin.text.trim
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.OpenInNew

@Composable
fun InboxScreen(
    userData: UserData?,
    userViewModel: com.example.rockland.presentation.viewmodel.UserViewModel? = null,
    onProfileClick: () -> Unit,
    onGoToPage: (InboxNotification) -> Unit = {},
    reviewViewModel: ReviewContentViewModel = viewModel(factory = ReviewContentViewModel.Factory()),
    reviewTabIndex: Int? = null,
    onReviewTabChanged: ((Int) -> Unit)? = null
) {
    val showAgentDialog = remember { mutableStateOf(false) }
    val showFaqDialog = remember { mutableStateOf(false) }
    val requestFormVisible = remember { mutableStateOf(false) }
    val reviewScreenVisible = remember { mutableStateOf(false) }
    val rockReviewVisible = remember { mutableStateOf(false) }
    val friendsScreenVisible = remember { mutableStateOf(false) }
    val friendsBanner = remember { mutableStateOf<UiBanner?>(null) }
    val friendsViewModel: FriendsViewModel = viewModel(factory = FriendsViewModel.Factory())
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(userDataFlow = userViewModel?.userData)
    )
    val interactiveNotificationScreenVisible = remember { mutableStateOf(false) }
    val pendingComments by reviewViewModel.pendingComments.collectAsState()
    val pendingPhotos by reviewViewModel.pendingPhotos.collectAsState()
    val pendingRockRequests by reviewViewModel.pendingRockRequests.collectAsState()
    val pendingHelpRequests by reviewViewModel.pendingHelpRequests.collectAsState()
    val pendingExpertApplications by reviewViewModel.pendingExpertApplications.collectAsState()
    val notifications by reviewViewModel.notifications.collectAsState()
    val isReviewLoading by reviewViewModel.isLoading.collectAsState()
    val faqViewModel: FaqViewModel = viewModel(factory = FaqViewModel.Factory())
    val isFaqAdmin = userData?.role?.trim()?.lowercase() in listOf("admin", "user_admin")
    val isVerifiedExpert = userData?.role?.trim()?.lowercase() == "verified_expert"
    val isAdmin = userData?.role?.trim()?.lowercase() in listOf("admin", "user_admin")
    val helpRequestListVisible = remember { mutableStateOf(false) }
    val helpRequestDetail = remember { mutableStateOf<HelpRequest?>(null) }
    val helpReplyPreviewNotification = remember { mutableStateOf<InboxNotification?>(null) }
    val helpRequestBanner = remember { mutableStateOf<UiBanner?>(null) }
    val applicationReviewVisible = remember { mutableStateOf(false) }

    LaunchedEffect(userData?.userId, userData?.role) {
        reviewViewModel.bindUser(userData?.userId, userData?.role)
    }
    LaunchedEffect(userData?.userId) {
        chatViewModel.bindUser(userData?.userId)
    }
    LaunchedEffect(friendsScreenVisible.value, userData?.userId) {
        if (friendsScreenVisible.value && userData != null) {
            val displayName = listOf(userData.firstName, userData.lastName).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { userData.email ?: "Me" }
            friendsViewModel.bindUser(userData.userId, displayName)
        } else if (friendsScreenVisible.value && userData == null) {
            friendsViewModel.bindUser(null, "Me")
        }
    }
    LaunchedEffect(userData?.userId) {
        faqViewModel.bindUser(userData?.userId)
    }

    val photosByCommentId = remember(pendingPhotos) {
        pendingPhotos.groupBy { it.commentId }
    }
    val pendingReviewItems = remember(pendingComments, pendingPhotos) {
        val items = mutableListOf<PendingComment>()
        // comment + attached photos
        pendingComments.forEachIndexed { index, comment ->
            val photos = photosByCommentId[comment.commentId].orEmpty()
            items += PendingComment(
                id = comment.commentId,
                number = "${index + 1}",
                locationId = comment.locationId,
                userId = comment.userId,
                author = comment.author,
                postedAt = formatDate(comment.timestamp),
                location = comment.locationName.ifBlank { "Unknown Location" },
                preview = comment.text.take(80),
                fullText = comment.text,
                imageUrls = photos.map { it.imageUrl },
                photoIds = photos.map { it.locationPhotoId }
            )
        }
        val photoOnly = photosByCommentId[null].orEmpty()
        val groupedPhotoOnly = photoOnly.groupBy { "${it.locationId}|${it.userId}" }
        groupedPhotoOnly.entries.forEachIndexed { index, entry ->
            val photos = entry.value
            val first = photos.first()
            items += PendingComment(
                id = "photo_only_${index + 1}",
                number = "${pendingComments.size + index + 1}",
                locationId = first.locationId,
                userId = first.userId,
                author = first.author,
                postedAt = formatDate(first.timestamp),
                location = first.locationName.ifBlank { "Unknown Location" },
                preview = "Photo submission",
                fullText = "Photo submission",
                imageUrls = photos.map { it.imageUrl },
                photoIds = photos.map { it.locationPhotoId }
            )
        }
        items
    }
    val commentsById = remember(pendingComments) {
        pendingComments.associateBy { it.commentId }
    }
    val photosById = remember(pendingPhotos) {
        pendingPhotos.associateBy { it.locationPhotoId }
    }

    if (reviewScreenVisible.value) {
        ReviewContentScreen(
            pendingComments = pendingReviewItems,
            onApproveComment = { item ->
                val comment = commentsById[item.id]
                val photos = item.photoIds.mapNotNull { photosById[it] }
                if (comment != null) {
                    reviewViewModel.approveComment(comment, userData?.userId)
                }
                if (photos.isNotEmpty()) {
                    reviewViewModel.approvePhotos(photos, userData?.userId)
                }
            },
            onRejectComment = { item ->
                val comment = commentsById[item.id]
                val photos = item.photoIds.mapNotNull { photosById[it] }
                if (comment != null) {
                    reviewViewModel.rejectComment(comment, userData?.userId)
                }
                if (photos.isNotEmpty()) {
                    reviewViewModel.rejectPhotos(photos, userData?.userId)
                }
            },
            onBack = {
                reviewScreenVisible.value = false
                interactiveNotificationScreenVisible.value = true
            }
        )
        return
    }
    if (rockReviewVisible.value) {
        RockDictionaryReviewScreen(
            requests = pendingRockRequests,
            onApprove = { request ->
                reviewViewModel.approveRockRequest(request, userData?.userId)
            },
            onReject = { request ->
                reviewViewModel.rejectRockRequest(request, userData?.userId)
            },
            onBack = {
                rockReviewVisible.value = false
                interactiveNotificationScreenVisible.value = true
            }
        )
        return
    }
    if (applicationReviewVisible.value) {
        ApplicationReviewScreen(
            applications = pendingExpertApplications,
            onApprove = { item ->
                reviewViewModel.approveExpertApplication(item, userData?.userId)
            },
            onReject = { item ->
                reviewViewModel.rejectExpertApplication(item, userData?.userId)
            },
            onBack = {
                applicationReviewVisible.value = false
                interactiveNotificationScreenVisible.value = true
            }
        )
        return
    }
    if (helpRequestListVisible.value) {
        HelpRequestListScreen(
            requests = pendingHelpRequests,
            onRequestClick = { helpRequestDetail.value = it },
            onBack = {
                helpRequestListVisible.value = false
                interactiveNotificationScreenVisible.value = true
            }
        )
        helpRequestDetail.value?.let { request ->
            HelpRequestDetailDialog(
                request = request,
                onReply = { replyText ->
                    reviewViewModel.replyHelpRequest(request, replyText, userData?.userId)
                    helpRequestDetail.value = null
                },
                onClose = { helpRequestDetail.value = null }
            )
        }
        return
    }
    val chatUiState by chatViewModel.uiState.collectAsState()
    if (chatUiState.activeConversationId != null) {
        ChatScreen(
            otherDisplayName = chatUiState.activeOtherDisplayName,
            otherProfilePictureUrl = chatUiState.activeOtherProfilePictureUrl,
            currentUserProfilePictureUrl = userData?.profilePictureUrl.orEmpty(),
            messages = chatUiState.messages,
            currentUserId = chatUiState.currentUserId,
            onBack = chatViewModel::closeConversation,
            onSendMessage = chatViewModel::sendMessage,
            onDeleteMessage = chatViewModel::deleteMessage
        )
        return
    }
    if (friendsScreenVisible.value) {
        Box(modifier = Modifier.fillMaxSize()) {
            FriendsScreen(
                viewModel = friendsViewModel,
                onBack = { friendsScreenVisible.value = false },
                onStartChatInInbox = { friend ->
                    chatViewModel.startChat(friend)
                    friendsScreenVisible.value = false
                }
            )
            TopBannerHost(
                banner = friendsBanner.value,
                onDismiss = { friendsBanner.value = null },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        LaunchedEffect(Unit) {
            friendsViewModel.banners.collect { friendsBanner.value = it }
        }
        return
    }
    if (interactiveNotificationScreenVisible.value) {
    InteractiveNotificationScreen(
            showContentReviewTab = isVerifiedExpert || isAdmin || isFaqAdmin,
            onBack = { interactiveNotificationScreenVisible.value = false },
            notifications = notifications,
            onMarkRead = { id -> reviewViewModel.markNotificationRead(id) },
            onClearAll = { reviewViewModel.clearNotifications() },
            onDelete = { n -> reviewViewModel.deleteNotification(n.id) },
            onGoToPage = { n ->
                reviewViewModel.markNotificationRead(n.id)
                interactiveNotificationScreenVisible.value = false
                val targetTab = n.targetTab?.trim()?.lowercase()
                when {
                    n.type == "help_reply" -> helpReplyPreviewNotification.value = n
                    n.id == "pending_comments" || n.id == "pending_photos" -> {
                        reviewScreenVisible.value = true
                    }
                    n.id == "pending_rock_dictionary" -> rockReviewVisible.value = true
                    n.id == "pending_help_requests" -> helpRequestListVisible.value = true
                    n.id == "pending_expert_applications" -> applicationReviewVisible.value = true
                    n.type == "friend_request" || targetTab == "friends" -> {
                        friendsScreenVisible.value = true
                    }
                    else -> onGoToPage(n)
                }
            },
            isVerifiedExpert = isVerifiedExpert,
            isAdmin = isAdmin,
            isFaqAdmin = isFaqAdmin,
            pendingCommentCount = pendingReviewItems.size,
            pendingImageCount = pendingPhotos.size,
            pendingRockCount = pendingRockRequests.size,
            pendingHelpCount = pendingHelpRequests.size,
            pendingApplicationCount = pendingExpertApplications.size,
            onOpenCommentReview = {
                interactiveNotificationScreenVisible.value = false
                reviewScreenVisible.value = true
            },
            onOpenImageReview = {
                interactiveNotificationScreenVisible.value = false
                reviewScreenVisible.value = true
            },
            onOpenRockReview = {
                interactiveNotificationScreenVisible.value = false
                rockReviewVisible.value = true
            },
            onOpenHelpRequests = {
                interactiveNotificationScreenVisible.value = false
                helpRequestListVisible.value = true
            },
            onOpenApplicationReview = {
                interactiveNotificationScreenVisible.value = false
                applicationReviewVisible.value = true
            }
        )
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onProfileClick() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayName = when {
                    userData == null -> "Loading..."
                    userData.firstName.isBlank() && userData.lastName.isBlank() ->
                        userData.email.ifBlank { "Unknown User" }
                    else -> listOf(userData.firstName, userData.lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                AvatarOrInitials(
                    profilePictureUrl = userData?.profilePictureUrl.orEmpty(),
                    displayName = displayName
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Text(
                        text = "View profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark.copy(alpha = 0.7f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFFE8EAF1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { friendsScreenVisible.value = true },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Friends",
                        tint = TextDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        HelpDeskCard(
            onAgentClick = {
                showAgentDialog.value = true
                requestFormVisible.value = true
            },
            onFaqClick = { showFaqDialog.value = true }
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Message area: fixed Interactive notification row + private message list
        val latestPreview = notifications.firstOrNull()?.let { n ->
            if (n.id == "pending_comments") {
                val commentCount = pendingComments.size
                "Content review: $commentCount comment(s) pending"
            } else {
                "${n.title}: ${n.message.take(40)}"
            }
        }.orEmpty()
        val unreadNotificationCount = notifications.count { n -> !n.isRead }
        InteractiveNotificationRow(
            latestPreview = latestPreview,
            unreadCount = unreadNotificationCount,
            onClick = { interactiveNotificationScreenVisible.value = true }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            PrivateMessageArea(viewModel = chatViewModel)
        }
        }

        TopBannerHost(
            banner = helpRequestBanner.value,
            onDismiss = { helpRequestBanner.value = null },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )

        if (isReviewLoading &&
            notifications.isEmpty() &&
            pendingComments.isEmpty() &&
            pendingPhotos.isEmpty() &&
            pendingRockRequests.isEmpty() &&
            pendingHelpRequests.isEmpty() &&
            pendingExpertApplications.isEmpty()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TextDark)
            }
        }
    }

    HelpDeskAgentDialog(
        visible = showAgentDialog.value,
        initiallyShowForm = requestFormVisible.value,
        onClose = {
            showAgentDialog.value = false
            requestFormVisible.value = false
        },
        onFaqRequest = { showFaqDialog.value = true },
        onSendRequest = { subject, details ->
            val displayName = listOf(userData?.firstName, userData?.lastName)
                .filter { !it.isNullOrBlank() }
                .joinToString(" ")
                .ifBlank { null }
            reviewViewModel.submitHelpRequest(
                userData?.userId,
                displayName,
                subject,
                details
            )
            helpRequestBanner.value = UiBanner(
                text = "Help request sent successfully. Please wait for an admin reply.",
                type = UiBannerType.Success
            )
            showAgentDialog.value = false
            requestFormVisible.value = false
        }
    )

    helpRequestDetail.value?.let { request ->
        HelpRequestDetailDialog(
            request = request,
            onReply = { replyText ->
                reviewViewModel.replyHelpRequest(request, replyText, userData?.userId)
                helpRequestDetail.value = null
            },
            onClose = { helpRequestDetail.value = null }
        )
    }
    helpReplyPreviewNotification.value?.let { notification ->
        HelpReplyPreviewDialog(
            title = notification.title,
            message = notification.message,
            onClose = { helpReplyPreviewNotification.value = null }
        )
    }


        HelpDeskFaqDialog(
            visible = showFaqDialog.value,
            onClose = { showFaqDialog.value = false },
            faqViewModel = faqViewModel,
            isFaqAdmin = isFaqAdmin
        )
}

@Composable
private fun ReviewEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.7f)
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "View",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun RockDictionaryReviewScreen(
    requests: List<RockDictionaryRequest>,
    onApprove: (RockDictionaryRequest) -> Unit,
    onReject: (RockDictionaryRequest) -> Unit,
    onBack: () -> Unit
) {
    val selectedRequest = remember { mutableStateOf<RockDictionaryRequest?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RockReviewHeader(onBack = onBack)
            if (requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pending rock dictionary requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    requests.forEachIndexed { index, request ->
                        RockDictionaryReviewCard(
                            request = request,
                            number = index + 1,
                            onClick = { selectedRequest.value = request }
                        )
                    }
                }
            }
        }
    }

    selectedRequest.value?.let { request ->
        RockDictionaryReviewDialog(
            request = request,
            onApprove = {
                onApprove(request)
                selectedRequest.value = null
            },
            onReject = {
                onReject(request)
                selectedRequest.value = null
            },
            onClose = { selectedRequest.value = null }
        )
    }
}

@Composable
private fun ApplicationReviewScreen(
    applications: List<ExpertApplicationReviewItem>,
    onApprove: (ExpertApplicationReviewItem) -> Unit,
    onReject: (ExpertApplicationReviewItem) -> Unit,
    onBack: () -> Unit
) {
    val selectedApplication = remember { mutableStateOf<ExpertApplicationReviewItem?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDark
                        )
                    }
                    Column {
                        Text(
                            text = "Application Review",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Text(
                            text = "Admin Review",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDark.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            if (applications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pending expert applications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    applications.forEachIndexed { index, item ->
                        ApplicationReviewCard(
                            item = item,
                            number = index + 1,
                            onClick = { selectedApplication.value = item }
                        )
                    }
                }
            }
        }
    }

    selectedApplication.value?.let { item ->
        ApplicationReviewDialog(
            item = item,
            onApprove = {
                onApprove(item)
                selectedApplication.value = null
            },
            onReject = {
                onReject(item)
                selectedApplication.value = null
            },
            onClose = { selectedApplication.value = null }
        )
    }
}

@Composable
private fun ApplicationReviewCard(
    item: ExpertApplicationReviewItem,
    number: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Application No. $number: ${item.fullName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFEDEDED))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.expertise.ifBlank { "Expert" },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDark
                    )
                }
            }
            Text(
                text = "User: ${item.userDisplayName.ifBlank { item.userEmail }}",
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.8f)
            )
            Text(
                text = "Portfolio: ${item.portfolioLink}",
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ApplicationReviewDialog(
    item: ExpertApplicationReviewItem,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Review expert application",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = "User: ${item.userDisplayName.ifBlank { item.userEmail }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Full Name: ${item.fullName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark
                )
                Text(
                    text = "Expertise: ${item.expertise}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark
                )
                Text(
                    text = "Years of experience: ${item.yearsOfExperience}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark
                )
                Text(
                    text = "Portfolio: ${item.portfolioLink}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark
                )
                Text(
                    text = "Notes from applicant:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = item.notes.ifBlank { "No additional notes." },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject")
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Rock1)
                    ) {
                        Text("Approve")
                    }
                }
            }
        }
    }
}

@Composable
private fun RockReviewHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextDark
                )
            }
            Column {
                Text(
                    text = "Rock Dictionary Review",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = "Admin Review",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun RockDictionaryReviewCard(
    request: RockDictionaryRequest,
    number: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Request No. $number: ${request.requestType} • ${request.rockName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFEDEDED))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = request.requestType.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            }
            Text(
                text = "Submitted by: ${request.submittedBy}",
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.7f)
            )
            Text(
                text = "Submitted on: ${formatDate(request.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.7f)
            )
            Text(
                text = request.rockLocation,
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun RockDictionaryReviewDialog(
    request: RockDictionaryRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${request.requestType} Request",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextDark
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color(0xFFF1F1F1)),
                    contentAlignment = Alignment.Center
                ) {
                    if (request.imageUrl.isBlank()) {
                        Text(
                            text = "No image provided",
                            color = TextDark.copy(alpha = 0.7f)
                        )
                    } else {
                        AsyncImage(
                            model = request.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Text(
                    text = "Rock Name: ${request.rockName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark
                )
                Text(
                    text = "Rarity: ${request.rockRarity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark
                )
                Text(
                    text = "Location: ${request.rockLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark
                )
                Text(
                    text = "Description: ${request.rockDesc}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.8f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("APPROVE")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2E2E)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("REJECT")
                    }
                }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun NotificationsDialog(
    notifications: List<InboxNotification>,
    onMarkRead: (String) -> Unit,
    onClearAll: () -> Unit,
    onGoToPage: (InboxNotification) -> Unit,
    onDelete: (InboxNotification) -> Unit,
    showGoToPage: Boolean,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Notifications",
                            tint = TextDark
                        )
                    }
                }

                if (notifications.isEmpty()) {
                    Text(
                        text = "No notifications yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark.copy(alpha = 0.6f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications) { notification ->
                            var menuExpanded by remember(notification.id) { mutableStateOf(false) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onMarkRead(notification.id)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notification.isRead) {
                                        Color(0xFFF4F4F4)
                                    } else {
                                        Color(0xFFF9F9F9)
                                    }
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = notification.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextDark
                                        )
                                        Text(
                                            text = notification.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextDark.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = notification.date,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDark.copy(alpha = 0.6f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (!notification.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 10.dp)
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(TextDark.copy(alpha = 0.6f))
                                            )
                                        }
                                        if (showGoToPage) {
                                            Box {
                                                IconButton(onClick = { menuExpanded = true }) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Notification actions",
                                                        tint = TextDark.copy(alpha = 0.85f)
                                                    )
                                                }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Go to Page") },
                                                enabled = hasGoToPageAction(notification),
                                                onClick = {
                                                    menuExpanded = false
                                                    onGoToPage(notification)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextDark)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    menuExpanded = false
                                                    onDelete(notification)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = TextDark)
                                                }
                                            )
                                        }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (notifications.isNotEmpty()) {
                    Button(
                        onClick = onClearAll,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear all")
                    }
                }
            }
        }
    }
}

// True if this notification has a "Go to Page" action (by targetTab/targetLocationId, id, type, or title).
private fun hasGoToPageAction(notification: InboxNotification): Boolean {
    val targetTab = notification.targetTab?.trim()?.lowercase()
    if (!targetTab.isNullOrBlank() || !notification.targetLocationId.isNullOrBlank()) return true
    if (notification.id in listOf("pending_comments", "pending_photos", "pending_rock_dictionary", "pending_help_requests", "pending_expert_applications")) return true
    if (notification.type in listOf("help_reply", "rock_dictionary_approved", "friend_request")) return true
    if (notification.title == "Rock Dictionary Update Approved") return true
    return false
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    return SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(timestamp))
}

@Composable
private fun HelpRequestListScreen(
    requests: List<HelpRequest>,
    onRequestClick: (HelpRequest) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDark
                        )
                    }
                    Text(
                        text = "Help Requests",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                }
            }
            if (requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pending help requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(requests) { request ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRequestClick(request) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = request.subject,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Text(
                                    text = "From: ${request.userDisplayName.ifBlank { "Unknown" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatDate(request.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDark.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = request.details.take(120).let { if (request.details.length > 120) "$it…" else it },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpRequestDetailDialog(
    request: HelpRequest,
    onReply: (replyText: String) -> Unit,
    onClose: () -> Unit
) {
    var replyText by remember(request.id) { mutableStateOf("") }
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Help Request",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextDark)
                    }
                }
                Text(
                    text = request.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = "From: ${request.userDisplayName.ifBlank { "Unknown" }} · ${formatDate(request.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.7f)
                )
                Text(
                    text = request.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDark
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Text(
                    text = "Your reply (required)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                var replyError by remember(request.id) { mutableStateOf(false) }
                TextField(
                    value = replyText,
                    onValueChange = {
                        replyText = it
                        replyError = false
                    },
                    label = { Text("Reply") },
                    isError = replyError,
                    supportingText = if (replyError) {
                        { Text("Reply is required", color = Color(0xFFB00020)) }
                    } else null,
                    placeholder = { Text("Type your reply…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val text = replyText.trim()
                            if (text.isEmpty()) {
                                replyError = true
                                return@Button
                            }
                            onReply(text)
                        }
                    ) {
                        Text("Send Reply")
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpReplyPreviewDialog(
    title: String,
    message: String,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextDark)
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDark
                )
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun HelpDeskCard(onAgentClick: () -> Unit, onFaqClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Need help navigating Rockland?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            Text(
                text = "Talk to Rocky or browse the FAQ to learn more about the app.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onFaqClick,
                    modifier = Modifier
                        .weight(0.65f)
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEFEFF5),
                        contentColor = TextDark
                    )
                ) {
                    Text("See FAQ")
                }
                OutlinedButton(
                    onClick = onAgentClick,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF2A2A2A),
                contentColor = Color.White
                    )
                ) {
                    Text("Enter your question")
                }
            }
        }
    }
}

@Composable
private fun HelpDeskAgentDialog(
    visible: Boolean,
    initiallyShowForm: Boolean,
    onClose: () -> Unit,
    onFaqRequest: () -> Unit,
    onSendRequest: (subject: String, details: String) -> Unit = { _, _ -> }
) {
    if (!visible) return

    val conversation = remember { mutableStateListOf(
        "Rocky: Hey, I can help you with Rockland questions.",
        "Rocky: Tap FAQ for quick tips or send your own question."
    ) }
    val showForm = remember { mutableStateOf(initiallyShowForm) }
    LaunchedEffect(true) {
        if (visible) {
            showForm.value = initiallyShowForm
        }
    }
    val subject = remember { mutableStateOf("") }
    val details = remember { mutableStateOf("") }
    val subjectError = remember { mutableStateOf(false) }
    val detailsError = remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F3F7)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Rocky Help Desk",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextDark
                        )
                        Text(
                            text = "Can ask for assistance here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDark.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFFDBE2F2), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Help Desk",
                            tint = TextDark
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFDEE0E5))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    conversation.forEach { line ->
                        Text(
                            text = line,
                            color = TextDark,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onFaqRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDDE5F3),
                            contentColor = TextDark
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("See FAQ")
                    }
                }

                if (showForm.value) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = subject.value,
                            onValueChange = {
                                subject.value = it
                                subjectError.value = false
                            },
                            label = { Text("Subject (required)") },
                            isError = subjectError.value,
                            supportingText = if (subjectError.value) {
                                { Text("Subject is required", color = Color(0xFFB00020)) }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        TextField(
                            value = details.value,
                            onValueChange = {
                                details.value = it
                                detailsError.value = false
                            },
                            label = { Text("Describe your question (required)") },
                            isError = detailsError.value,
                            supportingText = if (detailsError.value) {
                                { Text("Please describe your question", color = Color(0xFFB00020)) }
                            } else null,
                            placeholder = { Text("Describe your question...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                showForm.value = false
                                subject.value = ""
                                details.value = ""
                                subjectError.value = false
                                detailsError.value = false
                            }) {
                                Text("Cancel")
                            }
                            Button(onClick = {
                                val s = subject.value.trim()
                                val d = details.value.trim()
                                subjectError.value = s.isEmpty()
                                detailsError.value = d.isEmpty()
                                if (s.isEmpty() || d.isEmpty()) return@Button
                                onSendRequest(s, d)
                                conversation.add("You: $s")
                                conversation.add("Details: $d")
                                subject.value = ""
                                details.value = ""
                                subjectError.value = false
                                detailsError.value = false
                                showForm.value = false
                            }) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpDeskFaqDialog(
    visible: Boolean,
    onClose: () -> Unit,
    faqViewModel: FaqViewModel,
    isFaqAdmin: Boolean
) {
    if (!visible) return
    val faqs by faqViewModel.faqs.collectAsState()
    val loading by faqViewModel.loading.collectAsState()
    val addEditTarget = remember { mutableStateOf<FaqItem?>(null) }

    LaunchedEffect(true) {
        if (visible) faqViewModel.loadFaqs(activeOnly = !isFaqAdmin)
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FAQ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close FAQ",
                            tint = TextDark
                        )
                    }
                }
                if (isFaqAdmin) {
                    Button(
                        onClick = { addEditTarget.value = FaqItem(order = faqs.size) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add FAQ")
                    }
                }
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading…", style = MaterialTheme.typography.bodySmall, color = TextDark.copy(alpha = 0.7f))
                    }
                } else if (faqs.isEmpty()) {
                    Text(
                        text = "No FAQs yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark.copy(alpha = 0.7f)
                    )
                } else {
                    faqs.forEach { item ->
                        if (!isFaqAdmin) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = item.question,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Text(
                                    text = item.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark.copy(alpha = 0.8f)
                                )
                                HorizontalDivider(color = Color(0xFFE0E0E0))
                            }
                        } else {
                            var menuExpanded by remember(item.id) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.question,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = item.answer,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextDark.copy(alpha = 0.8f)
                                    )
                                    HorizontalDivider(color = Color(0xFFE0E0E0))
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "FAQ actions",
                                            tint = TextDark
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                menuExpanded = false
                                                addEditTarget.value = item
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = TextDark)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                menuExpanded = false
                                                faqViewModel.deleteFaq(item)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFF8B2E2E))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    addEditTarget.value?.let { target ->
        AddEditFaqDialog(
            existing = target,
            onDismiss = { addEditTarget.value = null },
            onSave = { question, answer, order, isActive ->
                if (target.id.isBlank()) {
                    faqViewModel.addFaq(question, answer, order, isActive)
                } else {
                    faqViewModel.updateFaq(target, question, answer, order, isActive)
                }
                addEditTarget.value = null
            }
        )
    }
}

@Composable
private fun AddEditFaqDialog(
    existing: FaqItem,
    onDismiss: () -> Unit,
    onSave: (question: String, answer: String, order: Int, isActive: Boolean) -> Unit
) {
    val isEdit = existing.id.isNotBlank()
    var question by remember(existing.id) { mutableStateOf(existing.question) }
    var answer by remember(existing.id) { mutableStateOf(existing.answer) }
    var order by remember(existing.id) { mutableIntStateOf(existing.order) }
    var isActive by remember(existing.id) { mutableStateOf(existing.isActive) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEdit) "Edit FAQ" else "Add FAQ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                TextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Order", color = TextDark, style = MaterialTheme.typography.bodyMedium)
                    TextField(
                        value = order.toString(),
                        onValueChange = { order = it.toIntOrNull() ?: order },
                        modifier = Modifier.width(80.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active", color = TextDark, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (question.isNotBlank() && answer.isNotBlank()) {
                                onSave(question, answer, order, isActive)
                            }
                        }
                    ) {
                        Text(if (isEdit) "Save" else "Add")
                    }
                }
            }
        }
    }
}

// Friends Management + Chat: search, requests, friends list, accept setting; chat with friend (FEATURE-12, FEATURE-13).
@Composable
private fun FriendsScreen(
    viewModel: FriendsViewModel,
    onBack: () -> Unit,
    onStartChatInInbox: (FriendRelation) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val previewFriendState = remember { mutableStateOf<FriendRelation?>(null) }

    previewFriendState.value?.let { friend ->
        UserPreviewProfileScreen(
            friend = friend,
            onBack = { previewFriendState.value = null },
            onStartChat = {
                onStartChatInInbox(friend)
            },
            onRemove = {
                viewModel.deleteFriend(friend)
                previewFriendState.value = null
            }
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextDark
                    )
                }
                Text(
                    text = "Friends",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search users to add as friends", color = TextDark.copy(alpha = 0.6f)) },
                singleLine = true
            )
            if (uiState.searchResults.isNotEmpty()) {
                Text(
                    text = "Search results",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults.size) { idx ->
                        val user = uiState.searchResults[idx]
                        SearchResultRow(
                            user = user,
                            onAdd = { viewModel.sendFriendRequest(user) }
                        )
                    }
                }
            }
            Text(
                text = "Contacts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            if (uiState.incomingRequests.isNotEmpty()) {
                Text(
                    text = "Incoming requests",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextDark.copy(alpha = 0.8f)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.incomingRequests) { req ->
                        IncomingRequestRow(
                            request = req,
                            onAccept = { viewModel.acceptFriendRequest(req) },
                            onReject = { viewModel.rejectFriendRequest(req) }
                        )
                    }
                }
            }
            if (uiState.outgoingRequests.isNotEmpty()) {
                Text(
                    text = "Outgoing requests",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextDark.copy(alpha = 0.8f)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.outgoingRequests) { req ->
                        OutgoingRequestRow(
                            request = req,
                            onCancel = { viewModel.deleteFriendRequest(req) }
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.friends.size) { idx ->
                    val friend = uiState.friends[idx]
                    ContactRow(
                        friend = friend,
                        onClick = { previewFriendState.value = friend }
                    )
                }
                if (uiState.friends.isEmpty() && uiState.outgoingRequests.isEmpty() && uiState.incomingRequests.isEmpty()) {
                    item {
                        Text(
                            text = "No friends yet. Search and send requests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDark.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = TextDark.copy(alpha = 0.2f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Accept friend requests",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDark
                )
                Switch(
                    checked = uiState.acceptFriendRequests,
                    onCheckedChange = { viewModel.setAcceptFriendRequests(it) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    user: UserSummary,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AvatarOrInitials(profilePictureUrl = user.profilePictureUrl, displayName = user.displayName)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.displayName, style = MaterialTheme.typography.titleSmall, color = TextDark)
                if (user.email.isNotBlank()) Text(text = user.email, style = MaterialTheme.typography.bodySmall, color = TextDark.copy(alpha = 0.7f))
            }
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
            ) { Text("Add") }
        }
    }
}

@Composable
private fun IncomingRequestRow(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarOrInitials(
                profilePictureUrl = request.fromProfilePictureUrl,
                displayName = request.fromDisplayName.ifBlank { "User" },
                size = 48.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.fromDisplayName.ifBlank { "User" },
                    style = MaterialTheme.typography.titleSmall,
                    color = TextDark
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Decline")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
private fun OutgoingRequestRow(
    request: FriendRequest,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarOrInitials(
                profilePictureUrl = request.toProfilePictureUrl,
                displayName = request.toDisplayName.ifBlank { "User" },
                size = 48.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.toDisplayName.ifBlank { "User" },
                    style = MaterialTheme.typography.titleSmall,
                    color = TextDark
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel request") }
        }
    }
}

@Composable
private fun ContactRow(
    friend: FriendRelation,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AvatarOrInitials(profilePictureUrl = friend.friendProfilePictureUrl, displayName = friend.friendDisplayName)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = friend.friendDisplayName, style = MaterialTheme.typography.titleSmall, color = TextDark)
                if (friend.friendEmail.isNotBlank()) {
                    Text(text = friend.friendEmail, style = MaterialTheme.typography.bodySmall, color = TextDark.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun AvatarOrInitials(
    profilePictureUrl: String,
    displayName: String,
    size: Dp = 40.dp
) {
    val initials = displayName
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first() }
        .joinToString("")
        .ifBlank { "UN" }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFE8EAF1)),
        contentAlignment = Alignment.Center
    ) {
        if (profilePictureUrl.isNotBlank()) {
            AsyncImage(
                model = profilePictureUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserPreviewProfileScreen(
    friend: FriendRelation,
    onBack: () -> Unit,
    onStartChat: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val repo = remember { UserProfileRepository() }
    var loadedUser by remember(friend.friendUserId) { mutableStateOf<UserData?>(null) }
    var loadError by remember(friend.friendUserId) { mutableStateOf<String?>(null) }

    // display achievmenet badges
// display achievement badges
    val awardsRepo = remember { AwardsRepository() }
    var badgeUrls by remember { mutableStateOf<List<String>>(emptyList()) }

// Recompute whenever the loaded profile changes (or its badges list changes)
    LaunchedEffect(loadedUser?.userId, loadedUser?.badges) {
        val badges = loadedUser?.badges.orEmpty()
        if (badges.isEmpty()) {
            badgeUrls = emptyList()
            return@LaunchedEffect
        }

        runCatching {
            val defs = awardsRepo.fetchAchievements()

            val byId = defs.associateBy { it.id.trim() }
            val byTitle = defs.associateBy { it.title.trim() }

            badges.mapNotNull { b ->
                val key = b.trim()
                byId[key]?.imageFile?.takeIf { it.isNotBlank() }
                    ?: byTitle[key]?.imageFile?.takeIf { it.isNotBlank() }
            }
        }.onSuccess { urls ->
            badgeUrls = urls.distinct()
        }.onFailure {
            badgeUrls = emptyList()
        }
    }

    LaunchedEffect(friend.friendUserId) {
        loadError = null
        loadedUser = null
        runCatching { repo.getUserProfile(friend.friendUserId) }
            .onSuccess { loadedUser = it }
            .onFailure { loadError = it.message ?: "Failed to load profile" }
    }

    val effective = loadedUser ?: UserData(
        userId = friend.friendUserId,
        firstName = friend.friendDisplayName.split(" ").firstOrNull().orEmpty(),
        lastName = friend.friendDisplayName.split(" ").drop(1).joinToString(" "),
        email = friend.friendEmail,
        role = "nature_enthusiast"
    )
    val profileName = "${effective.firstName} ${effective.lastName}".trim().ifBlank { friend.friendDisplayName }
    val points = effective.points
    val achievements = effective.achievementsCompleted
    val missions = effective.missionsCompleted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                }
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = TextDark)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Chat") },
                            onClick = {
                                menuExpanded = false
                                onStartChat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            }
                        )
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AvatarOrInitials(
                        profilePictureUrl = effective.profilePictureUrl.ifBlank { friend.friendProfilePictureUrl },
                        displayName = profileName
                    )
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFEDEDED))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Nature Enthusiast",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDark.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = if (effective.joinDate.isNotBlank()) "Joined ${effective.joinDate}" else "Joined —",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark.copy(alpha = 0.6f)
                    )
                    if (!loadError.isNullOrBlank()) {
                        Text(
                            text = loadError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B2E2E)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Progress Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProgressStatCard(
                            label = "Points",
                            value = points.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        ProgressStatCard(
                            label = "Achievements",
                            value = achievements.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        ProgressStatCard(
                            label = "Missions",
                            value = missions.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (badgeUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Badges",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = 3
                            ) {
                                badgeUrls.forEach { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Badge",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF0F0F0))
                                            .padding(4.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextDark)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextDark.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ChatScreen(
    otherDisplayName: String,
    otherProfilePictureUrl: String = "",
    currentUserProfilePictureUrl: String = "",
    messages: List<ChatMessage>,
    currentUserId: String,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                }
                Text(
                    text = otherDisplayName.ifBlank { "Chat" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { selectedMessageId = null })
                    }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true
                ) {
                    items(messages.size) { idx ->
                        val msg = messages[messages.size - 1 - idx]
                        val isOwn = msg.senderId == currentUserId
                        val senderAvatarUrl = if (isOwn) currentUserProfilePictureUrl else otherProfilePictureUrl
                        val senderDisplayName = if (isOwn) "" else otherDisplayName
                        ChatMessageRow(
                            message = msg,
                            isOwn = isOwn,
                            senderAvatarUrl = senderAvatarUrl,
                            senderDisplayName = senderDisplayName,
                            isSelected = selectedMessageId == msg.id,
                            onLongPress = {
                                if (isOwn) {
                                    selectedMessageId = msg.id
                                }
                            },
                            onDelete = {
                                onDeleteMessage(msg.id)
                                selectedMessageId = null
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message", color = TextDark.copy(alpha = 0.6f)) },
                    singleLine = true
                )
                Button(
                    onClick = {
                        onSendMessage(inputText)
                        inputText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    isOwn: Boolean,
    senderAvatarUrl: String = "",
    senderDisplayName: String = "",
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    val cardModifier = if (isOwn) {
        Modifier.pointerInput(message.id) {
            detectTapGestures(
                onLongPress = { onLongPress() }
            )
        }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            AvatarOrInitials(profilePictureUrl = senderAvatarUrl, displayName = senderDisplayName)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = if (isOwn) Color(0xFFE8EAF1) else Color.White),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDark,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isOwn && isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        onDelete()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete message",
                            tint = TextDark.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        if (isOwn) {
            Spacer(modifier = Modifier.width(8.dp))
            AvatarOrInitials(profilePictureUrl = senderAvatarUrl, displayName = senderDisplayName)
        }
    }
}

// Full-screen Interactive notification: NE = single list; VE/Admin = tabs [Interactive notification | Content review].
@Composable
private fun InteractiveNotificationScreen(
    showContentReviewTab: Boolean,
    onBack: () -> Unit,
    notifications: List<InboxNotification>,
    onMarkRead: (String) -> Unit,
    onClearAll: () -> Unit,
    onGoToPage: (InboxNotification) -> Unit,
    onDelete: (InboxNotification) -> Unit,
    // Content review tab (VE/Admin only)
    isVerifiedExpert: Boolean = false,
    isAdmin: Boolean = false,
    isFaqAdmin: Boolean = false,
    pendingCommentCount: Int = 0,
    pendingImageCount: Int = 0,
    pendingRockCount: Int = 0,
    pendingHelpCount: Int = 0,
    onOpenCommentReview: () -> Unit = {},
    onOpenImageReview: () -> Unit = {},
    onOpenRockReview: () -> Unit = {},
    onOpenHelpRequests: () -> Unit = {},
    pendingApplicationCount: Int = 0,
    onOpenApplicationReview: () -> Unit = {}
) {
    val interactiveTabIndex = rememberSaveable { mutableIntStateOf(0) }
    val currentTab = interactiveTabIndex.intValue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextDark
                    )
                }
                Text(
                    text = if (showContentReviewTab) "Inbox" else "Interactive notification",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
            if (showContentReviewTab) {
                TabRow(
                    selectedTabIndex = currentTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { interactiveTabIndex.intValue = 0 },
                        text = { Text("Interactive notification", color = TextDark) }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { interactiveTabIndex.intValue = 1 },
                        text = { Text("Content review", color = TextDark) }
                    )
                }
            }
            when {
                !showContentReviewTab -> Column(Modifier.weight(1f)) {
                    NotificationListContent(
                        notifications = notifications,
                        onMarkRead = onMarkRead,
                        onClearAll = onClearAll,
                        onGoToPage = onGoToPage,
                        onDelete = onDelete
                    )
                }
                currentTab == 0 -> Column(Modifier.weight(1f)) {
                    NotificationListContent(
                        notifications = notifications,
                        onMarkRead = onMarkRead,
                        onClearAll = onClearAll,
                        onGoToPage = onGoToPage,
                        onDelete = onDelete
                    )
                }
                else -> Column(Modifier.weight(1f)) {
                    ContentReviewTabContent(
                        isVerifiedExpert = isVerifiedExpert,
                        isAdmin = isAdmin,
                        isFaqAdmin = isFaqAdmin,
                        pendingCommentCount = pendingCommentCount,
                        pendingImageCount = pendingImageCount,
                        pendingRockCount = pendingRockCount,
                        pendingHelpCount = pendingHelpCount,
                        onOpenCommentReview = onOpenCommentReview,
                        onOpenImageReview = onOpenImageReview,
                        onOpenRockReview = onOpenRockReview,
                        onOpenHelpRequests = onOpenHelpRequests,
                        pendingApplicationCount = pendingApplicationCount,
                        onOpenApplicationReview = onOpenApplicationReview
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationListContent(
    notifications: List<InboxNotification>,
    onMarkRead: (String) -> Unit,
    onClearAll: () -> Unit,
    onGoToPage: (InboxNotification) -> Unit,
    onDelete: (InboxNotification) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (notifications.isEmpty()) {
            Text(
                text = "No notifications yet.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications) { notification ->
                    var menuExpanded by remember(notification.id) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMarkRead(notification.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = notification.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Text(
                                    text = notification.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = notification.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDark.copy(alpha = 0.6f)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!notification.isRead) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(TextDark.copy(alpha = 0.6f))
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Notification actions",
                                            tint = TextDark.copy(alpha = 0.85f)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Go to Page") },
                                            enabled = hasGoToPageAction(notification),
                                            onClick = {
                                                menuExpanded = false
                                                onGoToPage(notification)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextDark)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                menuExpanded = false
                                                onDelete(notification)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = TextDark)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onClearAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear all")
            }
        }
    }
}

@Composable
private fun ContentReviewTabContent(
    isVerifiedExpert: Boolean,
    isAdmin: Boolean,
    isFaqAdmin: Boolean,
    pendingCommentCount: Int,
    pendingImageCount: Int,
    pendingRockCount: Int,
    pendingHelpCount: Int,
    onOpenCommentReview: () -> Unit,
    onOpenImageReview: () -> Unit,
    onOpenRockReview: () -> Unit,
    onOpenHelpRequests: () -> Unit,
    pendingApplicationCount: Int,
    onOpenApplicationReview: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isVerifiedExpert) {
            item {
                Text(
                    text = "Content Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
            item {
                val subtitle = "$pendingCommentCount Comments Pending"
                ReviewEntryCard(
                    title = "Content Review",
                    subtitle = subtitle,
                    onClick = onOpenCommentReview
                )
            }
        }
        if (isAdmin) {
            item {
                Spacer(modifier = Modifier.height(if (isVerifiedExpert) 12.dp else 0.dp))
            }
            item {
                Text(
                    text = "Application Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
            item {
                ReviewEntryCard(
                    title = "Application Review",
                    subtitle = "$pendingApplicationCount Applications Pending",
                    onClick = onOpenApplicationReview
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                Text(
                    text = "Rock Dictionary Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
            item {
                ReviewEntryCard(
                    title = "Dictionary Review",
                    subtitle = "$pendingRockCount Requests Pending",
                    onClick = onOpenRockReview
                )
            }
        }
        if (isFaqAdmin) {
            item {
                Spacer(modifier = Modifier.height(if (isVerifiedExpert || isAdmin) 12.dp else 0.dp))
            }
            item {
                Text(
                    text = "Help request response",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }
            item {
                ReviewEntryCard(
                    title = "Help Requests",
                    subtitle = "$pendingHelpCount Requests Pending",
                    onClick = onOpenHelpRequests
                )
            }
        }
    }
}

@Composable
private fun InteractiveNotificationRow(
    latestPreview: String,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EAF1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = TextDark,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interactive notification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = latestPreview.ifBlank { "No new notifications" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.7f)
                )
            }
            // Unread count badge on the right, light red when > 0
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFE57373)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// Stub list for private messages below the fixed Interactive notification row.
@Composable
private fun PrivateMessageArea(
    viewModel: ChatViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var convToDelete by remember { mutableStateOf<ChatConversation?>(null) }

    if (uiState.conversations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            Text(
                text = "No conversations yet.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDark.copy(alpha = 0.6f)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(uiState.conversations.size) { idx ->
            val conv = uiState.conversations[idx]
            ConversationRow(
                conversation = conv,
                onClick = { viewModel.openFromConversation(conv) },
                onLongPress = { convToDelete = conv }
            )
        }
    }

    val dismissDialog: () -> Unit = { convToDelete = null }
    convToDelete?.let { conv ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = dismissDialog,
            title = { Text("Remove from inbox") },
            text = { Text("Remove this chat from your inbox?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(conv.id)
                    dismissDialog()
                }) { Text("Remove", color = Color(0xFFE57373)) }
            },
            dismissButton = {
                TextButton(onClick = dismissDialog) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: ChatConversation,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(conversation.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarOrInitials(profilePictureUrl = conversation.otherProfilePictureUrl, displayName = conversation.otherDisplayName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.otherDisplayName.ifBlank { "Chat" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
                Text(
                    text = conversation.lastMessagePreview.ifBlank { "Tap to open chat" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDark.copy(alpha = 0.7f)
                )
            }
            if (conversation.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFE57373)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

