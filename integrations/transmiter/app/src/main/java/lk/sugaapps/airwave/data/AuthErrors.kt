package lk.sugaapps.airwave.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/** Maps auth/network exceptions to clear, user-facing messages. Never leaks stack traces. */
object AuthErrors {

    /** True for benign user-cancellation (don't treat as an error, just stop quietly). */
    fun isCancellation(t: Throwable): Boolean = t is GetCredentialCancellationException

    fun message(t: Throwable): String = when (t) {
        is AuthRepository.GoogleNotConfigured -> t.message
            ?: "Google sign-in isn't configured yet."
        is GetCredentialCancellationException -> "Sign-in cancelled."
        is NoCredentialException ->
            "No Google account on this device. Add one in Settings, then try again."
        is FirebaseAuthWeakPasswordException ->
            "Password is too weak — use at least 6 characters."
        is FirebaseAuthUserCollisionException ->
            "An account with that email already exists. Please log in."
        is FirebaseAuthInvalidUserException ->
            "No account found with that email. Please sign up first."
        is FirebaseAuthInvalidCredentialsException ->
            "Incorrect email or password."
        is FirebaseNetworkException ->
            "Network error. Check your connection and try again."
        is FirebaseAuthException -> fromCode(t.errorCode) ?: (t.message ?: "Authentication failed.")
        else -> t.message ?: "Something went wrong. Please try again."
    }

    private fun fromCode(code: String?): String? = when (code) {
        "ERROR_INVALID_EMAIL" -> "That email address is invalid."
        "ERROR_USER_DISABLED" -> "This account has been disabled."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait and try again."
        "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method isn't enabled for the project."
        "ERROR_USER_NOT_FOUND" -> "No account found with that email. Please sign up first."
        "ERROR_WRONG_PASSWORD" -> "Incorrect email or password."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with that email already exists. Please log in."
        else -> null
    }
}
