package com.example.rockland.navigation


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rockland.ui.components.TopBannerHost
import com.example.rockland.presentation.model.UiBanner
import com.example.rockland.ui.screens.LoginScreen
import com.example.rockland.ui.screens.MainScreen
import com.example.rockland.ui.screens.RegisterScreen
import com.example.rockland.ui.screens.SettingsScreen
import com.example.rockland.ui.screens.WelcomeScreen
import com.example.rockland.presentation.viewmodel.UserViewModel
import com.example.rockland.ui.screens.BoxesScreen


// Application routes
object AppRoutes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val SETTINGS = "settings"

    const val ROUTE_BOXES = "boxes"

}

// Application navigation component
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val userViewModel: UserViewModel = viewModel(factory = UserViewModel.Factory())
    val isLoggedIn by userViewModel.isLoggedIn.collectAsState()
    val isLoading by userViewModel.isLoading.collectAsState()
    val bannerState = remember { mutableStateOf<UiBanner?>(null) }
    val userData by userViewModel.userData.collectAsState()
    val userId = userData?.userId


    LaunchedEffect(Unit) {
        userViewModel.banners.collect { bannerState.value = it }
    }

    // Auth gate: MAIN is only reachable after login.
    LaunchedEffect(isLoggedIn) {
        val route = navController.currentDestination?.route
        if (isLoggedIn) {
            if (route == AppRoutes.WELCOME || route == AppRoutes.LOGIN || route == AppRoutes.REGISTER) {
                navController.navigate(AppRoutes.MAIN) {
                    popUpTo(AppRoutes.WELCOME) { inclusive = true }
                }
            }
        } else {
            if (route == AppRoutes.MAIN || route == AppRoutes.SETTINGS) {
                navController.navigate(AppRoutes.WELCOME) {
                    popUpTo(AppRoutes.MAIN) { inclusive = true }
                }
            }
        }
    }

    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.WELCOME
    ) {
        // Welcome screen
        composable(AppRoutes.WELCOME) {
            WelcomeScreen(
                onSignInClick = {
                    navController.navigate(AppRoutes.LOGIN)
                },
                onSignUpClick = {
                    navController.navigate(AppRoutes.REGISTER)
                }
            )
        }

        // Login screen
        composable(AppRoutes.LOGIN) {

            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.WELCOME) { inclusive = true }
                    }
                }
            }

            LoginScreen(
                isLoading = isLoading,
                    errorMessage = null, // handled via top banner
                onBackClick = {
                    navController.popBackStack()
                },
                onLoginClick = { email, password ->
                    userViewModel.loginUser(email, password)
                },
                onRegisterClick = {
                    navController.navigate(AppRoutes.REGISTER) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                },
                    onClearError = { /* no-op */ },
                    onShowMessage = { msg -> userViewModel.showError(msg) }
            )
        }

        // Register screen
        composable(AppRoutes.REGISTER) {

            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.WELCOME) { inclusive = true }
                    }
                }
            }

            RegisterScreen(
                isLoading = isLoading,
                    errorMessage = null, // handled via top banner
                onBackClick = {
                    navController.popBackStack()
                },
                onRegisterClick = { email, password, firstName, lastName ->
                    userViewModel.registerUser(email, password, firstName, lastName)
                },
                onLoginClick = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.REGISTER) { inclusive = true }
                    }
                },
                    onClearError = { /* no-op */ },
                    onShowMessage = { msg -> userViewModel.showError(msg) }
            )
        }

        // Main screen
        composable(AppRoutes.MAIN) {
            MainScreen(
                onSettingsClick = {
                    navController.navigate(AppRoutes.SETTINGS)
                },
                onLogoutClick = {
                    userViewModel.logout()
                },
                userViewModel = userViewModel
            )
        }
        composable(AppRoutes.ROUTE_BOXES) {
            BoxesScreen(userId = userId)
        }



        // Settings screen
        composable(AppRoutes.SETTINGS) {
            val userData by userViewModel.userData.collectAsState()

            val isUploadingPicture by userViewModel.isLoading.collectAsState()
            SettingsScreen(
                userData = userData,
                isUploadingPicture = isUploadingPicture,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        navController.navigate(AppRoutes.MAIN) {
                            popUpTo(AppRoutes.MAIN) { inclusive = true }
                        }
                    }
                },
                onSaveClick = { firstName, lastName, _ ->
                    userViewModel.updateUserProfile(firstName, lastName)
                    if (!navController.popBackStack()) {
                        navController.navigate(AppRoutes.MAIN) {
                            popUpTo(AppRoutes.MAIN) { inclusive = true }
                        }
                    }
                },
                onUploadProfilePicture = { uri -> userViewModel.uploadProfilePicture(uri) },
                onLogoutClick = {
                    userViewModel.logout()
                },
                onValidationError = { msg -> userViewModel.showError(msg) },
                )
        }
        }

        TopBannerHost(
            banner = bannerState.value,
            onDismiss = { bannerState.value = null },
            modifier = androidx.compose.ui.Modifier.align(Alignment.TopCenter)
        )
    }
}


