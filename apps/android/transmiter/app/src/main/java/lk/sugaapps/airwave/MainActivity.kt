package lk.sugaapps.airwave

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import lk.sugaapps.airwave.data.AuthErrors
import lk.sugaapps.airwave.data.AuthRepository
import lk.sugaapps.airwave.data.UserStore
import lk.sugaapps.airwave.engine.EngineRegistry
import lk.sugaapps.airwave.engine.Playlist
import lk.sugaapps.airwave.ui.ExperienceRegistry
import lk.sugaapps.airwave.ui.screens.LoginScreen
import lk.sugaapps.airwave.ui.screens.SignUpScreen
import lk.sugaapps.airwave.ui.theme.AirWaveTheme
import lk.sugaapps.airwave.ui.theme.BrandBlue

private enum class AppScreen { LOGIN, SIGN_UP, HOME }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            Class.forName("lk.sugaapps.airwave.PrivateExperience")
                .getMethod("install")
                .invoke(null)
        }
        // Initialize whichever engine is registered (real native engine locally,
        // no-op in the public build).
        runCatching { EngineRegistry.engine.initialize(applicationContext) }
        // Restore persistent playlist and queue state across app launches
        runCatching { Playlist.initialize(applicationContext) }
        // Restore all settings from SharedPreferences so engine and UI never reset
        runCatching {
            val savedSettings = lk.sugaapps.airwave.data.SettingsStore.load(applicationContext)
            lk.sugaapps.airwave.data.SettingsStore.applyToEngine(savedSettings)
        }
        // Restore all mixer & equalizer settings so mic volume, monitoring, and EQ never reset
        runCatching {
            lk.sugaapps.airwave.data.MixerStore.applyAllToEngine(applicationContext)
        }
        // Light UI → dark status/nav-bar icons for contrast.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = SystemBarStyle.light(transparent, transparent),
        )
        setContent {
            AirWaveTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var screen by remember {
                    mutableStateOf(if (AuthRepository.currentUser != null) AppScreen.HOME else AppScreen.LOGIN)
                }
                var busy by remember { mutableStateOf(false) }

                // React to sign-out (e.g. from Settings) by returning to login.
                DisposableEffect(Unit) {
                    val listener = AuthRepository.addAuthStateListener { user ->
                        if (user == null && screen == AppScreen.HOME) {
                            lk.sugaapps.airwave.data.SettingsStore.reset(applicationContext)
                            screen = AppScreen.LOGIN
                        }
                    }
                    onDispose { AuthRepository.removeAuthStateListener(listener) }
                }

                fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

                val timedOut = "That took too long. Check your connection and try again."

                fun loginEmail(email: String, password: String) {
                    busy = true
                    scope.launch {
                        try {
                            val user = withTimeoutOrNull(30_000) { AuthRepository.loginEmail(email, password) }
                            if (user == null) {
                                toast(timedOut)
                            } else {
                                lk.sugaapps.airwave.data.SettingsStore.reset(applicationContext)
                                screen = AppScreen.HOME
                            }
                        } catch (t: Throwable) {
                            toast(AuthErrors.message(t))
                        } finally {
                            busy = false
                        }
                    }
                }

                // Profile write must never block sign-in: a Firestore write only
                // completes on server ack, so we fire it in the background with its
                // own timeout. Offline writes still sync later via Firestore's cache.
                fun saveProfileInBackground(uid: String, name: String, email: String) {
                    scope.launch {
                        runCatching { withTimeoutOrNull(15_000) { UserStore.saveProfile(uid, name, email) } }
                    }
                }

                fun signUpEmail(name: String, email: String, password: String) {
                    busy = true
                    scope.launch {
                        try {
                            val user = withTimeoutOrNull(30_000) { AuthRepository.signUpEmail(email, password) }
                            if (user == null) {
                                toast(timedOut)
                            } else {
                                screen = AppScreen.HOME
                                saveProfileInBackground(user.uid, name, email)
                            }
                        } catch (t: Throwable) {
                            toast(AuthErrors.message(t))
                        } finally {
                            busy = false
                        }
                    }
                }

                // Sends a Firebase password-reset email. Validation (empty/invalid
                // address) happens in LoginScreen before this is called.
                fun forgotPassword(email: String) {
                    val target = email.trim()
                    busy = true
                    FirebaseAuth.getInstance().sendPasswordResetEmail(target)
                        .addOnSuccessListener {
                            busy = false
                            toast("Reset link sent to $target")
                        }
                        .addOnFailureListener { t ->
                            busy = false
                            toast(AuthErrors.message(t))
                        }
                }

                fun googleSignIn() {
                    busy = true
                    scope.launch {
                        try {
                            val user = withTimeoutOrNull(60_000) { AuthRepository.signInWithGoogle(context) }
                            if (user == null) {
                                toast(timedOut)
                            } else {
                                screen = AppScreen.HOME
                                saveProfileInBackground(user.uid, user.displayName ?: "", user.email ?: "")
                            }
                        } catch (t: Throwable) {
                            if (!AuthErrors.isCancellation(t)) toast(AuthErrors.message(t))
                        } finally {
                            busy = false
                        }
                    }
                }

                // Back from Sign-Up returns to Login instead of leaving the app
                // (rememberSaveable keeps the typed fields).
                BackHandler(enabled = screen == AppScreen.SIGN_UP) { screen = AppScreen.LOGIN }

                Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        AppScreen.LOGIN -> AuthHost { padding ->
                            LoginScreen(
                                modifier = padding,
                                onLogin = ::loginEmail,
                                onGoogleSignIn = ::googleSignIn,
                                onForgotPassword = ::forgotPassword,
                                onSignUp = { screen = AppScreen.SIGN_UP },
                            )
                        }

                        AppScreen.SIGN_UP -> AuthHost { padding ->
                            SignUpScreen(
                                modifier = padding,
                                onCreateAccount = ::signUpEmail,
                                onGoogleSignIn = ::googleSignIn,
                                onLogin = { screen = AppScreen.LOGIN },
                            )
                        }

                        AppScreen.HOME -> ExperienceRegistry.home()
                    }

                    if (busy) LoadingOverlay()
                }
            }
        }
    }
}

/** Full-screen loading animation that also blocks interaction while work is in flight. */
@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* consume touches */ },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = BrandBlue)
    }
}

/** Wraps the auth screens in a Scaffold so they respect system-bar insets. */
@Composable
private fun AuthHost(content: @Composable (Modifier) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
