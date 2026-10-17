package com.example

import com.example.domain.einvoicing.StandardEInvoicingExporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EInvoicingExporterTest {

    @Test
    fun testGenerateQrCodePayload() {
        val exporter = StandardEInvoicingExporter()
        val payload = exporter.generateQrCodePayload(
            sellerName = "بقالة العزي",
            vatRegistrationNumber = "300000000000003",
            timestampIso = "2026-10-17T15:00:00Z",
            invoiceTotalWithVat = 1150.0,
            vatAmount = 150.0
        )
        assertNotNull(payload)
        assertTrue(payload.isNotBlank())
        // Base64 string check
        assertTrue(payload.matches("^[A-Za-z0-9+/=]+$".toRegex()))
    }
}
