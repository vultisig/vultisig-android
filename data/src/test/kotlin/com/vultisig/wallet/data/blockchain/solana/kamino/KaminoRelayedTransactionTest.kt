package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * What a co-signing device can work out about a relayed Kamino transaction from its bytes alone
 * (issue #5644).
 *
 * The instruction shapes here are the ones the live fixtures in `KaminoFixtureApi` decode to — an
 * associated-account creation, the kVault instruction, the farms pair, the compute budget and the
 * attribution memo last — with the discriminators and account lists copied from that decode.
 */
class KaminoRelayedTransactionTest {

    private val vault = KaminoVaultRegistry.STEAKHOUSE_USDC
    private val solVault = KaminoVaultRegistry.ALLEZ_SOL

    private val signer = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

    /** The signer's share account for [vault] — what names the vault inside the bytes. */
    private val shareAccount = "GSayQpRaoh1LFdBbja4vensNKDfihcixzCcQShKMCdMJ"

    private val vaultsByShareAccount = mapOf(shareAccount to vault)

    private val depositDiscriminator = hex("f223c68952e1f2b6")
    private val withdrawDiscriminator = hex("1383709baadc2239")

    private val memo =
        KaminoTxInstruction(
            programId = KaminoAttributionMemo.MEMO_PROGRAM_ID,
            data = KaminoAttributionMemo.TAG.toByteArray(),
        )

    @Test
    fun `the kVault discriminators are the Anchor names they claim to be`() {
        // Pinned as bytes in the reader, so this is where the derivation is written down: get one
        // wrong and every Kamino transaction silently stops being recognised.
        assertEquals(anchorDiscriminator("deposit").toList(), depositDiscriminator.toList())
        assertEquals(
            anchorDiscriminator("withdraw_from_available").toList(),
            withdrawDiscriminator.toList(),
        )
    }

    @Test
    fun `a deposit is read as its vault, direction and token amount`() {
        val intent = read(depositInstructions(amount = ONE_USDC))

        assertEquals(vault, intent?.vault)
        assertEquals(KaminoAction.DEPOSIT, intent?.action)
        assertEquals(ONE_USDC, intent?.amount)
    }

    @Test
    fun `a withdraw is read as a withdraw, carrying shares rather than tokens`() {
        val shares = BigInteger.valueOf(500_000)
        val intent = read(withdrawInstructions(amount = shares))

        assertEquals(KaminoAction.WITHDRAW, intent?.action)
        assertEquals(shares, intent?.amount)
    }

    @Test
    fun `a transaction that never invokes kVault is not a Kamino transaction`() {
        val instructions = depositInstructions().filterNot { it.programId == KVAULT }

        assertNull(read(instructions))
    }

    @Test
    fun `two kVault instructions are refused rather than half-described`() {
        val instructions = depositInstructions() + kvault(depositDiscriminator, ONE_USDC)

        assertNull(read(instructions))
    }

    @Test
    fun `a kVault instruction the app has no name for is not guessed at`() {
        // A third entry point would move value in a way neither screen describes; better the
        // co-signer keeps the generic screen than is shown a deposit that is not one.
        val instructions =
            depositInstructions().map { instruction ->
                if (instruction.programId == KVAULT) {
                    kvault(hex("0102030405060708"), ONE_USDC)
                } else {
                    instruction
                }
            }

        assertNull(read(instructions))
    }

    @Test
    fun `a vault whose share account is not named cannot be identified`() {
        // The vault's own address travels in an address lookup table, so an unresolved share
        // account leaves nothing to name it with.
        assertNull(
            KaminoRelayedTransactionReader.read(
                decoded(depositInstructions()),
                vaultsByShareAccount = emptyMap(),
            )
        )
    }

    @Test
    fun `an instruction too short to carry an amount is refused`() {
        val instructions =
            depositInstructions().map { instruction ->
                if (instruction.programId == KVAULT) {
                    KaminoTxInstruction(KVAULT, depositDiscriminator, listOf(signer, shareAccount))
                } else {
                    instruction
                }
            }

        assertNull(read(instructions))
    }

    @Test
    fun `recognition runs the same validator the initiating device ran`() {
        // The memo is what the initiating device appends last; a transaction missing it is not one
        // this app built, whatever its instructions say.
        assertNull(resolve(depositInstructions(memoInstruction = null)))
    }

    @Test
    fun `a payload carrying more than one transaction is not a Kamino payload`() {
        val useCase = useCase(depositInstructions())

        assertNull(useCase(listOf(RAW, RAW), signer))
    }

    @Test
    fun `bytes that cannot be decoded are not a Kamino payload`() {
        val useCase =
            ResolveKaminoRelayedIntentUseCase(
                decode = { error("not a Solana transaction") },
                tokenAccount = ::fakeTokenAccount,
            )

        assertNull(useCase(listOf(RAW), signer))
    }

    @Test
    fun `a recognised deposit reaches the caller`() {
        assertEquals(vault, resolve(depositInstructions())?.vault)
    }

    @Test
    fun `the intent carries the budget from the instructions, not from beside them`() {
        // What the fee row is priced from on the joining device. Read here rather than taken from
        // the payload's own field, which is a display value the initiating device filled in.
        val intent =
            read(
                depositInstructions(
                    budget =
                        computeBudgetInstructions(
                            vault,
                            KaminoAction.DEPOSIT,
                            price = BigInteger.valueOf(250_000),
                        )
                )
            )

        assertEquals(
            KaminoPriorityFee(
                limit = KaminoComputeBudget.unitLimitFor(vault, KaminoAction.DEPOSIT),
                price = BigInteger.valueOf(250_000),
            ),
            intent?.priorityFee,
        )
    }

    @Test
    fun `a transaction priced outside the app's own range never reaches the caller`() {
        // Recognition re-runs the initiating device's validator, so an unbounded price is refused
        // here too rather than quoted under a fee row that clamps its own display.
        assertNull(
            resolve(
                depositInstructions(
                    budget =
                        computeBudgetInstructions(
                            vault,
                            KaminoAction.DEPOSIT,
                            price = KaminoComputeBudget.MAX_UNIT_PRICE.add(BigInteger.ONE),
                        )
                )
            )
        )
    }

    @Test
    fun `a transaction needing a second signature never reaches the caller`() {
        val useCase =
            ResolveKaminoRelayedIntentUseCase(
                decode = { decoded(depositInstructions(), requiredSignatures = 2) },
                tokenAccount = ::fakeTokenAccount,
            )

        assertNull(useCase(listOf(RAW), signer))
    }

    @Test
    fun `the network fee is the relayed budget plus the rent the deposit spends`() {
        val rent = BigInteger.valueOf(10_377_640)

        val fee =
            kaminoNetworkFeeLamports(
                vault = solVault,
                action = KaminoAction.DEPOSIT,
                relayedUnitPrice = BigInteger.valueOf(250_000),
                rentReserve = rent,
            )

        // 1,000,000 base + (250,000 µlamports × 350,000 units / 1e6) + rent.
        assertEquals(BigInteger.valueOf(1_000_000 + 87_500) + rent, fee)
    }

    @Test
    fun `a withdraw quotes the budget alone, with no rent to spend`() {
        val fee =
            kaminoNetworkFeeLamports(
                vault = solVault,
                action = KaminoAction.WITHDRAW,
                relayedUnitPrice = BigInteger.valueOf(250_000),
            )

        // 1,000,000 base + (250,000 × 400,000 / 1e6).
        assertEquals(BigInteger.valueOf(1_000_000 + 100_000), fee)
    }

    private fun read(instructions: List<KaminoTxInstruction>): KaminoRelayedIntent? =
        KaminoRelayedTransactionReader.read(decoded(instructions), vaultsByShareAccount)

    private fun resolve(instructions: List<KaminoTxInstruction>): KaminoRelayedIntent? =
        useCase(instructions)(listOf(RAW), signer)

    private fun useCase(instructions: List<KaminoTxInstruction>) =
        ResolveKaminoRelayedIntentUseCase(
            decode = { decoded(instructions) },
            tokenAccount = ::fakeTokenAccount,
        )

    /** Stands in for WalletCore's derivation, answering the share account the bytes name. */
    private fun fakeTokenAccount(owner: String, mint: String): String? =
        if (owner == signer && mint == vault.sharesMint) shareAccount else "other-$mint"

    private fun decoded(instructions: List<KaminoTxInstruction>, requiredSignatures: Int = 1) =
        KaminoDecodedTransaction(
            feePayer = signer,
            instructions = instructions,
            requiredSignatures = requiredSignatures,
            isUnsigned = true,
        )

    private fun depositInstructions(
        amount: BigInteger = ONE_USDC,
        memoInstruction: KaminoTxInstruction? = memo,
        budget: List<KaminoTxInstruction> = computeBudgetInstructions(vault, KaminoAction.DEPOSIT),
    ) =
        budget +
            listOfNotNull(
                createAta(shareAccount),
                kvault(depositDiscriminator, amount),
                farms(hex("6f11b9fa3c7a26fe"), userStateSlot = 4),
                farms(
                    hex("ceb0ca12c8d1b36c"),
                    userStateSlot = 1,
                    argument = U64_MAX.toLittleEndianU64(),
                    shareAccountSlot = 4,
                ),
                memoInstruction,
            )

    private fun withdrawInstructions(amount: BigInteger) =
        computeBudgetInstructions(vault, KaminoAction.WITHDRAW) +
            listOf(
                createAta(fakeTokenAccount(signer, vault.tokenMint)),
                kvault(withdrawDiscriminator, amount),
                memo,
            )

    /**
     * The kVault instruction at the layout the validator reads: the signer as authority, the
     * wallet's token account where the action puts it, and its share account at slot 7 — which is
     * also what names the vault to [KaminoRelayedTransactionReader].
     */
    private fun kvault(discriminator: ByteArray, amount: BigInteger): KaminoTxInstruction {
        val deposit = discriminator.contentEquals(depositDiscriminator)
        val accounts = MutableList(9) { KaminoTransactionDecoder.UNKNOWN_ACCOUNT }
        accounts[0] = signer
        accounts[if (deposit) 6 else 5] = fakeTokenAccount(signer, vault.tokenMint).orEmpty()
        accounts[7] = shareAccount
        return KaminoTxInstruction(
            programId = KVAULT,
            data = discriminator + amount.toLittleEndianU64(),
            accounts = accounts,
        )
    }

    /**
     * A farms instruction naming the state this wallet derives for the vault's farm — the account
     * that says which stake is moving, since the farm itself rides in a lookup table.
     */
    private fun farms(
        discriminator: ByteArray,
        userStateSlot: Int,
        argument: ByteArray = ByteArray(0),
        shareAccountSlot: Int? = null,
    ): KaminoTxInstruction {
        val accounts = MutableList(6) { KaminoTransactionDecoder.UNKNOWN_ACCOUNT }
        accounts[0] = signer
        accounts[userStateSlot] = KaminoFarmsUserState.derive(vault.farm, signer).orEmpty()
        shareAccountSlot?.let { accounts[it] = shareAccount }
        return KaminoTxInstruction(
            programId = KaminoVaultRegistry.FARMS_PROGRAM_ID,
            data = discriminator + argument,
            accounts = accounts,
        )
    }

    /** `CreateIdempotent` for one of the wallet's own accounts, as every captured shape opens. */
    private fun createAta(account: String?) =
        KaminoTxInstruction(
            programId = ASSOCIATED_TOKEN,
            data = byteArrayOf(1),
            accounts = listOf(signer, account.orEmpty(), signer),
        )

    private fun BigInteger.toLittleEndianU64(): ByteArray =
        ByteArray(8) { index -> shiftRight(index * 8).and(BigInteger.valueOf(0xFF)).toByte() }

    private fun anchorDiscriminator(name: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("global:$name".toByteArray()).copyOfRange(0, 8)

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private companion object {
        const val KVAULT = KaminoVaultRegistry.PROGRAM_ID
        const val ASSOCIATED_TOKEN = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"

        /** Any string: the decode is faked, so the bytes themselves are never read. */
        const val RAW = "relayed-transaction"

        val ONE_USDC: BigInteger = BigInteger.valueOf(1_000_000)

        /** What `farms::stake` always carries: stake the whole share balance. */
        val U64_MAX: BigInteger = BigInteger("18446744073709551615")
    }
}
