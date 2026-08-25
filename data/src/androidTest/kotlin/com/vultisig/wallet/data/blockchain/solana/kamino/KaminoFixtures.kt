package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoPnlJson
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.api.KaminoVaultMetricsJson

/**
 * Live Kamino responses, captured once and shared by the tests that exercise the prepare pipeline.
 *
 * Kept here rather than inside one test class so the payload coverage and the preparer coverage
 * assert against the same bytes — the point of both is that what the payload records and what the
 * transaction carries are the same compute budget.
 */
internal object KaminoFixtures {

    /** A live `POST /ktx/kvault/deposit` response for the Steakhouse USDC vault. */
    const val DEPOSIT =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA" +
            "AQAFCX//JB25KLX6LxwUe5Pw/FGXqeETK7Jj6GQYPDbOSuemOEsQm7A2IghRdbzU0ar6Q7dIhEJA6xfcD32X" +
            "M4YwfPSF38N5IeLVVX5/3BJfld0IR08X1RB7fe6uQkeVw/nk3uVtPK//R3XanFktvqbKIbsjaArJEZzlqWvc" +
            "axKftHFhAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP2mzp9mGY3YcNiphvMGdcRvg+L1KDSHlS" +
            "9dUFY+PveoyXJY9OJInxuz0QKRSODYMLWhOZ2v8QhASOe9jb6fhZ2LAQF2PT5R8SbmFW3oXejGEwWbhEaNDa" +
            "P+iioiUcxwEE2Qrx24k57DX/lNlkDVfcwyeUuz4btm/TroSahNzblDJCgcwkFVHRKh2nMxb4pfSsIMN/m4f+" +
            "R1HblBX15CRABAYGAAMACQQaAQEIFQARDRYUCQEDGBoaBQgQDwoMFRMXEhDyI8aJUuHytkBCDwAAAAAABwgA" +
            "AAAAAgsEGQhvEbn6PHom/gcIAAILDgMJBxoQzrDKEsjRs2z//////////wGC6dRmw0Z9LrKw2cy+tHvb9JXu" +
            "HBu0d9Dar60DX6fr5AkFNTElLzITCQEJJxUECwI3BwYD"

    /**
     * A live `POST /ktx/kvault/withdraw` response for the same vault: create the destination token
     * account, then `kVault::withdraw`.
     *
     * Kamino built it for a wallet holding no position at all — the clearest evidence that the
     * endpoint validates nothing about the amount it is handed, and also why there is nothing
     * staked to release here.
     *
     * **This is therefore the UNSTAKED shape, which is not the one most holders will send.** A
     * withdraw against a staked position carries two extra `farms` instructions and a second
     * account creation ahead of the vault withdraw. Capturing that needs a wallet actually holding
     * a staked position, which these vaults only produce by depositing real funds, so the limit
     * sized for it rests on the iOS mainnet measurements (283,786 / 289,486 / 309,310) rather than
     * on anything this suite proves.
     */
    const val WITHDRAW =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA" +
            "AQAFCH//JB25KLX6LxwUe5Pw/FGXqeETK7Jj6GQYPDbOSuemOEsQm7A2IghRdbzU0ar6Q7dIhEJA6xfcD32XM4Yw" +
            "fPTlbTyv/0d12pxZLb6myiG7I2gKyRGc5alr3GsSn7RxYQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "D9ps6fZhmN2HDYqYbzBnXEb4Pi9Sg0h5UvXVBWPj73qMlyWPTiSJ8bs9ECkUjg2DC1oTmdr/EIQEjnvY2+n4WZlx" +
            "KXooAEJJPkJxkMd7ZH6G99GZch/RVimWXJ1rZZT4BNkK8duJOew1/5TZZA1X3MMnlLs+G7Zv066EmoTc25TcFsAN" +
            "s3q6CtgzQiIGiofmyao3Sh0Dq7RowlSc92jsQgIFBgABAA0DFgEBBxYADwYLEgENAggWFhUEBw4MCQoTERQQEBOD" +
            "cJuq3CI5//////////8BgunUZsNGfS6ysNnMvrR72/SV7hwbtHfQ2q+tA1+n6+QIBTUlLxMCCQEHJxUECzcHAw=="

