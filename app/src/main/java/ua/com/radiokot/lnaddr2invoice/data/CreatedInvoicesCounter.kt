package ua.com.radiokot.lnaddr2invoice.data

interface CreatedInvoicesCounter {
    val createdInvoiceCount: Int
    fun incrementCreatedInvoices()
}