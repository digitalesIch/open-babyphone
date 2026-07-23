package org.openbabyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openbabyphone.ui.theme.Spacing

@Composable
fun StartScreen(
    onNavigateToMonitor: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(Spacing.space16)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(size = 64.dp)

            Spacer(modifier = Modifier.height(Spacing.space16))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W900),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.space8))

            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.space16)
            )

            Spacer(modifier = Modifier.height(Spacing.space32))

            RoleCard(
                title = stringResource(R.string.child_device),
                description = stringResource(R.string.child_description),
                onClick = onNavigateToMonitor,
                modifier = Modifier
                    .testTag("child_role_card")
                    .semantics {
                        role = Role.Button
                        contentDescription = "Role: Child Device"
                        traversalIndex = 0f
                    }
            )

            Spacer(modifier = Modifier.height(Spacing.space16))

            RoleCard(
                title = stringResource(R.string.parent_device),
                description = stringResource(R.string.parent_description),
                onClick = onNavigateToDiscover,
                modifier = Modifier
                    .testTag("parent_role_card")
                    .semantics {
                        role = Role.Button
                        contentDescription = "Role: Parent Device"
                        traversalIndex = 1f
                    }
            )

            Spacer(modifier = Modifier.height(Spacing.space32))

            OdTextButton(
                text = stringResource(R.string.settings),
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .testTag("settings_button")
                    .semantics { traversalIndex = 2f }
            )
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 112.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8)
        ) {
            OdCardTitle(text = title)
            OdCardBody(text = description)
        }
    }
}