    /**
     * A live `POST /ktx/kvault/deposit` response for the Allez SOL vault: create the wrapped-SOL
     * account, move the lamports into it, `SyncNative` to credit them, create the share account,
     * then `kVault::deposit` and the two farms instructions.
     *
     * The wrapped-SOL shape the USDC fixtures never show. It is the only one of these that carries
     * top-level System and Token instructions at all — a plain-token vault moves its tokens through
     * the kVault program's own CPI — so it is what the validator's allow-lists are sized against.
     *
     * Captured against the same empty wallet as the fixtures above, which is what makes this shape
     * observable without depositing real SOL: the endpoint builds the transaction regardless.
     */
    const val ALLEZ_SOL_DEPOSIT =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA" +
            "AQAGDH//JB25KLX6LxwUe5Pw/FGXqeETK7Jj6GQYPDbOSuemJnd9pKyNWm8rX5WMWlaRpEuaYsAMd/0uKk2HNuIL" +
            "qFNvAn5UvuGbp47XxazBMJL84XxTjp/284efVL2pzo4iIesfng/b+jhKCNqqrmiaZ6cpiUcyN06V+7/WNOGHIPzV" +
            "+gzvPgp+u6Wl/vHysCWMtbXQzFi88aWnIsLgwAKpS9z6HoLYlf5IbdNEuFQby9p5byRMsWwK5OpCzyixXzveqAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD9ps6fZhmN2HDYqYbzBnXEb4Pi9Sg0h5UvXVBWPj73qMlyWP" +
            "TiSJ8bs9ECkUjg2DC1oTmdr/EIQEjnvY2+n4WdiwEBdj0+UfEm5hVt6F3oxhMFm4RGjQ2j/ooqIlHMcBBNkK8duJ" +
            "Oew1/5TZZA1X3MMnlLs+G7Zv066EmoTc25QG3fbh12Whk9nL4UbO63msHLSF7V9bN5E6jPWFfv8AqSRNU541+dCq" +
            "v0WinT6QVBdvd+mOjbLg8/uLSxi4xYKOBwgGAAMAHQYLAQEGAgADDAIAAACA8PoCAAAAAAsBAwERCAYABAASBgsB" +
            "AQoZAA8FHRYSAwQcCwsHCg4QDRQRDBsVFxgaGRDyI8aJUuHytoDw+gIAAAAACQgAAAAAAhMGHghvEbn6PHom/gkI" +
            "AAITAQQSCQsQzrDKEsjRs2z//////////wFcvAkvUgYmlpRjmt1MX98IzA/9elzQ9ld+Vs/yAHeYyAlbGwkBEjwF" +
            "UDEKFAQdM0g+CwcCBg=="

    /**
     * A live `POST /ktx/kvault/withdraw` response for the Allez SOL vault: create the account,
     * `kVault::withdraw`, then `Token::CloseAccount` to unwrap back to lamports.
     *
     * The unwrap pays the closed account's whole balance to its second account — on a full exit,
     * the entire withdrawn amount — which is why the validator pins that account to the signer
     * rather than accepting any close. Rewriting one address here would redirect the lot while
     * every other instruction still read as the withdraw the user asked for.
     *
     * Carries the same `u64::MAX` rewrite as [WITHDRAW], for the same reason: the wallet holds no
     * position.
     */
    const val ALLEZ_SOL_WITHDRAW =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA" +
            "AQAFCX//JB25KLX6LxwUe5Pw/FGXqeETK7Jj6GQYPDbOSuem6x+eD9v6OEoI2qquaJpnpymJRzI3TpX7v9Y04Ycg" +
            "/NX6DO8+Cn67paX+8fKwJYy1tdDMWLzxpaciwuDAAqlL3PoegtiV/kht00S4VBvL2nlvJEyxbArk6kLPKLFfO96o" +
            "D9ps6fZhmN2HDYqYbzBnXEb4Pi9Sg0h5UvXVBWPj73qMlyWPTiSJ8bs9ECkUjg2DC1oTmdr/EIQEjnvY2+n4WZlx" +
            "KXooAEJJPkJxkMd7ZH6G99GZch/RVimWXJ1rZZT4BNkK8duJOew1/5TZZA1X3MMnlLs+G7Zv066EmoTc25QG3fbh" +
            "12Whk9nL4UbO63msHLSF7V9bN5E6jPWFfv8AqeYlXCRbrgUpUt0oPyIIjMIS538zeau6KrxNXijxeJJRAwUGAAEA" +
            "ERIIAQEHGgAMBgMUARECDwgIGgQHCw0KEA4JGRMVFhgXEBODcJuq3CI5//////////8IAwEAAAEJAVy8CS9SBiaW" +
            "lGOa3Uxf3wjMD/16XND2V35Wz/IAd5jICVsbCQESPAUxAgkeFAQdM0g+Cwc="

    /** The wallet the fixtures above were built for — their fee payer and only required signer. */
    const val WALLET = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

    /** [WALLET]'s wrapped-SOL associated token account, which the Allez SOL fixtures wrap into. */
    const val WALLET_WRAPPED_SOL = "GppmkdEmuqNgS7uY5SSN3gXEamJrcPG9197wBdQ37NLc"
}

/** Returns the captured transaction for whatever is asked, so the pipeline is what's under test. */
internal class KaminoFixtureApi(private val transaction: String = KaminoFixtures.DEPOSIT) :
    KaminoApi {
    var lastAmount: String? = null
    var lastVault: String? = null

    override suspend fun getVaultState(vaultAddress: String) = error("not used")

    override suspend fun getVaultMetrics(vaultAddress: String) = KaminoVaultMetricsJson()

    override suspend fun getUserPositions(walletAddress: String): List<KaminoUserPositionJson> =
        emptyList()

    override suspend fun getPositionPnl(walletAddress: String, vaultAddress: String) =
        KaminoPnlJson()

    override suspend fun buildDeposit(
        walletAddress: String,
        vaultAddress: String,
        amount: String,
    ): String {
        lastAmount = amount
        lastVault = vaultAddress
        return transaction
    }

    override suspend fun buildWithdraw(
        walletAddress: String,
        vaultAddress: String,
        amount: String,
    ): String {
        lastAmount = amount
        lastVault = vaultAddress
        return transaction
    }
}
