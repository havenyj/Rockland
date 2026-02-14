package com.example.rockland.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rockland.R
import com.example.rockland.data.datasource.remote.ApplicationStatus
import com.example.rockland.data.datasource.remote.ExpertApplication
import com.example.rockland.data.datasource.remote.UserData
import com.example.rockland.presentation.viewmodel.UserViewModel
import com.example.rockland.ui.theme.BackgroundLight
import com.example.rockland.ui.theme.Rock1
import com.example.rockland.ui.theme.Rock3
import com.example.rockland.ui.theme.TextDark
import com.example.rockland.util.TimeFormatter
import androidx.compose.runtime.DisposableEffect
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun ProfileScreen(
    userViewModel: UserViewModel = viewModel(factory = UserViewModel.Factory()),
    userData: UserData? = null,
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val vmUserData by userViewModel.userData.collectAsState()
    val effectiveUserData = vmUserData ?: userData

    val roleLabel = formatRoleLabel((effectiveUserData?.role).orEmpty().ifBlank { "nature_enthusiast" })
    val normalizedRole = (effectiveUserData?.role).orEmpty().trim().lowercase()
    val isAdmin = normalizedRole in listOf("admin", "user_admin")
    val isVerifiedExpert = normalizedRole == "verified_expert"

    val uid = effectiveUserData?.userId.orEmpty()
    val rocksCollected = remember(uid) { mutableStateOf(0) }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            onDispose { }
        } else {
            val reg = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("collection")
                .addSnapshotListener { snap, _ ->
                    rocksCollected.value = snap?.size() ?: 0
                }
            onDispose { reg.remove() }
        }
    }

    val points = effectiveUserData?.points ?: 0
    val missionsCompleted = effectiveUserData?.missionsCompleted ?: 0
    val achievementsCompleted = effectiveUserData?.achievementsCompleted ?: 0

    val profileName =
        "${effectiveUserData?.firstName.orEmpty()} ${effectiveUserData?.lastName.orEmpty()}".trim().ifBlank { "RockLand User" }

    val expertApp = effectiveUserData?.expertApplication
    val isPending = (expertApp?.statusEnum) == ApplicationStatus.PENDING
    val applyButtonText = if (isPending) "View Application" else "Apply"

    var applicationFullName by remember(profileName) { mutableStateOf(profileName) }
    var expertise by remember { mutableStateOf("") }
    var yearsOfExp by remember { mutableStateOf("") }
    var portfolioLink by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showExpertDialog by remember { mutableStateOf(false) }
    var showPendingDialog by remember { mutableStateOf(false) }

    val bg = Brush.verticalGradient(
        colors = listOf(
            BackgroundLight,
            Rock3.copy(alpha = 0.08f),
            BackgroundLight
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextDark
                    )
                }
                Text(
                    text = "Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = Rock1
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(98.dp)
                        .clip(CircleShape)
                        .background(Rock3.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    val profilePicUrl = effectiveUserData?.profilePictureUrl.orEmpty()
                    if (profilePicUrl.isNotBlank()) {
                        AsyncImage(
                            model = profilePicUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val initials =
                            "${effectiveUserData?.firstName?.firstOrNull() ?: ""}${effectiveUserData?.lastName?.firstOrNull() ?: ""}"
                        Text(
                            text = initials.ifBlank { "?" }.uppercase(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = profileName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                RoleChip(
                    label = roleLabel,
                    isAdmin = isAdmin,
                    isVerifiedExpert = isVerifiedExpert
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${stringResource(R.string.joined)} ${effectiveUserData?.joinDate ?: "—"}",
                    fontSize = 13.sp,
                    color = TextDark.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Progress Overview",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        value = if (isAdmin) "All" else points.toString(),
                        label = "Points"
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        value = if (isAdmin) "All" else achievementsCompleted.toString(),
                        label = "Achievements"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        value = if (isAdmin) "All" else missionsCompleted.toString(),
                        label = "Missions"
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        value = if (isAdmin) "All" else rocksCollected.value.toString(),
                        label = "Rocks Collected"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!isVerifiedExpert && !isAdmin) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPending) Icons.Default.PendingActions else Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFFE8D2B5),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Verified Expert",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isPending)
                            "Your application is under review. You can view or edit your submitted details."
                        else
                            "Prove your expertise to unlock extra permissions for Rock Dictionary contributions.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (isPending) showPendingDialog = true else showExpertDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8D2B5)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = applyButtonText,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        Button(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE7E7)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = Color(0xFFB00020),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.logout),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB00020)
            )
        }
    }

    if (showExpertDialog) {
        VerifiedExpertDialog(
            fullName = applicationFullName,
            expertise = expertise,
            yearsOfExp = yearsOfExp,
            portfolioLink = portfolioLink,
            notes = notes,
            onFullNameChange = { applicationFullName = it },
            onExpertiseChange = { expertise = it },
            onYearsChange = { yearsOfExp = it },
            onPortfolioChange = { portfolioLink = it },
            onNotesChange = { notes = it },
            onDismiss = { showExpertDialog = false },
            onSubmit = {
                userViewModel.submitExpertApplication(
                    fullName = applicationFullName,
                    expertise = expertise,
                    yearsOfExperience = yearsOfExp,
                    portfolioLink = portfolioLink,
                    notes = notes
                )
                showExpertDialog = false
            }
        )
    }

    if (showPendingDialog && expertApp != null) {
        val submittedAtText =
            expertApp.submittedAt?.toDate()?.time?.let(TimeFormatter::formatLocal) ?: "—"

        PendingExpertApplicationDialog(
            application = expertApp,
            submittedAtText = submittedAtText,
            onDismiss = { showPendingDialog = false },
            onEdit = {
                applicationFullName = expertApp.fullName.ifBlank { applicationFullName }
                expertise = expertApp.expertise
                yearsOfExp = expertApp.yearsOfExperience
                portfolioLink = expertApp.portfolioLink
                notes = expertApp.notes
                showPendingDialog = false
                showExpertDialog = true
            }
        )
    }
}

