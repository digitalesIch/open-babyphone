package org.openbabyphone

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionState

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        MonitorServiceRepository.reset()
        composeTestRule.activity.getSharedPreferences(
            OpenBabyphoneApplication.SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        composeTestRule.activity.getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        composeTestRule.activity.getSharedPreferences(
            TrustedChildStore.METADATA_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        composeTestRule.activity.getSharedPreferences(
            ProtectedTrustedCredentialStore.PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun backAction_isVisibleAndInvokesNavigation() {
        var navigatedBack = false
        setSettingsContent(onNavigateBack = { navigatedBack = true })

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()

        composeTestRule.runOnIdle { assertTrue(navigatedBack) }
    }

    @Test
    fun themeAndSensitivity_applyAndPersist() {
        val theme = mutableStateOf(ThemeMode.SYSTEM)
        setSettingsContent(
            themeMode = theme.value,
            onThemeModeChanged = {
                ThemePreferences.write(composeTestRule.activity, it)
                theme.value = it
            }
        )

        composeTestRule.onNodeWithTag("theme_dark").performClick()
        composeTestRule.onNodeWithTag("sensitivity_very_high").performScrollTo().performClick()

        assertEquals(ThemeMode.DARK, ThemePreferences.read(composeTestRule.activity))
        assertEquals(
            MicrophoneSensitivity.VERY_HIGH,
            MicrophoneSensitivityPreferences.read(composeTestRule.activity)
        )
    }

    @Test
    fun childName_isNormalizedPersistedAndMarkedForNextActiveSession() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.WaitingForParent)
        setSettingsContent()

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.child_name_next_monitoring_session)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("rename_child_phone").performClick()
        composeTestRule.onNodeWithTag("child_name_input").performTextReplacement("  Nursery  ")
        composeTestRule.onNodeWithTag("save_child_name").performClick()

        composeTestRule.onNodeWithText("Nursery").assertIsDisplayed()
        assertEquals(
            "Nursery",
            composeTestRule.activity.getSharedPreferences(
                MonitorService.PAIRING_PREFS_NAME,
                Context.MODE_PRIVATE
            ).getString(MonitorService.PREF_KEY_DEVICE_NAME, null)
        )
    }

    @Test
    fun activeMonitoring_blocksPairingResetWithInstruction() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        setSettingsContent()

        composeTestRule.onNodeWithTag("reset_pairing").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.reset_pairing_stop_monitoring_first)
        ).assertIsDisplayed()
    }

    @Test
    fun resetPairing_requiresConfirmationAndNeverDisplaysCode() {
        val oldPairing = PairingSettings.load(composeTestRule.activity) { "ABCDEFGH" }
        setSettingsContent()

        composeTestRule.onNodeWithText(oldPairing.pairingCode).assertDoesNotExist()
        composeTestRule.onNodeWithTag("reset_pairing").performScrollTo().performClick()
        assertEquals(oldPairing, PairingSettings.load(composeTestRule.activity))
        composeTestRule.onNodeWithTag("confirm_reset_pairing").performClick()

        assertNotEquals(oldPairing, PairingSettings.load(composeTestRule.activity))
        composeTestRule.onNodeWithText(oldPairing.pairingCode).assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_feedback_security")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun knownChildRowHidesTechnicalDataAndForgetRequiresConfirmation() {
        val store = TrustedChildStore(composeTestRule.activity, TestCredentialCrypto())
        val credential = "code1234".toCharArray()
        try {
            store.trustAuthenticated(
                childId = "technical-child-id",
                pairingId = "technical-pairing-id",
                displayName = "Nursery",
                pairingCode = credential,
                address = "192.0.2.10",
                port = 12345
            )
        } finally {
            credential.fill('\u0000')
        }
        setSettingsContent(trustedChildStore = store)

        composeTestRule.onNodeWithText("Nursery").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("technical-child-id").assertDoesNotExist()
        composeTestRule.onNodeWithText("technical-pairing-id").assertDoesNotExist()
        composeTestRule.onNodeWithText("192.0.2.10").assertDoesNotExist()
        composeTestRule.onNodeWithText("12345").assertDoesNotExist()
        composeTestRule.onNodeWithTag("forget_child_0").performClick()
        assertEquals(1, store.getAll().size)
        composeTestRule.onNodeWithTag("confirm_forget_child").performClick()

        assertTrue(store.getAll().isEmpty())
        composeTestRule.onNodeWithTag("no_known_children").assertIsDisplayed()
    }

    @Test
    fun legalLinksUseExplicitExternalDestinations() {
        val opened = mutableListOf<String>()
        setSettingsContent(externalLinkOpener = { _, url -> opened += url; true })

        composeTestRule.onNodeWithTag("privacy_policy_link").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("source_code_link").performClick()
        composeTestRule.onNodeWithTag("license_link").performClick()
        composeTestRule.onNodeWithTag("notices_link").performClick()
        composeTestRule.onNodeWithTag("support_link").performClick()

        assertEquals(5, opened.size)
        assertTrue(opened.all { it.startsWith("https://github.com/digitalesIch/open-babyphone") })
        assertTrue(opened.any { it.endsWith("privacy-policy.md") })
        assertTrue(opened.any { it.endsWith("/LICENSE") })
        assertTrue(opened.any { it.endsWith("/NOTICE") })
        assertTrue(opened.any { it.endsWith("/issues") })
    }

    @Test
    fun appVersionRemainsReachableAtTwoHundredPercentFontScale() {
        val density = composeTestRule.activity.resources.displayMetrics.density
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                SettingsScreen(
                    onNavigateBack = {},
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChanged = {},
                    trustedChildStore = TrustedChildStore(
                        composeTestRule.activity,
                        TestCredentialCrypto()
                    )
                )
            }
        }

        composeTestRule.onNodeWithTag("reset_pairing").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_version").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun actionFeedbackIsInlinePoliteAndDistinguishesSuccessFromFailureText() {
        setSettingsContent(externalLinkOpener = { _, _ -> false })
        composeTestRule.onNodeWithTag("rename_child_phone").performClick()
        composeTestRule.onNodeWithTag("child_name_input").performTextReplacement("Nursery")
        composeTestRule.onNodeWithTag("save_child_name").performClick()

        composeTestRule.onNodeWithTag("settings_feedback_child_name")
            .assertIsDisplayed()
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
                    androidx.compose.ui.semantics.LiveRegionMode.Polite
                )
            )
        composeTestRule.onNodeWithTag("privacy_policy_link").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("settings_feedback_about").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.could_not_open_link)
        ).assertIsDisplayed()
    }

    private fun setSettingsContent(
        onNavigateBack: () -> Unit = {},
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        onThemeModeChanged: (ThemeMode) -> Unit = {},
        trustedChildStore: TrustedChildStore = TrustedChildStore(
            composeTestRule.activity,
            TestCredentialCrypto()
        ),
        externalLinkOpener: (Context, String) -> Boolean = { _, _ -> true }
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateBack = onNavigateBack,
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
                trustedChildStore = trustedChildStore,
                externalLinkOpener = externalLinkOpener
            )
        }
    }

    private class TestCredentialCrypto : TrustedCredentialCrypto {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential =
            ProtectedCredential(ByteArray(12), plaintext.copyOf())

        override fun decrypt(
            protectedCredential: ProtectedCredential,
            aad: ByteArray
        ): ByteArray = protectedCredential.ciphertext.copyOf()
    }
}
