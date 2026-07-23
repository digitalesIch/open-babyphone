/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.isAuthoritativelyActive
import org.openbabyphone.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    trustedChildStore: TrustedChildStore? = null,
    initialKnownChildren: List<TrustedChild>? = null,
    initialChildName: String? = null,
    initialSensitivity: MicrophoneSensitivity? = null,
    externalLinkOpener: (Context, String) -> Boolean = ::openExternalLink
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext, trustedChildStore, initialKnownChildren) {
        trustedChildStore ?: if (initialKnownChildren == null) context.trustedChildStore() else null
    }
    val monitorState by MonitorServiceRepository.sessionState.collectAsState()
    val monitoringActive = monitorState.isAuthoritativelyActive()
    val defaultChildName = stringResource(R.string.default_child_name)
    val couldNotOpenLinkMessage = stringResource(R.string.could_not_open_link)
    val childNameSavedMessage = stringResource(R.string.child_name_saved)
    val childNameSavedNextSessionMessage = stringResource(R.string.child_name_saved_next_session)
    val childForgottenMessage = stringResource(R.string.child_forgotten)
    val couldNotForgetChildMessage = stringResource(R.string.could_not_forget_child)
    val pairingResetCompleteMessage = stringResource(R.string.pairing_reset_complete)
    val pairingResetFailedMessage = stringResource(R.string.pairing_reset_failed)
    val childNameSaveFailedMessage = stringResource(R.string.child_name_save_failed)
    val linkOpenedMessage = stringResource(R.string.link_opened)
    var childName by remember(initialChildName) {
        mutableStateOf(initialChildName ?: ChildDeviceNamePreferences.read(context, defaultChildName))
    }
    var sensitivity by remember(initialSensitivity) {
        mutableStateOf(initialSensitivity ?: MicrophoneSensitivityPreferences.read(context))
    }
    var knownChildren by remember(initialKnownChildren, store) {
        mutableStateOf(
            (initialKnownChildren ?: store?.getAll().orEmpty()).sortedBy { it.displayName.lowercase() }
        )
    }
    var childBeingForgotten by remember { mutableStateOf<TrustedChild?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var feedbackSequence by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<SettingsFeedback?>(null) }
    val showFeedback: (SettingsFeedbackTarget, String, Boolean) -> Unit = { target, message, error ->
        feedbackSequence += 1
        feedback = SettingsFeedback(feedbackSequence, target, message, error)
    }
    val openLink: (String) -> Unit = { url ->
        val opened = externalLinkOpener(context, url)
        showFeedback(
            SettingsFeedbackTarget.About,
            if (opened) linkOpenedMessage else couldNotOpenLinkMessage,
            !opened
        )
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.settings),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.space16)
                    .testTag("settings_content"),
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)
            ) {
                SettingsHeader()

                SettingsSection(
                    title = stringResource(R.string.appearance),
                    helper = stringResource(R.string.theme_mode_description)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        SettingsChoiceRow(
                            title = when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            },
                            selected = themeMode == mode,
                            onClick = { onThemeModeChanged(mode) },
                            modifier = Modifier.testTag("theme_${mode.preferenceValue}")
                        )
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.child_device),
                    helper = stringResource(R.string.child_settings_description)
                ) {
                    SettingTitle(stringResource(R.string.device_name_title))
                    Text(
                        text = childName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("child_name_value")
                    )
                    Text(
                        text = if (monitoringActive) {
                            stringResource(R.string.child_name_next_monitoring_session)
                        } else {
                            stringResource(R.string.device_name_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OdOutlinedActionButton(
                        text = stringResource(R.string.rename_child_phone),
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.testTag("rename_child_phone")
                    )
                    feedback?.takeIf { it.target == SettingsFeedbackTarget.ChildName }?.let {
                        SettingsFeedbackMessage(it)
                    }

                    SettingsDivider()
                    SettingTitle(stringResource(R.string.microphone_sensitivity_title))
                    Text(
                        text = stringResource(R.string.microphone_sensitivity_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MicrophoneSensitivity.entries.forEach { option ->
                        SettingsChoiceRow(
                            title = when (option) {
                                MicrophoneSensitivity.NORMAL -> stringResource(R.string.microphone_sensitivity_normal)
                                MicrophoneSensitivity.HIGH -> stringResource(R.string.microphone_sensitivity_high)
                                MicrophoneSensitivity.VERY_HIGH -> stringResource(R.string.microphone_sensitivity_very_high)
                            },
                            selected = sensitivity == option,
                            onClick = {
                                if (MicrophoneSensitivityPreferences.write(context, option)) {
                                    sensitivity = option
                                }
                            },
                            modifier = Modifier.testTag("sensitivity_${option.preferenceValue}")
                        )
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.known_children_title),
                    helper = stringResource(R.string.known_children_management_help)
                ) {
                    if (knownChildren.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_known_children),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("no_known_children")
                        )
                    } else {
                        knownChildren.forEachIndexed { index, child ->
                            if (index > 0) SettingsDivider()
                            Text(
                                text = child.displayName.ifBlank {
                                    stringResource(R.string.unnamed_child_phone)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            OdOutlinedActionButton(
                                text = stringResource(R.string.forget_child),
                                onClick = { childBeingForgotten = child },
                                modifier = Modifier.testTag("forget_child_$index")
                            )
                        }
                    }
                    feedback?.takeIf { it.target == SettingsFeedbackTarget.KnownChildren }?.let {
                        SettingsFeedbackMessage(it)
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.security),
                    helper = stringResource(R.string.security_settings_description)
                ) {
                    Text(
                        text = if (monitoringActive) {
                            stringResource(R.string.reset_pairing_stop_monitoring_first)
                        } else {
                            stringResource(R.string.reset_pairing_summary)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("reset_pairing_guidance")
                    )
                    OdOutlinedActionButton(
                        text = stringResource(R.string.reset_pairing),
                        onClick = { showResetDialog = true },
                        enabled = !monitoringActive,
                        modifier = Modifier.testTag("reset_pairing")
                    )
                    feedback?.takeIf { it.target == SettingsFeedbackTarget.Security }?.let {
                        SettingsFeedbackMessage(it)
                    }
                }

                SettingsSection(
                    title = stringResource(R.string.about),
                    helper = stringResource(R.string.about_description)
                ) {
                    ExternalLinkButton(
                        label = stringResource(R.string.privacy_policy),
                        testTag = "privacy_policy_link",
                        onClick = { openLink(PRIVACY_POLICY_URL) }
                    )
                    ExternalLinkButton(
                        label = stringResource(R.string.source_code),
                        testTag = "source_code_link",
                        onClick = { openLink(SOURCE_CODE_URL) }
                    )
                    ExternalLinkButton(
                        label = stringResource(R.string.gpl_license),
                        testTag = "license_link",
                        onClick = { openLink(LICENSE_URL) }
                    )
                    ExternalLinkButton(
                        label = stringResource(R.string.third_party_notices),
                        testTag = "notices_link",
                        onClick = { openLink(NOTICE_URL) }
                    )
                    ExternalLinkButton(
                        label = stringResource(R.string.support_and_issues),
                        testTag = "support_link",
                        onClick = { openLink(ISSUES_URL) }
                    )
                    SettingsDivider()
                    SettingTitle(stringResource(R.string.app_version))
                    Text(
                        text = stringResource(
                            R.string.app_version_summary,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("app_version")
                    )
                    feedback?.takeIf { it.target == SettingsFeedbackTarget.About }?.let {
                        SettingsFeedbackMessage(it)
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.space16))
            }
        }
    }

    if (showRenameDialog) {
        RenameChildDialog(
            currentName = childName,
            onDismiss = { showRenameDialog = false },
            onSave = { proposedName ->
                val savedName = ChildDeviceNamePreferences.write(context, proposedName)
                if (savedName != null) {
                    childName = savedName
                    showFeedback(
                        SettingsFeedbackTarget.ChildName,
                        if (monitoringActive) childNameSavedNextSessionMessage else childNameSavedMessage,
                        false
                    )
                } else {
                    showFeedback(SettingsFeedbackTarget.ChildName, childNameSaveFailedMessage, true)
                }
                showRenameDialog = false
            }
        )
    }

    childBeingForgotten?.let { child ->
        AlertDialog(
            onDismissRequest = { childBeingForgotten = null },
            title = { Text(stringResource(R.string.forget_child_title)) },
            text = { Text(stringResource(R.string.forget_child_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (store?.forget(child.childId) == true) {
                            knownChildren = store.getAll().sortedBy { it.displayName.lowercase() }
                            showFeedback(SettingsFeedbackTarget.KnownChildren, childForgottenMessage, false)
                        } else {
                            showFeedback(SettingsFeedbackTarget.KnownChildren, couldNotForgetChildMessage, true)
                        }
                        childBeingForgotten = null
                    },
                    modifier = Modifier.testTag("confirm_forget_child")
                ) {
                    Text(stringResource(R.string.forget_child))
                }
            },
            dismissButton = {
                TextButton(onClick = { childBeingForgotten = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_pairing)) },
            text = { Text(stringResource(R.string.reset_pairing_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!MonitorServiceRepository.sessionState.value.isAuthoritativelyActive()) {
                            try {
                                PairingSettings.reset(context)
                                showFeedback(SettingsFeedbackTarget.Security, pairingResetCompleteMessage, false)
                            } catch (_: RuntimeException) {
                                showFeedback(SettingsFeedbackTarget.Security, pairingResetFailedMessage, true)
                            }
                        }
                        showResetDialog = false
                    },
                    modifier = Modifier.testTag("confirm_reset_pairing")
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private enum class SettingsFeedbackTarget(val tag: String) {
    ChildName("child_name"),
    KnownChildren("known_children"),
    Security("security"),
    About("about")
}

private data class SettingsFeedback(
    val sequence: Int,
    val target: SettingsFeedbackTarget,
    val message: String,
    val isError: Boolean
)

@Composable
private fun SettingsFeedbackMessage(feedback: SettingsFeedback) {
    key(feedback.sequence) {
        Text(
            text = feedback.message,
            color = if (feedback.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .testTag("settings_feedback_${feedback.target.tag}")
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = feedback.message
                }
        )
    }
}

@Composable
private fun SettingsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(size = 44.dp)
        Column {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    helper: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        OdSectionHeader(title = title, helper = helper)
        OdOutlinedCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = Spacing.space8)
        )
    }
}

@Composable
private fun SettingTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = Spacing.space8),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    )
}

@Composable
private fun ExternalLinkButton(label: String, testTag: String, onClick: () -> Unit) {
    OdOutlinedActionButton(
        text = label,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun RenameChildDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by rememberSaveable(currentName) { mutableStateOf(currentName) }
    val normalized = DeviceName.normalize(value)
    val valid = DeviceName.isValid(normalized)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_child_phone)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.device_name_title)) },
                placeholder = { Text(stringResource(R.string.device_name_placeholder)) },
                supportingText = {
                    if (!valid) Text(stringResource(R.string.invalid_child_name))
                },
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_name_input")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(normalized) },
                enabled = valid,
                modifier = Modifier.testTag("save_child_name")
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun openExternalLink(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    )
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private const val SOURCE_CODE_URL = "https://github.com/digitalesIch/open-babyphone"
private const val PRIVACY_POLICY_URL = "$SOURCE_CODE_URL/blob/main/privacy-policy.md"
private const val LICENSE_URL = "$SOURCE_CODE_URL/blob/main/LICENSE"
private const val NOTICE_URL = "$SOURCE_CODE_URL/blob/main/NOTICE"
private const val ISSUES_URL = "$SOURCE_CODE_URL/issues"
