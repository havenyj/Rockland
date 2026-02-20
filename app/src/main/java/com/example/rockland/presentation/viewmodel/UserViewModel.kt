// Keeps the auth/profile wiring inside the ViewModel layer for Compose screens.
// Lets the UI observe StateFlows without calling Firebase directly.
package com.example.rockland.presentation.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rockland.data.auth.AuthRepository
import com.example.rockland.data.auth.AuthResult
import com.example.rockland.data.auth.FirebaseAuthRepository
import com.example.rockland.data.auth.toUiMessage
import com.example.rockland.data.datasource.remote.UserData
import com.example.rockland.presentation.model.UiBanner
import com.example.rockland.presentation.model.UiBannerType
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import com.example.rockland.data.repository.UserProfileRepository

// Manages Firebase auth flows, profile loading, and banners for the UI.
class UserViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository.getInstance(),
    private val userRepo: UserProfileRepository = UserProfileRepository()
) : ViewModel() {

    // Banner messages the UI collects to show alerts.
    private val _banners = MutableSharedFlow<UiBanner>(extraBufferCapacity = 1)
    val banners = _banners.asSharedFlow()

    // banner message for submission of expert application
    sealed class ExpertUiEvent {
        object Submitted : ExpertUiEvent()
    }

    private val _expertUiEvents = MutableSharedFlow<ExpertUiEvent>(extraBufferCapacity = 1)
    val expertUiEvents = _expertUiEvents.asSharedFlow()

    // Stored profile data for Compose screens.
    private val _userData = MutableStateFlow<UserData?>(null)
    val userData: StateFlow<UserData?> = _userData.asStateFlow()

    // Shows when an async auth/profile task runs.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Auth state
    val currentUser: StateFlow<FirebaseUser?> =
        authRepository.authState.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoggedIn: StateFlow<Boolean> =
        currentUser.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Sync profile data whenever authentication changes.
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    android.util.Log.d(
                        "UserViewModel",
                        "Auth state changed: User logged in (${user.uid})"
                    )
                    // Only load user data if we don't have it already
                    if (_userData.value == null || _userData.value?.userId != user.uid) {
                        try {
                            val userData = userRepo.getUserProfile(user.uid)
                            _userData.value = userData
                            android.util.Log.d(
                                "UserViewModel",
                                "User data loaded in init: $userData"
                            )
                            if (userData.role.trim().lowercase() in listOf("admin", "user_admin")) {
                                userRepo.ensureAdminData(user.uid)
                                _userData.value = userRepo.getUserProfile(user.uid)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "UserViewModel",
                                "Error loading user data in init",
                                e
                            )
                        }
                    }
                } else {
                    android.util.Log.d("UserViewModel", "Auth state changed: User logged out")
                    _userData.value = null
                }
            }
        }
    }

    // Registers a user and creates the Firestore profile document.
    fun registerUser(email: String, password: String, firstName: String, lastName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                android.util.Log.d("UserViewModel", "Starting registration for: $email")

                when (val auth = authRepository.signUpWithEmail(email, password)) {
                    is AuthResult.Success -> {
                        val user = auth.value
                        android.util.Log.d("UserViewModel", "Auth user created, uid: ${user.uid}")

                        userRepo.createUserProfile(user, firstName, lastName)
                        android.util.Log.d("UserViewModel", "Firestore profile created")

                        showSuccess("Registration successful!")
                        android.util.Log.d("UserViewModel", "Registration completed successfully")
                    }
                    is AuthResult.Error -> {
                        showError(auth.error.toUiMessage())
                        android.util.Log.e("UserViewModel", "Registration failed: ${auth.error.debugMessage}", auth.error.throwable)
                    }
                }
            } catch (e: Exception) {
                showError("Registration failed.")
                android.util.Log.e("UserViewModel", "Registration error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Signs in so the auth listener can load the profile.
    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                android.util.Log.d("UserViewModel", "Attempting login for: $email")
                when (val auth = authRepository.signInWithEmail(email, password)) {
                    is AuthResult.Success -> {
                        android.util.Log.d("UserViewModel", "Login successful, uid: ${auth.value.uid}")
                        showSuccess("Signed in.")
                        // The auth state listener in init{} will automatically load user data
                    }
                    is AuthResult.Error -> {
                        showError(auth.error.toUiMessage())
                        android.util.Log.e("UserViewModel", "Login failed: ${auth.error.debugMessage}", auth.error.throwable)
                    }
                }
            } catch (e: Exception) {
                showError("Login failed.")
                android.util.Log.e("UserViewModel", "Login error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Signs out and notifies the UI via a banner. Clear userData immediately to avoid
    // recomposition with stale data before auth state updates.
    fun logout() {
        _userData.value = null
        authRepository.signOut()
        showInfo("Signed out.")
    }

    // Reloads cached profile data for the supplied UID.
    private fun loadUserData(userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val data = userRepo.getUserProfile(userId)
                _userData.value = data
            } catch (e: Exception) {
                showError(e.message ?: "Failed to load user data.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Persists name updates, reloads the profile, and triggers a success banner.
    fun updateUserProfile(firstName: String, lastName: String) {
        val userId = currentUser.value?.uid ?: return

        viewModelScope.launch {
            try {
                _isLoading.value = true

                val updates = mapOf(
                    "firstName" to firstName,
                    "lastName" to lastName
                )

                userRepo.updateUserProfile(userId, updates)
                loadUserData(userId)
                showSuccess("Profile updated.")
            } catch (e: Exception) {
                showError(e.message ?: "Failed to update profile.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Upload profile picture to Storage, update Firestore profilePictureUrl, refresh user data.
    fun uploadProfilePicture(uri: android.net.Uri) {
        val userId = currentUser.value?.uid ?: return
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val url = userRepo.uploadProfilePicture(userId, uri)
                userRepo.updateUserProfile(userId, mapOf("profilePictureUrl" to url))
                loadUserData(userId)
                showSuccess("Profile picture updated.")
            } catch (e: Exception) {
                showError(e.message ?: "Failed to upload profile picture.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun showError(message: String) {
        _banners.tryEmit(UiBanner(text = message, type = UiBannerType.Error))
    }

    fun showSuccess(message: String) {
        _banners.tryEmit(UiBanner(text = message, type = UiBannerType.Success))
    }

    fun showInfo(message: String) {
        _banners.tryEmit(UiBanner(text = message, type = UiBannerType.Info))
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
                return UserViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    //  submit expert application
    fun submitExpertApplication(
        fullName: String,
        expertise: String,
        yearsOfExperience: String,
        portfolioLink: String,
        notes: String
    ) {
        val userId = currentUser.value?.uid
        if (userId == null) {
            showError("You're not logged in. Please sign in again.")
            return
        }

        // input validation
        if (fullName.isBlank() || expertise.isBlank() || yearsOfExperience.isBlank() || portfolioLink.isBlank()) {
            showError("Please fill in all required fields.")
            return
        }
        if (yearsOfExperience.toIntOrNull() == null) {
            showError("Years of experience must be a number.")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                userRepo.submitExpertApplication(
                    userId = userId,
                    fullName = fullName,
                    expertise = expertise,
                    yearsOfExperience = yearsOfExperience,
                    portfolioLink = portfolioLink,
                    notes = notes
                )

                // refresh local cached profile
                val updated = userRepo.getUserProfile(userId)
                _userData.value = updated
                _expertUiEvents.tryEmit(ExpertUiEvent.Submitted)

                showSuccess("Application submitted!")
            } catch (e: Exception) {
                showError(e.message ?: "Failed to submit application.")
            } finally {
                _isLoading.value = false
            }
        }
    }
}

