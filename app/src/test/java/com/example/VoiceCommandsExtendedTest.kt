package com.example

import com.example.ui.util.VoiceCommand
import com.example.ui.util.VoiceInvoiceParser
import org.junit.Assert.*
import org.junit.Test

class VoiceCommandsExtendedTest {

    @Test
    fun testAddCustomerVoiceCommand() {
        val cmd1 = VoiceInvoiceParser.parseVoiceCommand("أضف عميل اسمه أحمد سالم")
        assertTrue(cmd1 is VoiceCommand.AddCustomer)
        assertEquals("أحمد سالم", (cmd1 as VoiceCommand.AddCustomer).customerName)

        val cmd2 = VoiceInvoiceParser.parseVoiceCommand("اضف عميل محمد الغامدي")
        assertTrue(cmd2 is VoiceCommand.AddCustomer)
        assertEquals("محمد الغامدي", (cmd2 as VoiceCommand.AddCustomer).customerName)
    }

    @Test
    fun testAddProductVoiceCommand() {
        val cmd = VoiceInvoiceParser.parseVoiceCommand("أضف صنف قلم جاف بسعر 500")
        assertTrue(cmd is VoiceCommand.AddProduct)
        val prod = cmd as VoiceCommand.AddProduct
        assertEquals("قلم جاف", prod.productName)
        assertEquals(500.0, prod.price, 0.01)
    }

    @Test
    fun testDeleteAndClearVoiceCommands() {
        val cmdDelete = VoiceInvoiceParser.parseVoiceCommand("احذف عصير")
        assertTrue(cmdDelete is VoiceCommand.DeleteItem)
        assertEquals("عصير", (cmdDelete as VoiceCommand.DeleteItem).query)

        val cmdClear = VoiceInvoiceParser.parseVoiceCommand("مسح الفاتورة")
        assertTrue(cmdClear is VoiceCommand.ClearInvoice)
    }

    @Test
    fun testAddInvoiceItemsVoiceCommand() {
        val cmd = VoiceInvoiceParser.parseVoiceCommand("4000 كرتون عصير 2")
        assertTrue(cmd is VoiceCommand.Add)
        val items = (cmd as VoiceCommand.Add).items
        assertEquals(1, items.size)
        assertEquals("4000", items[0].totalAmount)
        assertEquals("2", items[0].quantity)
        assertTrue(items[0].description.contains("عصير"))
    }
}
