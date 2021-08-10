/*
 * Corda Restate: Generate Corda Contract and Persistent states
 * from a simplified model interface.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */


package com.github.manosbatsis.corda.restate.example.contract

import com.github.manosbatsis.corda.restate.annotation.PropertyMappingMode
import com.github.manosbatsis.corda.restate.annotation.RestateModel
import com.github.manosbatsis.corda.restate.annotation.RestateProperty
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*
import javax.persistence.Column

// Contract and state.
val NEWSPAPER_CONTRACT_PACKAGE = NewsPaperContract::class.java.`package`.name
val NEWSPAPER_CONTRACT_ID = NewsPaperContract::class.java.canonicalName

class NewsPaperContract : Contract {

    val contractStateType = NewsPaperContractState::class.java

    /**
     * Contract commands
     */
    interface Commands : CommandData {
        /** Create the initial state */
        class Create : TypeOnlyCommandData(), Commands

        /** Create the updated state */
        class Update : TypeOnlyCommandData(), Commands

        /** Delete the state */
        class Delete : TypeOnlyCommandData(), Commands
    }

    @RestateModel(
            mappingModes = [
                PropertyMappingMode.NATIVE,
                PropertyMappingMode.STRINGIFY,
                PropertyMappingMode.EXPANDED
            ]
    )
    interface NewsPaper {
        val publisher: Party?
        val author: Party
        val price: BigDecimal
        @get:RestateProperty(initializer = "1")
        val editions: Int
        val title: String
        @get:RestateProperty(initializer = "Date()")
        val published: Date
        @get:Column(name = "alt_title", length = 500)
        val alternativeTitle: String?

        // No need to add or override explicitly
        //val linearId: UniqueIdentifier

        // Only extend LinearState and override if you don't want the implementation generated by default
        //override val participants get() = listOfNotNull(publisher?.party, author.party)

        // Only extend QueryableState and override if you don't want the implementation generated by default
        //override fun generateMappedObject(schema: MappedSchema) = NewsPaperPersistentState(
        //        ...
        //)

        // Only extend QueryableState and override if you don't want the implementation generated by default
        //override fun supportedSchemas() = listOf(SchemaV1)

        // Only add if you explicitly implement supportedSchemas()
        //object Schema
        //object NSchemaV1 : MappedSchema(NewsPaperSchema::class.java, 1, listOf(NewsPaperPersistentState::class.java))
    }


    /**
     * Verify transactions
     */
    override fun verify(tx: LedgerTransaction) {
        // Ensure only one of this contract's commands is present
        val command = tx.commands.requireSingleCommand<Commands>()
        // Forward to command-specific verification
        val signers = command.signers.toSet()
        when (command.value) {
            is Commands.Create -> verifyCreate(tx, signers)
            is Commands.Update -> verifyUpdate(tx, signers)
            is Commands.Delete -> verifyDelete(tx, signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val command = tx.commands.requireSingleCommand<Commands.Create>()
        "There can be no inputs when creating publications." using (tx.inputs.isEmpty())
        "There must be one output publication" using (tx.outputs.size == 1)
        val yo = tx.outputsOfType(contractStateType).single()
        "Cannot publish your own publication!" using (yo.author != yo.publisher)
        "The publication must be signed by the publisher." using (command.signers.contains(owningKey(yo.publisher)))
        "The publication must be signed by the author." using (command.signers.contains(owningKey(yo.author)))
    }

    fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val command = tx.commands.requireSingleCommand<Commands.Update>()
        "There must be one input publication." using (tx.inputs.size == 1)
        "There must be one output publication" using (tx.outputs.size == 1)
        val yo = tx.outputsOfType(contractStateType).single()
        "Cannot publish your own publication!" using (yo.author != yo.publisher)
        "The publication must be signed by the publisher." using (command.signers.contains(owningKey(yo.publisher)))
        "The publication must be signed by the author." using (command.signers.contains(owningKey(yo.author)))
    }

    fun verifyDelete(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val command = tx.commands.requireSingleCommand<Commands.Delete>()
        "There must be one input publication." using (tx.inputs.size == 1)
        "There must no output publication" using (tx.outputs.isEmpty())
        val yo = tx.outputsOfType(contractStateType).single()
        "Cannot delete your own publication!" using (yo.author != yo.publisher)
        "The publication deletion must be signed by the publisher." using (command.signers.contains(owningKey(yo.publisher)))
        "The publication must be signed by the author." using (command.signers.contains(owningKey(yo.author)))
    }


    fun owningKey(party: Party?): PublicKey? {
        return party?.owningKey
    }
}


