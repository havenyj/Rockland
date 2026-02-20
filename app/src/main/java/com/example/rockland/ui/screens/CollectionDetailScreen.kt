// Shows a detailed view for a single rock in the collection.
// Keeps all UI state inside Compose for this screen.
package com.example.rockland.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.example.rockland.data.model.CollectionItem
import com.example.rockland.data.repository.Rock
import com.example.rockland.data.repository.RockRepository
import com.example.rockland.presentation.viewmodel.CollectionViewModel
import com.example.rockland.ui.theme.Rock1
import com.example.rockland.ui.theme.Rock3
import com.example.rockland.ui.theme.TextDark

private object RockDictionaryCache {
    // Cache dictionary lookups to avoid repeated requests when tabs change.
    val byId = mutableMapOf<Int, Rock?>()
    val byNameKey = mutableMapOf<String, Rock?>()
}

// Detail screen for a single collection item.
@Composable
fun CollectionDetailScreen(
    item: CollectionItem,
    collectionViewModel: CollectionViewModel,
    onBack: () -> Unit,
    onSaveNotes: (customId: String, locationLabel: String, notes: String) -> Unit,
    onItemUpdated: (CollectionItem) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Rock Info, 1 = Note Details
    var showEditSheet by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        collectionViewModel.recordReadRockInfo()
    }

    // Cache dictionary lookup state at the parent level so tab switching doesn't reset it.
    val rockRepository = remember { RockRepository() }
    var dictRock by remember(item.rockId, item.rockName) { mutableStateOf<Rock?>(null) }
    var isDictLoading by remember(item.rockId, item.rockName) { mutableStateOf(true) }
    var dictErrorMsg by remember(item.rockId, item.rockName) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.rockId, item.rockName) {
        isDictLoading = true
        dictErrorMsg = null

        val idInt = item.rockId.toIntOrNull()
        val nameKey = item.rockName.trim().lowercase()

        // Fast path: serve from in-memory cache.
        val cached = when {
            idInt != null && RockDictionaryCache.byId.containsKey(idInt) -> RockDictionaryCache.byId[idInt]
            RockDictionaryCache.byNameKey.containsKey(nameKey) -> RockDictionaryCache.byNameKey[nameKey]
            else -> null
        }
        if (cached != null) {
            dictRock = cached
            isDictLoading = false
            return@LaunchedEffect
        }

        try {
            val result = if (idInt != null) {
                rockRepository.getRockById(idInt)
            } else {
                rockRepository.getRockByName(item.rockName.trim())
            }
            dictRock = result
            if (idInt != null) RockDictionaryCache.byId[idInt] = result
            RockDictionaryCache.byNameKey[nameKey] = result
            if (result == null) {
                dictErrorMsg = "No dictionary entry found for ${item.rockName}."
            }
        } catch (e: Exception) {
            dictErrorMsg = e.message ?: "Failed to load rock dictionary."
        } finally {
            isDictLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with back arrow and title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "My Rocks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Tab switch between rock info and notes
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Rock Information") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Personal Notes") }
                )
            }

            when (selectedTab) {
                0 -> RockInfoTab(
                    item = item,
                    dictRock = dictRock,
                    isLoading = isDictLoading,
                    errorMsg = dictErrorMsg
                )
                1 -> NoteDetailsTab(item = item, onEditClick = { showEditSheet = true })
            }
        }

        if (showEditSheet) {
            EditNotesSheet(
                item = item,
                onDismiss = { showEditSheet = false },
                onSave = { customId, locationLabel, notes ->
                    onSaveNotes(customId, locationLabel, notes)
                    showEditSheet = false
                },
                collectionViewModel = collectionViewModel,
                onItemUpdated = onItemUpdated
            )
        }
    }
}

