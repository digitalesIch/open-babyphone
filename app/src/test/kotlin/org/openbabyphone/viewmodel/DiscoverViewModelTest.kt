package org.openbabyphone.viewmodel

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.AesGcmTrustedCredentialCrypto
import org.openbabyphone.CredentialStorageResult
import org.openbabyphone.PairingQrCode
import org.openbabyphone.PendingConnectionStore
import org.openbabyphone.ProtectedTrustedCredentialStore
import org.openbabyphone.TrustedChildStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import javax.crypto.KeyGenerator

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Application
    private lateinit var trustedStore: TrustedChildStore
    private lateinit var pendingStore: PendingConnectionStore
    private lateinit var viewModel: DiscoverViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        trustedStore = TrustedChildStore(context, AesGcmTrustedCredentialCrypto({ key }))
        pendingStore = PendingConnectionStore()
        viewModel = DiscoverViewModel(
            application = context,
            trustedChildStore = trustedStore,
            pendingConnections = pendingStore,
            elapsedRealtime = { dispatcher.scheduler.currentTime }
        )
    }

    @After
    fun tearDown() {
        viewModel.cancelPairingSearch()
        pendingStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `valid structured qr creates pending material but not trust`() {
        val result = viewModel.handleQrScan(payload())
        val looking = viewModel.uiState.value.pairingFlow as PairingFlowState.LookingForChild
        val pending = pendingStore.lease(looking.requestId)

        assertTrue(result is QrScanResult.Structured)
        assertEquals("code1234", pending?.pairingCode?.concatToString())
        assertEquals("child-1", pending?.expectedChildId)
        assertEquals("pair-1", pending?.expectedPairingId)
        assertTrue(pending?.rememberAfterAuthentication == true)
        assertTrue(trustedStore.getAll().isEmpty())
    }

    @Test
    fun `invalid qr produces typed invalid state`() {
        assertEquals(QrScanResult.Invalid, viewModel.handleQrScan(null))
        assertEquals(PairingFlowState.InvalidQr, viewModel.uiState.value.pairingFlow)
        assertEquals(QrScanResult.Invalid, viewModel.handleQrScan("short"))
        assertEquals(
            QrScanResult.Invalid,
            viewModel.handleQrScan("openbabyphone://pair?childId=%&pairingId=x&name=y&code=z")
        )
    }

    @Test
    fun `legacy qr returns code for fallback without creating pending request`() {
        val result = viewModel.handleQrScan("code1234")

        assertEquals(QrScanResult.Legacy("code1234"), result)
        assertEquals(PairingFlowState.Idle, viewModel.uiState.value.pairingFlow)
    }

    @Test
    fun `delayed exact identity match becomes ready`() {
        viewModel.handleQrScan(payload())

        viewModel.recordResolvedDevice(device())

        val ready = viewModel.uiState.value.pairingFlow as PairingFlowState.Ready
        assertEquals("Nursery", ready.childName)
        assertEquals("child-1", ready.request.childId)
        assertEquals("pair-1", ready.request.pairingId)
        assertEquals("host", pendingStore.lease(ready.request.requestId)?.address)
    }

    @Test
    fun `matching requires both child and pairing generation`() {
        viewModel.handleQrScan(payload())

        viewModel.recordResolvedDevice(device(pairingId = "pair-new"))
        assertTrue(viewModel.uiState.value.pairingFlow is PairingFlowState.LookingForChild)
        viewModel.recordResolvedDevice(device(childId = "child-other"))
        assertTrue(viewModel.uiState.value.pairingFlow is PairingFlowState.LookingForChild)
        viewModel.recordResolvedDevice(device())

        assertTrue(viewModel.uiState.value.pairingFlow is PairingFlowState.Ready)
    }

    @Test
    fun `search times out after twelve seconds`() = runTest(dispatcher) {
        viewModel.handleQrScan(payload())

        advanceTimeBy(DiscoverViewModel.PAIRING_TIMEOUT_MS)
        runCurrent()

        assertTrue(viewModel.uiState.value.pairingFlow is PairingFlowState.ChildNotFound)
    }

    @Test
    fun `retry reuses pending request and accepts delayed match`() = runTest(dispatcher) {
        viewModel.handleQrScan(payload())
        advanceTimeBy(DiscoverViewModel.PAIRING_TIMEOUT_MS)
        runCurrent()
        val oldRequest = (viewModel.uiState.value.pairingFlow as PairingFlowState.ChildNotFound).requestId

        viewModel.retryPairingSearch()
        viewModel.recordResolvedDevice(device())

        val ready = viewModel.uiState.value.pairingFlow as PairingFlowState.Ready
        assertEquals(oldRequest, ready.request.requestId)
    }

    @Test
    fun `cancel wipes pending scan and prevents timeout transition`() = runTest(dispatcher) {
        viewModel.handleQrScan(payload())
        val requestId = (viewModel.uiState.value.pairingFlow as PairingFlowState.LookingForChild).requestId

        viewModel.cancelPairingSearch()
        advanceTimeBy(DiscoverViewModel.PAIRING_TIMEOUT_MS)
        runCurrent()

        assertFalse(pendingStore.contains(requestId))
        assertEquals(PairingFlowState.Idle, viewModel.uiState.value.pairingFlow)
    }

    @Test
    fun `new scan cancels previous pending request`() {
        viewModel.handleQrScan(payload())
        val oldRequest = (viewModel.uiState.value.pairingFlow as PairingFlowState.LookingForChild).requestId

        viewModel.handleQrScan(
            PairingQrCode.buildPayload("child-2", "pair-2", "Bedroom", "code5678")
        )

        assertFalse(pendingStore.contains(oldRequest))
        assertEquals(
            "child-2",
            (viewModel.uiState.value.pairingFlow as PairingFlowState.LookingForChild).childId
        )
    }

    @Test
    fun `pairing reset is distinct from not found`() {
        trust("child-1", "pair-old", "Nursery", "code1234")
        viewModel.refreshTrustedChildren()

        assertEquals(KnownChildStatus.NotFound, viewModel.uiState.value.knownChildren.single().status)
        viewModel.recordResolvedDevice(device(pairingId = "pair-new"))

        assertEquals(KnownChildStatus.PairAgain, viewModel.uiState.value.knownChildren.single().status)
        assertEquals(KnownConnectionResult.PairAgain, viewModel.prepareKnownConnection("child-1"))
    }

    @Test
    fun `known online child creates protected credential request`() {
        trust("child-1", "pair-1", "Nursery", "code1234")
        viewModel.refreshTrustedChildren()
        viewModel.recordResolvedDevice(device())

        val result = viewModel.prepareKnownConnection("child-1") as KnownConnectionResult.Ready
        val pending = pendingStore.lease(result.request.requestId)

        assertNull(pending?.pairingCode)
        assertEquals("child-1", pending?.expectedChildId)
        assertEquals("pair-1", pending?.expectedPairingId)
        assertEquals("host", pending?.address)
    }

    @Test
    fun `multiple trusted children retain independent statuses`() {
        trust("child-1", "pair-1", "Nursery", "code1234")
        trust("child-2", "pair-2", "Bedroom", "code5678")
        viewModel.refreshTrustedChildren()
        viewModel.recordResolvedDevice(device())

        assertEquals(2, viewModel.uiState.value.knownChildren.size)
        assertEquals(
            mapOf("Nursery" to KnownChildStatus.Available, "Bedroom" to KnownChildStatus.NotFound),
            viewModel.uiState.value.knownChildren.associate { it.child.displayName to it.status }
        )
    }

    @Test
    fun `duplicate nsd records are deduped by child identity`() {
        viewModel.recordResolvedDevice(device(address = "old-host"))
        viewModel.recordResolvedDevice(
            device(
                pairingId = "new-generation",
                address = "new-host",
                name = "replacement-service"
            )
        )

        assertEquals(1, viewModel.uiState.value.devices.size)
        assertEquals("new-host", viewModel.uiState.value.devices.single().address)
        assertEquals("new-generation", viewModel.uiState.value.devices.single().pairingId)
    }

    private fun payload() =
        PairingQrCode.buildPayload("child-1", "pair-1", "Nursery", "code1234")

    private fun device(
        childId: String = "child-1",
        pairingId: String = "pair-1",
        address: String = "host",
        name: String = "service"
    ) = DiscoveredDevice(name, address, 10000, childId, pairingId, "Nursery")

    private fun trust(
        childId: String,
        pairingId: String,
        displayName: String,
        code: String
    ) {
        val chars = code.toCharArray()
        try {
            assertEquals(
                CredentialStorageResult.Success,
                trustedStore.trustAuthenticated(
                    childId,
                    pairingId,
                    displayName,
                    chars,
                    "last-known-host",
                    10000
                )
            )
        } finally {
            chars.fill('\u0000')
        }
    }
}
