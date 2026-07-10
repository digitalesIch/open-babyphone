package org.openbabyphone

import org.openbabyphone.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverAddressScreen(
    onNavigateBack: () -> Unit,
    onConnect: (String, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var ipAddress by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(ConnectionConstants.DEFAULT_PORT.toString()) }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    val parsedPort = port.toIntOrNull()
    val isPortValid = parsedPort in 1..65535
    val canConnect = ipAddress.isNotBlank() && isPortValid
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.enter_address_title),
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
                OdSectionHeader(
                    title = stringResource(R.string.enter_address_title),
                    helper = stringResource(R.string.enter_address_instructions)
                )

                OdOutlinedCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text(stringResource(R.string.ip_address)) },
                            placeholder = { Text(stringResource(R.string.example_address)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ip_address_field"),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )

                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { char -> char.isDigit() } },
                            label = { Text(stringResource(R.string.port_title)) },
                            placeholder = { Text(stringResource(R.string.example_port)) },
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
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )

                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it },
                            label = { Text(stringResource(R.string.pairing_code_optional)) },
                            placeholder = { Text(stringResource(R.string.example_pairing_code)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                OdPrimaryButton(
                    text = stringResource(R.string.connect),
                    onClick = {
                        if (canConnect) {
                            val normalizedAddress = ConnectionAddress.normalize(ipAddress)
                            onConnect(normalizedAddress, parsedPort ?: ConnectionConstants.DEFAULT_PORT, pairingCode)
                        }
                    },
                    modifier = Modifier.testTag("connect_button"),
                    enabled = canConnect
                )
            }
        }
    }
}