@Composable
private fun RockInfoTab(
    item: CollectionItem,
    dictRock: Rock?,
    isLoading: Boolean,
    errorMsg: String?
) {
    val headerImageModel: Any? = when {
        !dictRock?.rockImageUrl.isNullOrBlank() -> dictRock.rockImageUrl
        !item.thumbnailUrl.isNullOrBlank() -> item.thumbnailUrl
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Rock gallery (dictionary image)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Rock3.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when {
                    headerImageModel != null -> {
                        AsyncImage(
                            model = headerImageModel,
                            contentDescription = "${item.rockName} image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    isLoading -> {
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                    }
                    else -> {
                        Text(
                            text = "Rock Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                    }
                }
            }
        }

        Text(
            text = item.rockName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Rock Information", fontWeight = FontWeight.SemiBold, color = TextDark)
            if (isLoading) {
                Text(text = "Rarity: Loading...", color = TextDark)
                Text(text = "Location: Loading...", color = TextDark)
                Text(text = "Description: Loading...", color = TextDark)
            } else if (errorMsg != null) {
                Text(text = "Rarity: Unknown", color = TextDark)
                Text(text = "Location: Unknown", color = TextDark)
                Text(text = "Description: ${errorMsg.orEmpty()}", color = Color.Red)
            } else {
                Text(text = "Rarity: ${dictRock?.rockRarity ?: "Unknown"}", color = TextDark)
                Text(text = "Location: ${dictRock?.rockLocation ?: "Unknown"}", color = TextDark)
                Text(
                    text = "Description: ${dictRock?.rockDesc ?: "No description."}",
                    color = TextDark
                )
            }
        }
    }
}

@Composable
private fun NoteDetailsTab(
    item: CollectionItem,
    onEditClick: () -> Unit
) {
    val userImages = remember(item.userImageUrls, item.imageUrls) { item.effectiveUserImageUrls() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User photos gallery (from user's collection doc)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Rock3.copy(alpha = 0.2f))
        ) {
            if (userImages.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Rock Gallery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    userImages.forEach { url ->
                        Box(
                            modifier = Modifier
                                .size(196.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "User rock photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Personal Notes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextDark
        )

        Text(
            text = item.notes.ifBlank { "No notes yet." },
            style = MaterialTheme.typography.bodyMedium,
            color = TextDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onEditClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Rock1)
        ) {
            Text(text = "Click here to jot down your notes!", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EditNotesSheet(
    item: CollectionItem,
    onDismiss: () -> Unit,
    onSave: (customId: String, locationLabel: String, notes: String) -> Unit,
    collectionViewModel: CollectionViewModel,
    onItemUpdated: (CollectionItem) -> Unit
) {
    var customId by remember { mutableStateOf(item.customId) }
    var location by remember { mutableStateOf(item.locationLabel) }
    var notes by remember { mutableStateOf(item.notes) }
    var userImages by remember(item.userImageUrls, item.imageUrls) {
        mutableStateOf(item.effectiveUserImageUrls())
    }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
                collectionViewModel.uploadUserPhotos(
                    itemId = item.id,
                    uris = uris,
                    context = context,
                    onUploaded = { uploaded ->
                        if (uploaded.isNotEmpty()) {
                            val merged = (userImages + uploaded).distinct()
                            userImages = merged
                            onItemUpdated(item.copy(userImageUrls = merged))
                        }
                    }
                )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Personal Notes about " + item.rockName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Photos",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    userImages.forEach { url ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Rock3.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "User photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = {
                                    collectionViewModel.removeUserPhotos(
                                        itemId = item.id,
                                        urls = listOf(url)
                                    ) { removed ->
                                        if (removed.isNotEmpty()) {
                                            val updated = userImages.filterNot { it == url }
                                            userImages = updated
                                            onItemUpdated(item.copy(userImageUrls = updated))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove photo"
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .background(Rock3.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", fontSize = 24.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .background(Rock3.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSave(customId, location, notes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rock1)
                ) {
                    Text(text = "Save Changes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