@Composable
private fun RoleChip(
    label: String,
    isAdmin: Boolean,
    isVerifiedExpert: Boolean
) {
    val chipBg = when {
        isAdmin -> Color(0xFFE9F2FF)
        isVerifiedExpert -> Color(0xFFE9FFF1)
        else -> Rock1.copy(alpha = 0.14f)
    }
    val chipFg = when {
        isAdmin -> Color(0xFF1565C0)
        isVerifiedExpert -> Color(0xFF2E7D32)
        else -> TextDark
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(chipBg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isAdmin) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = chipFg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            } else if (isVerifiedExpert) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = chipFg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = chipFg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }

            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = chipFg
            )
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Rock1.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextDark.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatRoleLabel(role: String): String {
    return when (role.lowercase()) {
        "verified_expert" -> "Verified Expert"
        "admin" -> "Admin"
        "user_admin" -> "User Admin"
        else -> "Nature Enthusiast"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerifiedExpertDialog(
    fullName: String,
    expertise: String,
    yearsOfExp: String,
    portfolioLink: String,
    notes: String,
    onFullNameChange: (String) -> Unit,
    onExpertiseChange: (String) -> Unit,
    onYearsChange: (String) -> Unit,
    onPortfolioChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val fullNameError = fullName.isBlank()
    val expertiseError = expertise.isBlank()
    val yearsError = yearsOfExp.isBlank() || yearsOfExp.toIntOrNull() == null
    val portfolioError = portfolioLink.isBlank()
    val isFormValid = !fullNameError && !expertiseError && !yearsError && !portfolioError

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Rock1,
        unfocusedBorderColor = TextDark.copy(alpha = 0.25f),
        focusedLabelColor = Rock1,
        unfocusedLabelColor = TextDark.copy(alpha = 0.55f),
        cursorColor = Rock1
    )

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Verified Expert Application",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Fill in the required fields so admins can verify your credentials.",
                    fontSize = 13.sp,
                    color = TextDark.copy(alpha = 0.72f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = onFullNameChange,
                    label = { Text("Full Name (Required)") },
                    isError = fullNameError,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (fullNameError) Text("Required") },
                    colors = tfColors
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = expertise,
                    onValueChange = onExpertiseChange,
                    label = { Text("Area of Expertise (Required)") },
                    isError = expertiseError,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (expertiseError) Text("Required") },
                    colors = tfColors
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = yearsOfExp,
                    onValueChange = onYearsChange,
                    label = { Text("Years of experience (Required)") },
                    isError = yearsError,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (yearsError) Text(if (yearsOfExp.isBlank()) "Required" else "Must be a number")
                    },
                    colors = tfColors
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = portfolioLink,
                    onValueChange = onPortfolioChange,
                    label = { Text("Portfolio Link (Required)") },
                    isError = portfolioError,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { if (portfolioError) Text("Required") },
                    colors = tfColors,
                    trailingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = Rock1)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notes for admin (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = tfColors,
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel", color = TextDark) }

                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Rock1),
                        shape = RoundedCornerShape(14.dp),
                        enabled = isFormValid
                    ) {
                        Text("Submit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingExpertApplicationDialog(
    application: ExpertApplication,
    submittedAtText: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PendingActions,
                        contentDescription = null,
                        tint = Rock1,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Application Under Review",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Admins are reviewing your submission. You can edit and resubmit if needed.",
                    fontSize = 13.sp,
                    color = TextDark.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                ApplicationDetailRow(label = "Full Name", value = application.fullName)
                ApplicationDetailRow(label = "Expertise", value = application.expertise)
                ApplicationDetailRow(label = "Years of experience", value = application.yearsOfExperience)
                ApplicationDetailRow(label = "Portfolio", value = application.portfolioLink)
                ApplicationDetailRow(label = "Notes for admin", value = application.notes.ifBlank { "—" })
                ApplicationDetailRow(label = "Submitted at", value = submittedAtText)

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Rock1),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Edit", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Rock1.copy(alpha = 0.14f), contentColor = TextDark),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(text = "Close", fontWeight = FontWeight.Bold, color = TextDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationDetailRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark.copy(alpha = 0.65f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = TextDark
        )
    }
}
