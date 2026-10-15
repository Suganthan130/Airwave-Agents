package lk.sugaapps.airwave.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lk.sugaapps.airwave.R
import lk.sugaapps.airwave.ui.theme.AirWaveTheme
import lk.sugaapps.airwave.ui.theme.BorderLight
import lk.sugaapps.airwave.ui.theme.BorderStrong
import lk.sugaapps.airwave.ui.theme.BrandBlue
import lk.sugaapps.airwave.ui.theme.DangerRed
import lk.sugaapps.airwave.ui.theme.SurfaceWhite
import lk.sugaapps.airwave.ui.theme.TextMuted
import lk.sugaapps.airwave.ui.theme.TextPrimary
import lk.sugaapps.airwave.ui.theme.TextSecondary

/**
 * Sign-up screen for the Airwave Agents transmitter app.
 *
 * UI only: the caller supplies action callbacks so it can be wired to
 * Firebase Auth / Google sign-in later without changing the UI.
 */
@Composable
fun SignUpScreen(
    modifier: Modifier = Modifier,
    onCreateAccount: (name: String, email: String, password: String) -> Unit = { _, _, _ -> },
    onGoogleSignIn: () -> Unit = {},
    onLogin: () -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmVisible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo mark
            Image(
                painter = painterResource(R.drawable.logo_mark),
                contentDescription = stringResource(R.string.brand_name),
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.signup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(Modifier.height(28.dp))

            // Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(16.dp), clip = false, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceWhite)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.signup_heading),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )

                Spacer(Modifier.height(20.dp))

                FieldLabel(stringResource(R.string.signup_name_label))
                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.signup_name_hint),
                    keyboardType = KeyboardType.Text,
                )

                Spacer(Modifier.height(16.dp))

                FieldLabel(stringResource(R.string.signup_email_label))
                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = stringResource(R.string.signup_email_hint),
                    keyboardType = KeyboardType.Email,
                )

                Spacer(Modifier.height(16.dp))

                FieldLabel(stringResource(R.string.signup_password_label))
                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailing = {
                        PasswordToggle(passwordVisible) { passwordVisible = !passwordVisible }
                    },
                )

                Spacer(Modifier.height(16.dp))

                FieldLabel(stringResource(R.string.signup_confirm_label))
                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    placeholder = "",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (confirmVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailing = {
                        PasswordToggle(confirmVisible) { confirmVisible = !confirmVisible }
                    },
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val n = name.trim()
                        val e = email.trim()
                        error = when {
                            n.isEmpty() -> "Enter your full name."
                            e.isEmpty() -> "Enter your work email."
                            !e.contains("@") || !e.contains(".") -> "Enter a valid email address."
                            password.length < 6 -> "Password must be at least 6 characters."
                            password != confirm -> "Passwords don't match."
                            else -> null
                        }
                        if (error == null) onCreateAccount(n, e, password)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandBlue,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.signup_submit),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(20.dp))
                DividerWithText(stringResource(R.string.login_or))
                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onGoogleSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceWhite,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_google_g),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.signup_google),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.signup_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.signup_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                TextButton(
                    onClick = onLogin,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.signup_login),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    TextButton(
        onClick = onToggle,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(if (visible) R.string.login_hide else R.string.login_show),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = TextSecondary,
    )
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = TextMuted) }
        } else {
            null
        },
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandBlue,
            unfocusedBorderColor = BorderStrong,
            focusedContainerColor = SurfaceWhite,
            unfocusedContainerColor = SurfaceWhite,
            cursorColor = BrandBlue,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

@Composable
private fun DividerWithText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignUpScreenPreview() {
    AirWaveTheme {
        SignUpScreen()
    }
}
