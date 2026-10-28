package cz.poqet.walletka.service

import cz.poqet.walletka.api.WalletkaApi
import kotlin.test.Test
import kotlin.test.assertEquals

/** The app's operations (shared via `:serviceApi`) must be valid against the schema the Worker serves. */
class SchemaContractTest {

    @Test
    fun appOperationsMatchTheSchema() {
        val schema = buildSchema(WalletkaApi.SCHEMA)
        for (operation in WalletkaApi.operations) {
            val errors = validate(schema, parse(operation)).map { it.message as String }
            assertEquals(emptyList(), errors, operation)
        }
    }
}
