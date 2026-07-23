package org.openbabyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openbabyphone.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverAddressScreen(
    onNavigateBack: () -> Unit,
    onConnect: (requestId: String) -> Unit,
    requestId: String = "",
    onPairAgain: () -> Unit = {},
    onUseRegularWifi: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    var address by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(ConnectionConstants.DEFAULT_PORT.toString()) }
    var pairingCode by remember { mutableStateOf("") }
    var portExpanded by rememberSaveable { mutableStateOf(false) }
    val credentialState = pendingCredentialState(requestId)
    val usesPendingCredential = credentialState == PendingCredentialState.Available
    val parsedPort = port.toIntOrNull()
    val isPortValid = parsedPort in 1..65535
    val isAddressValid = ConnectionAddress.isValidAddress(address)
    val isPairingCodeValid = PairingCode.isValid(pairingCode)
    val canConnect = isAddressValid && isPortValid && (usesPendingCredential || isPairingCodeValid)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.manual_address_title),
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(Spacing.space16)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)
            ) {
                if (credentialState == PendingCredentialState.Expired) {
                    OdOutlinedCard(modifier = Modifier.testTag("expired_pairing_recovery")) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                            Text(
                                text = stringResource(R.string.pending_pairing_expired),
                                color = MaterialTheme.colorScheme.error
                            )
                            OdPrimaryButton(
                                text = stringResource(R.string.pair_again),
                                onClick = onPairAgain,
                                modifier = Modifier.testTag("pair_again_button")
                            )
                            OdOutlinedActionButton(
                                text = stringResource(R.string.use_regular_wifi),
                                onClick = onUseRegularWifi,
                                modifier = Modifier.testTag("manual_regular_wifi")
                            )
                        }
                    }
                    return@Column
                }
                OdSectionHeader(
                    title = stringResource(R.string.manual_address_expert_title),
                    helper = stringResource(R.string.manual_address_expert_description)
                )

                OdOutlinedCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                        Text(
                            text = stringResource(R.string.manual_address_source),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(stringResource(R.string.address_or_hostname)) },
                            placeholder = { Text(stringResource(R.string.example_address)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ip_address_field"),
                            isError = address.isNotBlank() && !isAddressValid,
                            supportingText = {
                                if (address.isNotBlank() && !isAddressValid) {
                                    Text(stringResource(R.string.invalid_address))
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = if (usesPendingCredential) ImeAction.Done else ImeAction.Next
                            ),
                            singleLine = true
                        )

                        val disclosureState = stringResource(
                            if (portExpanded) R.string.expanded else R.string.collapsed
                        )
                        TextButton(
                            onClick = { portExpanded = !portExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("port_override_disclosure")
                                .semantics { stateDescription = disclosureState }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.port_override, ConnectionConstants.DEFAULT_PORT))
                                Text(if (portExpanded) "-" else "+")
                            }
                        }
                        if (portExpanded) {
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it.filter(Char::isDigit) },
                                label = { Text(stringResource(R.string.port_title)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("port_field"),
                                isError = port.isNotBlank() && !isPortValid,
                                supportingText = {
                                    if (port.isNotBlank() && !isPortValid) {
                                        Text(stringResource(R.string.invalid_port))
                                    }
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true
                            )
                        }

                        if (usesPendingCredential) {
                            Text(
                                text = stringResource(R.string.scanned_pairing_reused),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("pending_credential_notice")
                            )
                        } else {
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { pairingCode = it.take(PairingCode.MAX_LENGTH) },
                                label = { Text(stringResource(R.string.pairing_code_title)) },
                                placeholder = { Text(stringResource(R.string.example_pairing_code)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_pairing_code_field"),
                                isError = pairingCode.isNotEmpty() && !isPairingCodeValid,
                                supportingText = {
                                    if (pairingCode.isNotEmpty() && !isPairingCodeValid) {
                                        Text(stringResource(R.string.invalid_pairing_code_feedback))
                                    }
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true
                            )
                        }
                    }
                }

                OdPrimaryButton(
                    text = stringResource(R.string.connect),
                    onClick = {
                        if (!canConnect) return@OdPrimaryButton
                        val normalizedAddress = ConnectionAddress.normalize(address)
                        val endpointPort = parsedPort ?: ConnectionConstants.DEFAULT_PORT
                        val completedRequest = if (usesPendingCredential) {
                            PendingConnections.store.completeEndpoint(
                                requestId,
                                normalizedAddress,
                                endpointPort,
                                ""
                            )
                        } else {
                            PendingConnections.store.put(
                                PendingConnection(
                                    address = normalizedAddress,
                                    port = endpointPort,
                                    name = "",
                                    pairingCode = PairingCode.normalize(pairingCode).toCharArray()
                                )
                            )
                        }
                        if (completedRequest == null) {
                            return@OdPrimaryButton
                        } else {
                            pairingCode = ""
                            onConnect(completedRequest)
                        }
                    },
                    modifier = Modifier.testTag("connect_button"),
                    enabled = canConnect
                )
            }
        }
    }
}
