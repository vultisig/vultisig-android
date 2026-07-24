package com.vultisig.wallet.data.models

object Coins {
    object Akash {
        val AKT =
            Coin(
                chain = Chain.Akash,
                ticker = "AKT",
                logo = "akash",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "akash-network",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(AKT)
    }

    object Arbitrum {
        val ARB =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "ARB",
                logo = "arbitrum",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "arbitrum",
                contractAddress = "0x912CE59144191C1204E64559FE8253a0e49E6548",
                isNativeToken = false,
            )

        val DAI =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "DAI",
                logo = "dai",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "DAI",
                contractAddress = "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1",
                isNativeToken = false,
            )

        val ETH =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )

        val FOX =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "FOX",
                logo = "fox",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "shapeshift-fox-token",
                contractAddress = "0xf929de51D91C77E42f5090069E0AD7A09e513c73",
                isNativeToken = false,
            )

        val GRT =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "GRT",
                logo = "grt",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "GRT",
                contractAddress = "0x9623063377AD1B27544C965cCd7342f7EA7e88C7",
                isNativeToken = false,
            )

        val LDO =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "LDO",
                logo = "ldo",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "LDO",
                contractAddress = "0x13Ad51ed4F1B7e9Dc168d8a00cB3f4dDD85EfA60",
                isNativeToken = false,
            )

        val LINK =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "LINK",
                logo = "link",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "LINK",
                contractAddress = "0xf97f4df75117a78c1A5a0DBb814Af92458539FB4",
                isNativeToken = false,
            )

        val PEPE =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "PEPE",
                logo = "pepe",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "PEPE",
                contractAddress = "0x25d887Ce7a35172C62FeBFD67a1856F20FaEbB00",
                isNativeToken = false,
            )

        val PYTH =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "PYTH",
                logo = "pyth",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "pyth-network",
                contractAddress = "0xE4D5c6aE46ADFAF04313081e8C0052A30b6Dd724",
                isNativeToken = false,
            )

        val TGT =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "TGT",
                logo = "tgt",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "thorwallet",
                contractAddress = "0x429fEd88f10285E61b12BDF00848315fbDfCC341",
                isNativeToken = false,
            )

        val UNI =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "UNI",
                logo = "uni",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "UNI",
                contractAddress = "0xFa7F8980b0f1E64A2062791cc3b0871572f1F7f0",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "0xaf88d065e77c8cC2239327C5EDb3A432268e5831",
                isNativeToken = false,
            )

        val USDC_e =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "USDC.e",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin-ethereum-bridged",
                contractAddress = "0xFF970A61A04b1cA14834A43f5dE4533eBDDB5CC8",
                isNativeToken = false,
            )

        val USDT =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tether",
                contractAddress = "0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9",
                isNativeToken = false,
            )

        val WBTC =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "WBTC",
                logo = "wbtc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "WBTC",
                contractAddress = "0x2f2a2543B76A4166549F7aaB2e75Bef0aefC5B0f",
                isNativeToken = false,
            )

        val ezETH =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "ezETH",
                logo = "ezeth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ezETH",
                contractAddress = "0x2416092f143378750bb29b79eD961ab195CcEea5",
                isNativeToken = false,
            )

        val USDS =
            Coin(
                chain = Chain.Arbitrum,
                ticker = "USDS",
                logo = "usds",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "usds",
                contractAddress = "0x6491c05A82219b8D1479057361ff1654749b876b",
                isNativeToken = false,
            )

        val all =
            listOf(
                ARB,
                DAI,
                ETH,
                FOX,
                GRT,
                LDO,
                LINK,
                PEPE,
                PYTH,
                TGT,
                UNI,
                USDC,
                USDC_e,
                USDS,
                USDT,
                WBTC,
                ezETH,
            )
    }

    object Mantle {
        val MNT =
            Coin(
                chain = Chain.Mantle,
                ticker = "MNT",
                logo = "mantle",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "mantle",
                contractAddress = "",
                isNativeToken = true,
            )

        val USDT =
            Coin(
                chain = Chain.Mantle,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tether",
                contractAddress = "0x201EBa5CC46D216Ce6DC03F6a759e8E766e956aE",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Mantle,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "0x09bc4e0d864854c6afb6eb9a9cdf58ac190d0df9",
                isNativeToken = false,
            )

        val all = listOf(MNT, USDT, USDC)
    }

    object Avalanche {
        val AVAX =
            Coin(
                chain = Chain.Avalanche,
                ticker = "AVAX",
                logo = "avax",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "avalanche-2",
                contractAddress = "",
                isNativeToken = true,
            )

        val BLS =
            Coin(
                chain = Chain.Avalanche,
                ticker = "BLS",
                logo = "bls",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x46B9144771Cb3195D66e4EDA643a7493fADCAF9D",
                isNativeToken = false,
            )

        val BTC_b =
            Coin(
                chain = Chain.Avalanche,
                ticker = "BTC.b",
                logo = "btc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x152b9d0FdC40C096757F570A51E494bd4b943E50",
                isNativeToken = false,
            )

        val COQ =
            Coin(
                chain = Chain.Avalanche,
                ticker = "COQ",
                logo = "coq",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x420FcA0121DC28039145009570975747295f2329",
                isNativeToken = false,
            )

        val JOE =
            Coin(
                chain = Chain.Avalanche,
                ticker = "JOE",
                logo = "joe",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x6e84a6216eA6dACC71eE8E6b0a5B7322EEbC0fDd",
                isNativeToken = false,
            )

        val PNG =
            Coin(
                chain = Chain.Avalanche,
                ticker = "PNG",
                logo = "png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x60781C2586D68229fde47564546784ab3fACA982",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Avalanche,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e",
                isNativeToken = false,
            )

        val USDT =
            Coin(
                chain = Chain.Avalanche,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x9702230A8Ea53601f5cD2dc00fDBc13d4dF4A8c7",
                isNativeToken = false,
            )

        val WAVAX =
            Coin(
                chain = Chain.Avalanche,
                ticker = "WAVAX",
                logo = "avax",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xB31f66AA3C1e785363F0875A1B74E27b85FD66c7",
                isNativeToken = false,
            )

        val aAvaUSDC =
            Coin(
                chain = Chain.Avalanche,
                ticker = "aAvaUSDC",
                logo = "aave",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x625E7708f30cA75bfd92586e17077590C60eb4cD",
                isNativeToken = false,
            )

        val sAVAX =
            Coin(
                chain = Chain.Avalanche,
                ticker = "sAVAX",
                logo = "savax",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x2b2C81e08f1Af8835a78Bb2A90AE924ACE0eA4bE",
                isNativeToken = false,
            )

        val all = listOf(AVAX, BLS, BTC_b, COQ, JOE, PNG, USDC, USDT, WAVAX, aAvaUSDC, sAVAX)
    }

    object Base {
        val AERO =
            Coin(
                chain = Chain.Base,
                ticker = "AERO",
                logo = "aero",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "aerodrome-finance",
                contractAddress = "0x940181a94A35A4569E4529A3CDfB74e38FD98631",
                isNativeToken = false,
            )

        val DAI =
            Coin(
                chain = Chain.Base,
                ticker = "DAI",
                logo = "dai",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "dai",
                contractAddress = "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb",
                isNativeToken = false,
            )

        val ETH =
            Coin(
                chain = Chain.Base,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )

        val OM =
            Coin(
                chain = Chain.Base,
                ticker = "OM",
                logo = "om",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "mantra-dao",
                contractAddress = "0x3992B27dA26848C2b19CeA6Fd25ad5568B68AB98",
                isNativeToken = false,
            )

        val PYTH =
            Coin(
                chain = Chain.Base,
                ticker = "PYTH",
                logo = "pyth",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "pyth-network",
                contractAddress = "0x4c5d8A75F3762c1561D96f177694f67378705E98",
                isNativeToken = false,
            )

        val SNX =
            Coin(
                chain = Chain.Base,
                ticker = "SNX",
                logo = "snx",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "SNX",
                contractAddress = "0x22e6966B799c4D5B13BE962E1D117b56327FDa66",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Base,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
                isNativeToken = false,
            )

        val W =
            Coin(
                chain = Chain.Base,
                ticker = "W",
                logo = "w",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "w",
                contractAddress = "0xB0fFa8000886e57F86dd5264b9582b2Ad87b2b91",
                isNativeToken = false,
            )

        val cbETH =
            Coin(
                chain = Chain.Base,
                ticker = "cbETH",
                logo = "cbeth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "cbETH",
                contractAddress = "0x2Ae3F1Ec7F1F5012CFEab0185bfc7aa3cf0DEc22",
                isNativeToken = false,
            )

        val ezETH =
            Coin(
                chain = Chain.Base,
                ticker = "ezETH",
                logo = "ezeth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ezETH",
                contractAddress = "0x2416092f143378750bb29b79eD961ab195CcEea5",
                isNativeToken = false,
            )

        val rETH =
            Coin(
                chain = Chain.Base,
                ticker = "rETH",
                logo = "reth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "reth",
                contractAddress = "0xB6fe221Fe9EeF5aBa221c348bA20A1Bf5e73624c",
                isNativeToken = false,
            )

        val USDS =
            Coin(
                chain = Chain.Base,
                ticker = "USDS",
                logo = "usds",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "usds",
                contractAddress = "0x820C137fa70C8691f0e44dC420a5e53c168921Dc",
                isNativeToken = false,
            )

        val all = listOf(AERO, DAI, ETH, OM, PYTH, SNX, USDC, USDS, W, cbETH, ezETH, rETH)
    }

    object Bitcoin {
        val BTC =
            Coin(
                chain = Chain.Bitcoin,
                ticker = "BTC",
                logo = "btc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "bitcoin",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(BTC)
    }

    object BitcoinCash {
        val BCH =
            Coin(
                chain = Chain.BitcoinCash,
                ticker = "BCH",
                logo = "bch",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "bitcoin-cash",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(BCH)
    }

    object Blast {
        val AI =
            Coin(
                chain = Chain.Blast,
                ticker = "AI",
                logo = "anyinu",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x764933fbAd8f5D04Ccd088602096655c2ED9879F",
                isNativeToken = false,
            )

        val BAG =
            Coin(
                chain = Chain.Blast,
                ticker = "BAG",
                logo = "bag",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xb9dfCd4CF589bB8090569cb52FaC1b88Dbe4981F",
                isNativeToken = false,
            )

        val BLAST =
            Coin(
                chain = Chain.Blast,
                ticker = "BLAST",
                logo = "blast",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xb1a5700fA2358173Fe465e6eA4Ff52E36e88E2ad",
                isNativeToken = false,
            )

        val DACKIE =
            Coin(
                chain = Chain.Blast,
                ticker = "DACKIE",
                logo = "dackie",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x47C337Bd5b9344a6F3D6f58C474D9D8cd419D8cA",
                isNativeToken = false,
            )

        val ETH =
            Coin(
                chain = Chain.Blast,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )

        val JUICE =
            Coin(
                chain = Chain.Blast,
                ticker = "JUICE",
                logo = "juice",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x818a92bc81Aad0053d72ba753fb5Bc3d0C5C0923",
                isNativeToken = false,
            )

        val MIM =
            Coin(
                chain = Chain.Blast,
                ticker = "MIM",
                logo = "mim",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x76DA31D7C9CbEAE102aff34D3398bC450c8374c1",
                isNativeToken = false,
            )

        val OMNI =
            Coin(
                chain = Chain.Blast,
                ticker = "OMNI",
                logo = "omni",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x9e20461bc2c4c980f62f1B279D71734207a6A356",
                isNativeToken = false,
            )

        val USDB =
            Coin(
                chain = Chain.Blast,
                ticker = "USDB",
                logo = "usdb",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x4300000000000000000000000000000000000003",
                isNativeToken = false,
            )

        val WBTC =
            Coin(
                chain = Chain.Blast,
                ticker = "WBTC",
                logo = "wbtc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xF7bc58b8D8f97ADC129cfC4c9f45Ce3C0E1D2692",
                isNativeToken = false,
            )

        val WETH =
            Coin(
                chain = Chain.Blast,
                ticker = "WETH",
                logo = "weth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "0x4300000000000000000000000000000000000004",
                isNativeToken = false,
            )

        val ZERO =
            Coin(
                chain = Chain.Blast,
                ticker = "ZERO",
                logo = "zero",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x357f93E17FdabEcd3fEFc488a2d27dff8065d00f",
                isNativeToken = false,
            )

        val bLOOKS =
            Coin(
                chain = Chain.Blast,
                ticker = "bLOOKS",
                logo = "blooks",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x406F10d635be12ad33D6B133C6DA89180f5B999e",
                isNativeToken = false,
            )

        val all =
            listOf(AI, BAG, BLAST, DACKIE, ETH, JUICE, MIM, OMNI, USDB, WBTC, WETH, ZERO, bLOOKS)
    }

    object BscChain {
        val AAVE =
            Coin(
                chain = Chain.BscChain,
                ticker = "AAVE",
                logo = "aave",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xfb6115445bff7b52feb98650c87f44907e58f802",
                isNativeToken = false,
            )

        val BNB =
            Coin(
                chain = Chain.BscChain,
                ticker = "BNB",
                logo = "bsc",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "binancecoin",
                contractAddress = "",
                isNativeToken = true,
            )

        val COMP =
            Coin(
                chain = Chain.BscChain,
                ticker = "COMP",
                logo = "comp",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x52ce071bd9b1c4b00a0b92d298c512478cad67e8",
                isNativeToken = false,
            )

        val DAI =
            Coin(
                chain = Chain.BscChain,
                ticker = "DAI",
                logo = "dai",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x1af3f329e8be154074d8769d1ffa4ee058b1dbc3",
                isNativeToken = false,
            )

        val ETH =
            Coin(
                chain = Chain.BscChain,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x2170ed0880ac9a755fd29b2688956bd959f933f8",
                isNativeToken = false,
            )

        val KNC =
            Coin(
                chain = Chain.BscChain,
                ticker = "KNC",
                logo = "knc",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xfe56d5892bdffc7bf58f2e84be1b2c32d21c308b",
                isNativeToken = false,
            )

        val PEPE =
            Coin(
                chain = Chain.BscChain,
                ticker = "PEPE",
                logo = "pepe",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x25d887ce7a35172c62febfd67a1856f20faebb00",
                isNativeToken = false,
            )

        val SUSHI =
            Coin(
                chain = Chain.BscChain,
                ticker = "SUSHI",
                logo = "sushi",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x947950bcc74888a40ffa2593c5798f11fc9124c4",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.BscChain,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d",
                isNativeToken = false,
            )

        val USDT =
            Coin(
                chain = Chain.BscChain,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "tether",
                contractAddress = "0x55d398326f99059fF775485246999027B3197955",
                isNativeToken = false,
            )

        val all = listOf(AAVE, BNB, COMP, DAI, ETH, KNC, PEPE, SUSHI, USDC, USDT)
    }

    object CronosChain {
        val CRO =
            Coin(
                chain = Chain.CronosChain,
                ticker = "CRO",
                logo = "cro",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "crypto-com-chain",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(CRO)
    }

    object Dash {
        val DASH =
            Coin(
                chain = Chain.Dash,
                ticker = "DASH",
                logo = "dash",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "dash",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(DASH)
    }

    object Dogecoin {
        val DOGE =
            Coin(
                chain = Chain.Dogecoin,
                ticker = "DOGE",
                logo = "doge",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "dogecoin",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(DOGE)
    }

    object Dydx {
        val DYDX =
            Coin(
                chain = Chain.Dydx,
                ticker = "DYDX",
                logo = "dydx",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "dydx-chain",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(DYDX)
    }

    object Ethereum {
        val AAVE =
            Coin(
                chain = Chain.Ethereum,
                ticker = "AAVE",
                logo = "aave",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "aave",
                contractAddress = "0x7fc66500c84a76ad7e9c93437bfc5ac33e2ddae9",
                isNativeToken = false,
            )

        val BAL =
            Coin(
                chain = Chain.Ethereum,
                ticker = "BAL",
                logo = "bal",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "balancer",
                contractAddress = "0xba100000625a3754423978a60c9317c58a424e3d",
                isNativeToken = false,
            )

        val BAT =
            Coin(
                chain = Chain.Ethereum,
                ticker = "BAT",
                logo = "bat",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "basic-attention-token",
                contractAddress = "0x0d8775f648430679a709e98d2b0cb6250d2887ef",
                isNativeToken = false,
            )

        val COMP =
            Coin(
                chain = Chain.Ethereum,
                ticker = "COMP",
                logo = "comp",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "compound-governance-token",
                contractAddress = "0xc00e94cb662c3520282e6f5717214004a7f26888",
                isNativeToken = false,
            )

        val DAI =
            Coin(
                chain = Chain.Ethereum,
                ticker = "DAI",
                logo = "dai",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "dai",
                contractAddress = "0x6b175474e89094c44da98b954eedeac495271d0f",
                isNativeToken = false,
            )

        val ETH =
            Coin(
                chain = Chain.Ethereum,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )

        val FLIP =
            Coin(
                chain = Chain.Ethereum,
                ticker = "FLIP",
                logo = "flip",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "chainflip",
                contractAddress = "0x826180541412d574cf1336d22c0c0a287822678a",
                isNativeToken = false,
            )

        val FOX =
            Coin(
                chain = Chain.Ethereum,
                ticker = "FOX",
                logo = "fox",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "shapeshift-fox-token",
                contractAddress = "0xc770eefad204b5180df6a14ee197d99d808ee52d",
                isNativeToken = false,
            )

        val GRT =
            Coin(
                chain = Chain.Ethereum,
                ticker = "GRT",
                logo = "grt",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "the-graph",
                contractAddress = "0xc944e90c64b2c07662a292be6244bdf05cda44a7",
                isNativeToken = false,
            )

        val KNC =
            Coin(
                chain = Chain.Ethereum,
                ticker = "KNC",
                logo = "knc",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "kyber-network-crystal",
                contractAddress = "0xdefa4e8a7bcba345f687a2f1456f5edd9ce97202",
                isNativeToken = false,
            )

        val LINK =
            Coin(
                chain = Chain.Ethereum,
                ticker = "LINK",
                logo = "link",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "chainlink",
                contractAddress = "0x514910771af9ca656af840dff83e8264ecf986ca",
                isNativeToken = false,
            )

        val MATIC =
            Coin(
                chain = Chain.Ethereum,
                ticker = "MATIC",
                logo = "matic",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "polygon-ecosystem-token",
                contractAddress = "0x7d1afa7b718fb893db30a3abc0cfc608aacfebb0",
                isNativeToken = false,
            )

        val MKR =
            Coin(
                chain = Chain.Ethereum,
                ticker = "MKR",
                logo = "mkr",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "maker",
                contractAddress = "0x9f8f72aa9304c8b593d555f12ef6589cc3a579a2",
                isNativeToken = false,
            )

        val PEPE =
            Coin(
                chain = Chain.Ethereum,
                ticker = "PEPE",
                logo = "pepe",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "pepe",
                contractAddress = "0x6982508145454ce325ddbe47a25d4ec3d2311933",
                isNativeToken = false,
            )

        val POL =
            Coin(
                chain = Chain.Ethereum,
                ticker = "POL",
                logo = "pol",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "polygon-ecosystem-token",
                contractAddress = "0x7d1afa7b718fb893db30a3abc0cfc608aacfebb0",
                isNativeToken = false,
            )

        val SNX =
            Coin(
                chain = Chain.Ethereum,
                ticker = "SNX",
                logo = "snx",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "havven",
                contractAddress = "0xc011a73ee8576fb46f5e1c5751ca3b9fe0af2a6f",
                isNativeToken = false,
            )

        val SUSHI =
            Coin(
                chain = Chain.Ethereum,
                ticker = "SUSHI",
                logo = "sushi",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "sushi",
                contractAddress = "0x6b3595068778dd592e39a122f4f5a5cf09c90fe2",
                isNativeToken = false,
            )

        val TGT =
            Coin(
                chain = Chain.Ethereum,
                ticker = "TGT",
                logo = "tgt",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "thorwallet",
                contractAddress = "0x108a850856Db3f85d0269a2693D896B394C80325",
                isNativeToken = false,
            )

        val UNI =
            Coin(
                chain = Chain.Ethereum,
                ticker = "UNI",
                logo = "uni",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "uniswap",
                contractAddress = "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Ethereum,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                isNativeToken = false,
            )

        val USDT =
            Coin(
                chain = Chain.Ethereum,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tether",
                contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                isNativeToken = false,
            )

        val VULT =
            Coin(
                chain = Chain.Ethereum,
                ticker = "VULT",
                logo = "vulti",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "vultisig",
                contractAddress = "0xb788144DF611029C60b859DF47e79B7726C4DEBa",
                isNativeToken = false,
            )

        val WBTC =
            Coin(
                chain = Chain.Ethereum,
                ticker = "WBTC",
                logo = "wbtc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "wrapped-bitcoin",
                contractAddress = "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599",
                isNativeToken = false,
            )

        val WETH =
            Coin(
                chain = Chain.Ethereum,
                ticker = "WETH",
                logo = "weth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "weth",
                contractAddress = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                isNativeToken = false,
            )

        val YFI =
            Coin(
                chain = Chain.Ethereum,
                ticker = "YFI",
                logo = "yfi",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "yearn-finance",
                contractAddress = "0x0bc529c00c6401aef6d220be8c6ea1667f6ad93e",
                isNativeToken = false,
            )

        val USDS =
            Coin(
                chain = Chain.Ethereum,
                ticker = "USDS",
                logo = "usds",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "usds",
                contractAddress = "0xdC035D45d973E3EC169d2276DDab16f1e407384F",
                isNativeToken = false,
            )

        val all =
            listOf(
                AAVE,
                BAL,
                BAT,
                COMP,
                DAI,
                ETH,
                FLIP,
                FOX,
                GRT,
                KNC,
                LINK,
                MATIC,
                MKR,
                PEPE,
                POL,
                SNX,
                SUSHI,
                TGT,
                UNI,
                USDC,
                USDS,
                USDT,
                VULT,
                WBTC,
                WETH,
                YFI,
            )
    }

    object GaiaChain {
        val ATOM =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "ATOM",
                logo = "atom",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "cosmos",
                contractAddress = "",
                isNativeToken = true,
            )

        val FUZN =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "FUZN",
                logo = "fuzn",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "fuzion",
                contractAddress =
                    "ibc/6BBBB4B63C51648E9B8567F34505A9D5D8BAAC4C31D768971998BE8C18431C26",
                isNativeToken = false,
            )

        val KUJI =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "KUJI",
                logo = "kuji",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "kujira",
                contractAddress =
                    "ibc/4CC44260793F84006656DD868E017578F827A492978161DA31D7572BCB3F4289",
                isNativeToken = false,
            )

        val LVN =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "LVN",
                logo = "levana",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "levana-protocol",
                contractAddress =
                    "ibc/6C95083ADD352D5D47FB4BA427015796E5FEF17A829463AD05ECD392EB38D889",
                isNativeToken = false,
            )

        val NAMI =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "NAMI",
                logo = "nami",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "nami-protocol",
                contractAddress =
                    "ibc/4622E82B845FFC6AA8B45C1EB2F507133A9E876A5FEA1BA64585D5F564405453",
                isNativeToken = false,
            )

        val NSTK =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "NSTK",
                logo = "nstk",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "unstake-fi",
                contractAddress =
                    "ibc/0B99C4EFF1BD05E56DEDEE1D88286DB79680C893724E0E7573BC369D79B5DDF3",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress =
                    "ibc/F663521BF1836B00F5F177680F74BFB9A8B5654A694D0D2BC249E03CF2509013",
                isNativeToken = false,
            )

        val USK =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "USK",
                logo = "usk",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usk",
                contractAddress =
                    "ibc/A47E814B0E8AE12D044637BCB4576FCA675EF66300864873FA712E1B28492B78",
                isNativeToken = false,
            )

        val WINK =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "WINK",
                logo = "wink",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "winkhub",
                contractAddress =
                    "ibc/4363FD2EF60A7090E405B79A6C4337C5E9447062972028F5A99FB041B9571942",
                isNativeToken = false,
            )

        val rKUJI =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "rKUJI",
                logo = "rkuji",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "kujira",
                contractAddress =
                    "ibc/50A69DC508ACCADE2DAC4B8B09AA6D9C9062FCBFA72BB4C6334367DECD972B06",
                isNativeToken = false,
            )

        val all = listOf(ATOM, FUZN, KUJI, LVN, NAMI, NSTK, USDC, USK, WINK, rKUJI)
    }

    object Kujira {
        val ASTRO =
            Coin(
                chain = Chain.Kujira,
                ticker = "ASTRO",
                logo = "terra-astroport",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "astroport-fi",
                contractAddress =
                    "ibc/640E1C3E28FD45F611971DF891AE3DC90C825DF759DF8FAA8F33F7F72B35AD56",
                isNativeToken = false,
            )

        val FUZN =
            Coin(
                chain = Chain.Kujira,
                ticker = "FUZN",
                logo = "fuzion",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "fuzion",
                contractAddress = "factory/kujira1sc6a0347cc5q3k890jj0pf3ylx2s38rh4sza4t/ufuzn",
                isNativeToken = false,
            )

        val KUJI =
            Coin(
                chain = Chain.Kujira,
                ticker = "KUJI",
                logo = "kuji",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "kujira",
                contractAddress = "",
                isNativeToken = true,
            )

        val LUNC =
            Coin(
                chain = Chain.Kujira,
                ticker = "LUNC",
                logo = "lunc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "terra-luna",
                contractAddress =
                    "ibc/119334C55720942481F458C9C462F5C0CD1F1E7EEAC4679D674AA67221916AEA",
                isNativeToken = false,
            )

        val MNTA =
            Coin(
                chain = Chain.Kujira,
                ticker = "MNTA",
                logo = "mnta",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "mantadao",
                contractAddress = "factory/kujira1643jxg8wasy5cfcn7xm8rd742yeazcksqlg4d7/umnta",
                isNativeToken = false,
            )

        val NAMI =
            Coin(
                chain = Chain.Kujira,
                ticker = "NAMI",
                logo = "nami",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "nami-protocol",
                contractAddress =
                    "factory/kujira13x2l25mpkhwnwcwdzzd34cr8fyht9jlj7xu9g4uffe36g3fmln8qkvm3qn/unami",
                isNativeToken = false,
            )

        val NSTK =
            Coin(
                chain = Chain.Kujira,
                ticker = "NSTK",
                logo = "nstk",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "unstake-fi",
                contractAddress = "factory/kujira1aaudpfr9y23lt9d45hrmskphpdfaq9ajxd3ukh/unstk",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Kujira,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress =
                    "ibc/FE98AAD68F02F03565E9FA39A5E627946699B2B07115889ED812D8BA639576A9",
                isNativeToken = false,
            )

        val USK =
            Coin(
                chain = Chain.Kujira,
                ticker = "USK",
                logo = "usk",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usk",
                contractAddress =
                    "factory/kujira1qk00h5atutpsv900x202pxx42npjr9thg58dnqpa72f2p7m2luase444a7/uusk",
                isNativeToken = false,
            )

        val WINK =
            Coin(
                chain = Chain.Kujira,
                ticker = "WINK",
                logo = "wink",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "winkhub",
                contractAddress = "factory/kujira12cjjeytrqcj25uv349thltcygnp9k0kukpct0e/uwink",
                isNativeToken = false,
            )

        val rKUJI =
            Coin(
                chain = Chain.Kujira,
                ticker = "rKUJI",
                logo = "rkuji",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "kujira",
                contractAddress = "factory/kujira1tsekaqv9vmem0zwskmf90gpf0twl6k57e8vdnq/urkuji",
                isNativeToken = false,
            )

        val LVN =
            Coin(
                chain = Chain.Kujira,
                ticker = "LVN",
                logo = "levana",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "levana-protocol",
                contractAddress =
                    "ibc/B64A07C006C0F5E260A8AD50BD53568F1FD4A0D75B7A9F8765C81BEAFDA62053",
                isNativeToken = false,
            )

        val AUTO =
            Coin(
                chain = Chain.Kujira,
                ticker = "AUTO",
                logo = "auto",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "auto-2",
                contractAddress =
                    "factory/kujira13x2l25mpkhwnwcwdzzd34cr8fyht9jlj7xu9g4uffe36g3fmln8qkvm3qn/uauto",
                isNativeToken = false,
            )

        val all =
            listOf(ASTRO, FUZN, KUJI, LUNC, MNTA, NAMI, NSTK, USDC, USK, WINK, rKUJI, LVN, AUTO)
    }

    object Litecoin {
        val LTC =
            Coin(
                chain = Chain.Litecoin,
                ticker = "LTC",
                logo = "ltc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "litecoin",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(LTC)
    }

    object MayaChain {
        val CACAO =
            Coin(
                chain = Chain.MayaChain,
                ticker = "CACAO",
                logo = "cacao",
                address = "",
                decimal = 10,
                hexPublicKey = "",
                priceProviderID = "cacao",
                contractAddress = "",
                isNativeToken = true,
            )

        val MAYA =
            Coin(
                chain = Chain.MayaChain,
                ticker = "MAYA",
                logo = "maya",
                address = "",
                decimal = 4,
                hexPublicKey = "",
                priceProviderID = "maya",
                contractAddress = "maya",
                isNativeToken = false,
            )

        val AZTEC =
            Coin(
                chain = Chain.MayaChain,
                ticker = "AZTEC",
                logo = "aztec",
                address = "",
                decimal = 4,
                hexPublicKey = "",
                priceProviderID = "aztec",
                contractAddress = "aztec",
                isNativeToken = false,
            )

        val all = listOf(CACAO, MAYA, AZTEC)
    }

    object Noble {
        val USDC =
            Coin(
                chain = Chain.Noble,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(USDC)
    }

    object Optimism {
        val DAI =
            Coin(
                chain = Chain.Optimism,
                ticker = "DAI",
                logo = "dai",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1",
                isNativeToken = false,
            )

        val ETH =
            Coin(
                chain = Chain.Optimism,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )

        val FOX =
            Coin(
                chain = Chain.Optimism,
                ticker = "FOX",
                logo = "fox",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "shapeshift-fox-token",
                contractAddress = "0xf1a0da3367bc7aa04f8d94ba57b862ff37ced174",
                isNativeToken = false,
            )

        val LDO =
            Coin(
                chain = Chain.Optimism,
                ticker = "LDO",
                logo = "ldo",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xFdb794692724153d1488CcdBE0C56c252596735F",
                isNativeToken = false,
            )

        val LINK =
            Coin(
                chain = Chain.Optimism,
                ticker = "LINK",
                logo = "link",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x350a791Bfc2C21F9Ed5d10980Dad2e2638ffa7f6",
                isNativeToken = false,
            )

        val OP =
            Coin(
                chain = Chain.Optimism,
                ticker = "OP",
                logo = "optimism",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "optimism",
                contractAddress = "0x4200000000000000000000000000000000000042",
                isNativeToken = false,
            )

        val PYTH =
            Coin(
                chain = Chain.Optimism,
                ticker = "PYTH",
                logo = "pyth",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "pyth-network",
                contractAddress = "0x99C59ACeBFEF3BBFB7129DC90D1a11DB0E91187f",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Optimism,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x0b2C639c533813f4Aa9D7837CAf62653d097Ff85",
                isNativeToken = false,
            )

        val USDC_e =
            Coin(
                chain = Chain.Optimism,
                ticker = "USDC.e",
                logo = "USDC.e",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x7F5c764cBc14f9669B88837ca1490cCa17c31607",
                isNativeToken = false,
            )

        val USDT =
            Coin(
                chain = Chain.Optimism,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x94b008aA00579c1307B0EF2c499aD98a8ce58e58",
                isNativeToken = false,
            )

        val WBTC =
            Coin(
                chain = Chain.Optimism,
                ticker = "WBTC",
                logo = "wbtc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x68f180fcCe6836688e9084f035309E29Bf0A2095",
                isNativeToken = false,
            )

        val ezETH =
            Coin(
                chain = Chain.Optimism,
                ticker = "ezETH",
                logo = "ezeth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x2416092f143378750bb29b79eD961ab195CcEea5",
                isNativeToken = false,
            )

        val all = listOf(DAI, ETH, FOX, LDO, LINK, OP, PYTH, USDC, USDC_e, USDT, WBTC, ezETH)
    }

    object Osmosis {
        val ION =
            Coin(
                chain = Chain.Osmosis,
                ticker = "ION",
                logo = "ion",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "ion",
                contractAddress = "uion",
                isNativeToken = false,
            )

        val LVN =
            Coin(
                chain = Chain.Osmosis,
                ticker = "LVN",
                logo = "levana",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "levana-protocol",
                contractAddress =
                    "factory/osmo1mlng7pz4pnyxtpq0akfwall37czyk9lukaucsrn30ameplhhshtqdvfm5c/ulvn",
                isNativeToken = false,
            )

        val OSMO =
            Coin(
                chain = Chain.Osmosis,
                ticker = "OSMO",
                logo = "osmo",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "osmosis",
                contractAddress = "",
                isNativeToken = true,
            )

        val USDC =
            Coin(
                chain = Chain.Osmosis,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress =
                    "ibc/498A0751C798A0D9A389AA3691123DADA57DAA4FE165D5C75894505B876BA6E4",
                isNativeToken = false,
            )

        val USDC_eth_axl =
            Coin(
                chain = Chain.Osmosis,
                ticker = "USDC.eth.axl",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress =
                    "ibc/D189335C6E4A68B513C10AB227BF1C1D38C746766278BA3EEB4FB14124F1D858",
                isNativeToken = false,
            )

        val allBTC =
            Coin(
                chain = Chain.Osmosis,
                ticker = "allBTC",
                logo = "btc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "osmosis-allbtc",
                contractAddress =
                    "factory/osmo1z6r6qdknhgsc0zeracktgpcxf43j6sekq07nw8sxduc9lg0qjjlqfu25e3/alloyed/allBTC",
                isNativeToken = false,
            )

        val all = listOf(ION, LVN, OSMO, USDC, USDC_eth_axl, allBTC)
    }

    object Polkadot {
        val DOT =
            Coin(
                chain = Chain.Polkadot,
                ticker = "DOT",
                logo = "dot",
                address = "",
                decimal = 10,
                hexPublicKey = "",
                priceProviderID = "polkadot",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(DOT)
    }

    object Bittensor {
        val TAO =
            Coin(
                chain = Chain.Bittensor,
                ticker = "TAO",
                logo = "bittensor",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "bittensor",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(TAO)
    }

    object Polygon {
        val AVAX =
            Coin(
                chain = Chain.Polygon,
                ticker = "AVAX",
                logo = "avax",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x2C89bbc92BD86F8075d1DEcc58C7F4E0107f286b",
                isNativeToken = false,
            )

        val BNB =
            Coin(
                chain = Chain.Polygon,
                ticker = "BNB",
                logo = "bsc",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x3BA4c387f786bFEE076A58914F5Bd38d668B42c3",
                isNativeToken = false,
            )

        val BUSD =
            Coin(
                chain = Chain.Polygon,
                ticker = "BUSD",
                logo = "busd",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xdAb529f40E671A1D4bF91361c21bf9f0C9712ab7",
                isNativeToken = false,
            )

        val FOX =
            Coin(
                chain = Chain.Polygon,
                ticker = "FOX",
                logo = "fox",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "shapeshift-fox-token",
                contractAddress = "0x65a05db8322701724c197af82c9cae41195b0aa8",
                isNativeToken = false,
            )

        val LINK =
            Coin(
                chain = Chain.Polygon,
                ticker = "LINK",
                logo = "link",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xb0897686c545045aFc77CF20eC7A532E3120E0F1",
                isNativeToken = false,
            )

        val POL =
            Coin(
                chain = Chain.Polygon,
                ticker = "POL",
                logo = "matic",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "polygon-ecosystem-token",
                contractAddress = "",
                isNativeToken = true,
            )

        val SHIB =
            Coin(
                chain = Chain.Polygon,
                ticker = "SHIB",
                logo = "shib",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x6f8a06447Ff6FcF75d803135a7de15CE88C1d4ec",
                isNativeToken = false,
            )

        val SOL =
            Coin(
                chain = Chain.Polygon,
                ticker = "SOL",
                logo = "sol",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xd93f7E271cB87c23AaA73edC008A79646d1F9912",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Polygon,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359",
                isNativeToken = false,
            )

        val USDC_e =
            Coin(
                chain = Chain.Polygon,
                ticker = "USDC.e",
                logo = "USDC.e",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174",
                isNativeToken = false,
            )

        val USDT =
            Coin(
                chain = Chain.Polygon,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xc2132D05D31c914a87C6611C10748AEb04B58e8F",
                isNativeToken = false,
            )

        val WBTC =
            Coin(
                chain = Chain.Polygon,
                ticker = "WBTC",
                logo = "wbtc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x1BFD67037B42Cf73acF2047067bd4F2C47D9BfD6",
                isNativeToken = false,
            )

        val WETH =
            Coin(
                chain = Chain.Polygon,
                ticker = "WETH",
                logo = "weth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "0x7ceB23fD6bC0adD59E62ac25578270cFf1b9f619",
                isNativeToken = false,
            )

        val all = listOf(AVAX, BNB, BUSD, FOX, LINK, POL, SHIB, SOL, USDC, USDC_e, USDT, WBTC, WETH)
    }

    object Ripple {
        val XRP =
            Coin(
                chain = Chain.Ripple,
                ticker = "XRP",
                logo = "xrp",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "ripple",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(XRP)
    }

    object Solana {
        val JUP =
            Coin(
                chain = Chain.Solana,
                ticker = "JUP",
                logo = "jupiter",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "jupiter-exchange-solana",
                contractAddress = "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN",
                isNativeToken = false,
            )

        val KWEEN =
            Coin(
                chain = Chain.Solana,
                ticker = "KWEEN",
                logo = "kween",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "kween",
                contractAddress = "DEf93bSt8dx58gDFCcz4CwbjYZzjwaRBYAciJYLfdCA9",
                isNativeToken = false,
            )

        val PYTH =
            Coin(
                chain = Chain.Solana,
                ticker = "PYTH",
                logo = "pyth",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "pyth-network",
                contractAddress = "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3",
                isNativeToken = false,
            )

        val RAY =
            Coin(
                chain = Chain.Solana,
                ticker = "RAY",
                logo = "raydium-ray-seeklogo-2",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "raydium",
                contractAddress = "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R",
                isNativeToken = false,
            )

        val SOL =
            Coin(
                chain = Chain.Solana,
                ticker = "SOL",
                logo = "solana",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "solana",
                contractAddress = "",
                isNativeToken = true,
            )

        val USDC =
            Coin(
                chain = Chain.Solana,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                isNativeToken = false,
            )

        val USDT =
            Coin(
                chain = Chain.Solana,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tether",
                contractAddress = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
                isNativeToken = false,
            )

        val WIF =
            Coin(
                chain = Chain.Solana,
                ticker = "WIF",
                logo = "dogwifhat-wif-logo",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "dogwifcoin",
                contractAddress = "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm",
                isNativeToken = false,
            )

        val USDS =
            Coin(
                chain = Chain.Solana,
                ticker = "USDS",
                logo = "usds",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usds",
                contractAddress = "USDSwr9ApdHk5bvJKMjzff41FfuX8bSxdKcR81vTwcA",
                isNativeToken = false,
            )

        val all = listOf(JUP, KWEEN, PYTH, RAY, SOL, USDC, USDS, USDT, WIF)
    }

    object Sei {
        val Sei =
            Coin(
                chain = Chain.Sei,
                ticker = "SEI",
                logo = "sei",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "sei-network",
                contractAddress = "",
                isNativeToken = true,
            )
        val all = listOf(Sei)
    }

    object Robinhood {
        val ETH =
            Coin(
                chain = Chain.Robinhood,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )
        val USDG =
            Coin(
                chain = Chain.Robinhood,
                ticker = "USDG",
                logo = "usdg",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "global-dollar",
                contractAddress = "0x5fc5360D0400a0Fd4f2af552ADD042D716F1d168",
                isNativeToken = false,
            )
        val USDe =
            Coin(
                chain = Chain.Robinhood,
                ticker = "USDe",
                logo = "usde",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethena-usde",
                contractAddress = "0x5d3a1Ff2b6BAb83b63cd9AD0787074081a52ef34",
                isNativeToken = false,
            )
        val WETH =
            Coin(
                chain = Chain.Robinhood,
                ticker = "WETH",
                logo = "weth",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "weth",
                contractAddress = "0x0Bd7D308f8E1639FAb988df18A8011f41EAcAD73",
                isNativeToken = false,
            )
        val LINK =
            Coin(
                chain = Chain.Robinhood,
                ticker = "LINK",
                logo = "link",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "chainlink",
                contractAddress = "0x492641F648a4986844848E0beFE66D14817bCE34",
                isNativeToken = false,
            )
        val STOCK_TOKENS = listOf(
            Coin(
                chain = Chain.Robinhood,
                ticker = "AAOI",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x521cf887e6531c6f667b5bc4d896e5d9bfe8eb2e.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x521Cf887E6531c6F667b5BC4D896E5d9bfE8EB2E",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "AAPL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xaf3d76f1834a1d425780943c99ea8a608f8a93f9.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xaF3D76f1834A1d425780943C99Ea8A608f8a93f9",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "AMAT",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x36046893810a7e7fce501229d57dc3fc8c8716d0.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x36046893810a7E7fCE501229d57dc3FC8c8716d0",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "AMD",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x86923f96303d656e4aa86d9d42d1e57ad2023fdc.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x86923f96303D656E4aa86D9d42D1e57ad2023fdC",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "AMZN",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x12f190a9f9d7d37a250758b26824b97ce941bf54.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x12f190a9F9d7D37a250758b26824B97CE941bF54",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "APLD",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xb8dbf92f9741c9ac1c32115e78581f23509916fd.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xb8DBf92F9741c9ac1c32115E78581f23509916FD",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "ASML",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x47f93d52cbec7c6d2cfc080e154002370a60daea.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x47F93d52cBeC7C6D2CfC080e154002370a60dAEA",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "ASTS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x1af6446f07eb1d97c546afc8c9544cbdf3ad5137.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x1AF6446f07eb1d97c546AFC8c9544cBDF3AD5137",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "AVGO",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x156e175dd063a8ce274c50654ef40e0032b3fbcf.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x156E175DD063a8cE274C50654eF40e0032b3fbcF",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "BA",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x4d21483a44bf67a86b77e3da301411880797d452.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x4D21483a44Bf67a86b77E3dA301411880797D452",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "BABA",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xad25ac6c84d497db898fa1e8387bf6af3532a1c4.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xad25Ac6C84D497db898fa1E8387bf6Af3532a1c4",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "BE",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x822cc93ffd030293e9842c30bbd678f530701867.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x822CC93fFD030293E9842c30BBD678F530701867",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "CBRS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x5c90450bbb4273d7b2f17cf6917aeb237a569679.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x5c90450Bbb4273D7b2f17CF6917AEB237A569679",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "CCL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x9651342cea770ae9a2969ba2a52611523146aef9.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x9651342CeA770aE9a2969Ba2A52611523146aef9",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "CELH",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x8cf07c5a878945185d327aaa6e33faa95f95e7bf.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x8cF07C5A878945185d327aAa6e33FAa95F95e7bF",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "CLSK",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xcbb95bbf36099d34da091dc6fa6f49efa257cee3.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xcBB95BBF36099d34dA091dc6Fa6F49EfA257Cee3",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "COIN",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x6330d8c3178a418788df01a47479c0ce7ccf450b.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x6330D8C3178a418788dF01a47479c0ce7CCF450b",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "COST",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x4ea005168d7f09a7a0ba9d1def21a479950e44c2.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x4EA005168D7F09a7A0Ba9D1DEf21a479950E44C2",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "CRCL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xdf0992e440dd0be65bd8439b609d6d4366bf1cb5.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xdF0992E440dD0be65BD8439b609d6D4366bf1CB5",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "CRWD",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xea72ecca2d0f6bfa1394dbbcff85b52cd4233931.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xea72Ecca2d0f6bFA1394DBBCff85b52CD4233931",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "CRWV",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x5f10a1c971b69e47e059e1dc91901b59b3fb49c3.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x5f10A1C971B69e47e059e1dC91901B59b3fB49C3",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "DDOG",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x27c99fbde9d0d2aa4f4bfb4943f237843ddf6958.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x27c99fBde9D0d2AA4f4Bfb4943f237843DdF6958",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "DELL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x941ae714ec6d8130c7b75d67160ca08f1e7d11dd.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x941AE714EC6D8130c7B75d67160Ca08f1e7d11Dd",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "ELF",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x39ec44bee4f6a116c6f9b8de566848a985c53c60.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x39EC44Bee4F6A116c6F9B8De566848a985C53C60",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "EWY",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x7f0abef0c07280f82c6a08ead09ded6bae2c13fc.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x7f0aBeF0C07280F82c6a08ead09dEd6BAE2C13Fc",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "F",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x25c288e6d899b9bc30160965ad9644c67e73be0c.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x25C288E6D899b9BC30160965aD9644c67e73bE0C",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "FLNC",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x282e87451e10fa6679bc7d76c69be44cd3fc777c.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x282e87451E10fA6679BC7D76C69BE44cD3fC777C",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "FUTU",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xeb30663bdff0622ef4e4e5cbb4e975f19f33f51d.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xeB30663bDFf0622Ef4e4E5cBb4E975F19f33f51D",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "GLW",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x7c04e6a3368f2a1de3874f0e80d2e0a1a9915da6.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x7c04E6A3368F2A1DE3874f0e80d2e0A1a9915da6",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "GME",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x1b0e319c6a659f002271b69db8a7df2f911c153e.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x1b0E319c6A659F002271B69dB8A7df2F911c153E",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "GOOGL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x2e0847e8910a9732eb3fb1bb4b70a580adad4fe3.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x2e0847E8910a9732eB3fb1bb4b70a580ADAD4FE3",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "INOD",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xf1953dab6fad537488d5a022361ffaa8b4c95ec6.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xf1953DAB6FaD537488d5A022361FfAa8B4c95eC6",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "INTC",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xc72b96e0e48ecd4dc75e1e45396e26300bc39681.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xc72b96e0E48ecd4DC75E1e45396e26300BC39681",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "INTU",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x56d23bee5f41a7120170b0c603dae30128e460e9.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x56d23beE5f41A7120170b0c603Dae30128e460e9",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "IONQ",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x558378e000d634a36593e338ebacdd6207640efe.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x558378E000D634A36593E338eBacdd6207640EfE",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "IREN",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xf0ab0c93be6f41369d302e55db1a96b3c430212d.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xF0AB0c93bE6F41369d302e55db1A96b3c430212D",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "LITE",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x8ef20885f94e3d9bc7eb3080279188bd5ed7c08c.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x8eF20885F94e3D9bc7eB3080279188Bd5ED7c08C",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "LLY",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x8005d266423c7ea827372c9c864491e5786600ea.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x8005d266423c7ea827372c9c864491e5786600ea",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "LULU",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x4e62068525ab11fe768e29dfd00ef909b9803016.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x4e62068525Ab11FE768e29dfD00ef909B9803016",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "LUNR",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xa5d4968421ba94814be3b136b15cf422101ac1a3.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xa5D4968421bA94814Be3B136b15cf422101aC1a3",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "MDB",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xddf2266b79abf0b48898959b0ed6e6adf512be74.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xDdf2266b79abf0B48898959B0ed6E6adf512be74",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "META",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xc0d6457c16cc70d6790dd43521c899c87ce02f35.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xc0D6457C16Cc70d6790Dd43521C899C87ce02f35",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "MRVL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x62fd0668e10d8b72339be2dcf7643001688ff13b.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x62fd0668e10D8B72339BE2DCF7643001688ff13B",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "MSFT",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xe93237c50d904957cf27e7b1133b510c669c2e74.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xe93237C50D904957Cf27E7B1133b510C669c2e74",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "MSTR",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xec262a75e413fafd0df80480274532c79d42da09.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xec262a75e413fAfD0dF80480274532C79D42da09",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "MU",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xff080c8ce2e5feadaca0da81314ae59d232d4afd.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xfF080c8ce2E5feadaCa0Da81314Ae59D232d4afD",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "MXL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x48961813349333209994750ffa89b3c5c22ec969.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x48961813349333209994750ffA89b3c5C22eC969",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "NBIS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x9d9c6684f596f66a64c030b93a886d51fd4d7931.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x9D9c6684F596F66a64C030B93A886D51Fd4D7931",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "NFLX",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xe0444ef8bf4ed74f74fd73686e2ddf4c1c5591e8.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xE0444EF8BF4eD74f74FD73686e2ddF4C1c5591E8",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "NNE",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xbef75684c43c4ea7bd18dd532a2244674ee8b926.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xBEF75684C43c4ea7BD18Dd532a2244674Ee8b926",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "NOW",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x0c3260af4b8f13a69c4c2dfb84fd667890cdfa14.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x0C3260aF4B8f13a69c4c2dFb84fD667890CDFa14",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "NU",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x408c14038a04f7bd235329e26d2bf569ee20e250.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x408c14038a04f7bD235329E26d2bf569ee20e250",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "NVDA",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xd0601ce157db5bdc3162bbac2a2c8af5320d9eec.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xd0601CE157Db5bdC3162BbaC2a2C8aF5320D9EEC",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "NVTS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xbe6702d7b70315376dc48a3293f24f0982f86386.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xbE6702d7b70315376dC48a3293f24f0982F86386",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "ORCL",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xb0992820e760d836549ba69bc7598b4af75dee03.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xb0992820E760d836549ba69BC7598b4af75dEE03",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "P",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x1cdad396db64bda184d5182a97dd9b3c62100b7d.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x1Cdad396DB64BDa184d5182A97Dd9B3C62100b7D",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "PENG",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x9b23573b156b52565012f5ce02cdf60afbaa70be.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x9b23573b156B52565012F5cE02CDF60AFBaa70Be",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "PLTR",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x894e1ec2d74ffe5aef8dc8a9e84686accb964f2a.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x894E1EC2D74FFE5AEF8Dc8A9e84686acCB964F2A",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "POET",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xcf6b2d875361be807eafa57458c80f28521f9333.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xcf6B2D875361be807EAfa57458c80f28521F9333",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "PR",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x4189f0c66ebbb0bfef1c31f763131361ef32f77c.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x4189F0c66EBBB0bfeF1C31f763131361EF32f77C",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "QBTS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xc583c60aef9dc401da72cec1b404743a93cea1cc.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xC583c60aeF9Dc401Da72cEC1B404743a93cea1Cc",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "QCOM",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x0f17206447090e464c277571124dd2688e48aea9.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x0f17206447090e464C277571124dD2688E48AEA9",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "QQQ",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xd5f3879160bc7c32ebb4dc785f8a4f505888de68.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xD5f3879160bc7c32ebb4dC785F8a4F505888de68",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "QUBT",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x59818904ab4ce163b3ce4ffb64f2d6ca02c434b4.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x59818904ab4cE163b3cE4FfB64f2D6Ca02c434B4",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "RBLX",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xf0c4bf4c582cb3836e98394b1d4e7b7281101be8.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xF0C4BF4C582cb3836e98394b1d4e7B7281101bE8",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "RDDT",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x05b37fb53a299a1b874a619e1c4c404d52c36f4c.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x05b37Fb53A299a1b874A619e1c4C404D52C36F4C",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "RDW",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x92ef19e82bd8ff36661de838d5eae7e5cef0effe.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x92Ef19E82bD8fF36661DE838D5eaE7e5CEF0EfFE",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "RGTI",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x284358abc07f9359f19f4b5b4ac91901be2597ba.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x284358abc07F9359f19f4b5b4aC91901Be2597Ba",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "RIVN",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xb1bf26c1d20ff267a4f93550d1e0d06ac40a114b.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xB1BF26c1D20ff267A4f93550d1E0d06ac40a114B",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "RKLB",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x3b14c39e89d60d627b42a1a4ca45b5bb45fc12e2.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x3b14C39E89D60D627b42a1A4CA45b5bb45Fc12e2",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SATS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x95052ddcd5dc25641657424a8cf04834997e1730.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x95052ddcd5DC25641657424A8Cf04834997E1730",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SGOV",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x92fd66527192e3e61d4ddd13322aa222de86f9b5.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x92FD66527192E3e61d4DDd13322Aa222DE86F9B5",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SHOP",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xf53f66751b1eff985311b693531e3290f600c410.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xF53F66751B1Eff985311b693531E3290F600c410",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SKHY",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x84cab63bc87912e71ad199ff14a0ba45de68fef8.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x84CAb63bc87912E71ad199ff14A0bA45de68FeF8",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SLV",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x411efb0e7f985935daec3d4c3ebaea0d0ad7d89f.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x411eFb0E7f985935DAec3D4C3ebaEa0d0AD7D89f",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SMCI",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xc01aa1fecec0605b13bc84874ff7256c0f5f562a.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xc01aA1fECeC0605b13bc84874ff7256C0f5F562a",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SNDK",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xb90a19ff0af67f7779aff50a882a9cff42446400.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xB90A19fF0Af67f7779afF50A882A9CfF42446400",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SOFI",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x98e75885157c80992a8d41b696d8c9c6fb30a926.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x98E75885157C80992A8D41b696D8c9C6Fb30A926",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SOXX",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x75742c18bc1f1c5c5f448f4c9d9c6f66dafaaa38.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x75742c18BC1f1C5c5f448f4C9D9C6F66dafAAa38",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SPCX",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x4a0e65a3eccec6dbe60ae065f2e7bb85fae35eea.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x4a0E65A3EcceC6dBe60AE065F2e7bb85Fae35eEa",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SPMO",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xad622320e520de39e72d41ef07438c3fd3354875.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xAd622320e520de39e72d41EF07438C3Fd3354875",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "SPY",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x117cc2133c37b721f49de2a7a74833232b3b4c0c.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x117cc2133c37B721F49dE2A7a74833232B3B4C0C",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "TSEM",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x89776d4cd68193597a2fc132cfac1fde36ccea8a.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x89776d4Cd68193597A2fC132cfaC1fDe36CCeA8a",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "TSLA",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x322f0929c4625ed5bad873c95208d54e1c003b2d.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x322F0929c4625eD5bAd873c95208D54E1c003b2d",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "TSM",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x58ffe4a942d3885baa22d7520691f611ef09e7aa.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x58FfE4a942d3885bAa22D7520691F611EF09e7AA",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "TTWO",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x5e81213613b6b86eab4c6c50d718d34359459786.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x5e81213613b6B86EaB4c6c50d718d34359459786",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "UMC",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x0e6e67ba88e7b5d9b67636a215c76779b948de79.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x0E6e67Ba88e7b5d9B67636A215c76779B948dE79",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "UPS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xf23250dac154d05bb671cb0d0ebef3c635c79ce2.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xf23250dac154D05Bb671CB0d0eBEf3c635c79CE2",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "USAR",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xd917b029c761d264c6a312bbbcda868658ef86a6.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xd917B029C761D264c6A312BBbcDA868658eF86a6",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "USO",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xa30fa36db767ad9ed3f7a60fc79526fb4d56d344.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xa30FA36Db767ad9eD3f7a60fC79526fB4d56D344",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "WDAY",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x82da4646242e1d962e96e932269dc644c94a9caa.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x82DA4646242e1D962e96e932269Dc644c94a9CaA",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "XLK",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x15cd20759ce7f3285c29a319de2d1a2e098c6f43.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x15Cd20759CE7F3285c29A319dE2D1A2e098c6f43",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "XNDU",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xa8eb3bccbf2017ee7cbfb652eb51cf2e1b153289.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xA8eB3BCcbf2017eE7CBfb652eB51CF2E1B153289",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "XOM",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0xf9b46d3d1b22199d4d1025a9cedb540a33f1a2d5.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0xf9B46d3D1B22199D4D1025a9cEDB540A33F1a2d5",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "ZM",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x44c4f142009036cf477ed2d09932051843137cf1.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x44c4F142009036cF477eD2d09932051843137CF1",
                isNativeToken = false,
            ),
            Coin(
                chain = Chain.Robinhood,
                ticker = "ZS",
                logo = "https://cdn.robinhood.com/ncw_assets/logos/0x7dc013eb55e436f30d7ed1afe4e36d6e45e3c3f7.png",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "0x7dc013eB55e436f30d7ED1AFE4E36d6e45e3c3f7",
                isNativeToken = false,
            ),
        )
        val all = listOf(ETH, USDG, USDe, WETH, LINK) + STOCK_TOKENS
    }

    object Hyperliquid {
        val HYPE =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "HYPE",
                logo = "hype",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "hyperliquid",
                contractAddress = "",
                isNativeToken = true,
            )

        val kHYPE =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "kHYPE",
                logo = "khype",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "kinetic-staked-hype",
                contractAddress = "0xfD739d4e423301CE9385c1fb8850539D657C296D",
                isNativeToken = false,
            )

        val wstHYPE =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "wstHYPE",
                logo = "wsthype",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "staked-hype-shares",
                contractAddress = "0x94e8396e0869c9F2200760aF0621aFd240E1CF38",
                isNativeToken = false,
            )

        val WHYPE =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "WHYPE",
                logo = "whype",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "wrapped-hype",
                contractAddress = "0x5555555555555555555555555555555555555555",
                isNativeToken = false,
            )

        val UFART =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "UFART",
                logo = "ufart",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "unit-fartcoin",
                contractAddress = "0x3B4575E689DEd21CAAD31d64C4df1f10F3B2CedF",
                isNativeToken = false,
            )

        val USDT0 =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "USDT0",
                logo = "usdt0",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usdt0",
                contractAddress = "0xB8CE59FC3717ada4C02eaDF9682A9e934F625ebb",
                isNativeToken = false,
            )

        val vkHYPE =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "vkHYPE",
                logo = "vkhype",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "kinetiq-earn-vault",
                contractAddress = "0x9BA2EDc44E0A4632EB4723E81d4142353e1bB160",
                isNativeToken = false,
            )

        val UBTC =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "UBTC",
                logo = "ubtc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "unit-bitcoin",
                contractAddress = "0x9FDBdA0A5e284c32744D2f17Ee5c74B284993463",
                isNativeToken = false,
            )

        val vHYPE =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "vHYPE",
                logo = "vhype",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ventuals-vhype",
                contractAddress = "0x8888888FdAAc0E7CF8C6523c8955bF7954c216fa",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Hyperliquid,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "0xb88339CB7199b77E23DB6E890353E22632Ba630f",
                isNativeToken = false,
            )

        val all = listOf(HYPE, kHYPE, wstHYPE, WHYPE, UFART, USDT0, vkHYPE, UBTC, vHYPE, USDC)
    }

    object Sui {
        val ETH =
            Coin(
                chain = Chain.Sui,
                ticker = "ETH",
                logo = "eth",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress =
                    "0xd0e89b2af5e4910726fbcd8b8dd37bb79b29e5f83f7491bca830e94f7f226d29::eth::ETH",
                isNativeToken = false,
            )

        val SUI =
            Coin(
                chain = Chain.Sui,
                ticker = "SUI",
                logo = "sui",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "sui",
                contractAddress = "",
                isNativeToken = true,
            )

        val DEEP =
            Coin(
                chain = Chain.Sui,
                ticker = "DEEP",
                logo = "https://s2.coinmarketcap.com/static/img/coins/64x64/33391.png",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "deep",
                contractAddress =
                    "0xdeeb7a4662eec9f2f3def03fb937a663dddaa2e215b8078a284d026b7946c270::deep::DEEP",
                isNativeToken = false,
            )

        val WAL =
            Coin(
                chain = Chain.Sui,
                ticker = "WAL",
                logo = "https://coin-images.coingecko.com/coins/images/54914/large/WAL_logo.png",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "walrus-2",
                contractAddress =
                    "0x356a26eb9e012a68958082340d4c4116e7f55615cf27affcff209cf0ae544f59::wal::WAL",
                isNativeToken = false,
            )

        val CETUS =
            Coin(
                chain = Chain.Sui,
                ticker = "CETUS",
                logo =
                    "https://raw.githubusercontent.com/cosmostation/chainlist/main/chain/sui/asset/cetus.png",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "cetus-protocol",
                contractAddress =
                    "0x06864a6f921804860930db6ddbe2e16acdf8504495ea7481637a1c8b9a8fe54b::cetus::CETUS",
                isNativeToken = false,
            )

        val NAVX =
            Coin(
                chain = Chain.Sui,
                ticker = "NAVX",
                logo =
                    "https://raw.githubusercontent.com/cosmostation/chainlist/main/chain/sui/asset/navx.png",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "navi",
                contractAddress =
                    "0xa99b8952d4f7d947ea77fe0ecdcc9e5fc0bcab2841d6e2a5aa00c3044e5544b5::navx::NAVX",
                isNativeToken = false,
            )

        val BLUE =
            Coin(
                chain = Chain.Sui,
                ticker = "BLUE",
                logo =
                    "https://coin-images.coingecko.com/coins/images/30883/large/BLUE_200x200.png",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "bluefin",
                contractAddress =
                    "0xe1b45a0e641b9955a20aa0ad1c1f4ad86aad8afb07296d4085e349a50e90bdca::blue::BLUE",
                isNativeToken = false,
            )

        val SEND =
            Coin(
                chain = Chain.Sui,
                ticker = "SEND",
                logo = "https://coin-images.coingecko.com/coins/images/50989/large/SEND.png",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "send-token-2",
                contractAddress =
                    "0xb45fcfcc2cc07ce0702cc2d229621e046c906ef14d9b25e8e4d25f6e8763fef7::send::SEND",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Sui,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress =
                    "0xdba34672e30cb065b1f93e3ab55318768fd6fef66c15942c9f7cb846e2f900e7::usdc::USDC",
                isNativeToken = false,
            )

        val AXOL =
            Coin(
                chain = Chain.Sui,
                ticker = "AXOL",
                logo = "https://coin-images.coingecko.com/coins/images/50412/large/AXOL.png",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "axol",
                contractAddress =
                    "0xf00eb7ab086967a33c04a853ad960e5c6b0955ef5a47d50b376d83856dc1215e::axol::AXOL",
                isNativeToken = false,
            )

        val LOFI =
            Coin(
                chain = Chain.Sui,
                ticker = "LOFI",
                logo = "https://s2.coinmarketcap.com/static/img/coins/64x64/34187.png",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "lofi-2",
                contractAddress =
                    "0xf22da9a24ad027cccb5f2d496cbe91de953d363513db08a3a734d361c7c17503::LOFI::LOFI",
                isNativeToken = false,
            )

        val all = listOf(ETH, SUI, DEEP, WAL, CETUS, NAVX, BLUE, SEND, USDC, AXOL, LOFI)
    }

    object Terra {
        val ASTRO =
            Coin(
                chain = Chain.Terra,
                ticker = "ASTRO",
                logo = "terra-astroport",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "astroport-fi",
                contractAddress =
                    "terra1nsuqsk6kh58ulczatwev87ttq2z6r3pusulg9r24mfj2fvtzd4uq3exn26",
                isNativeToken = false,
            )

        val ASTRO_IBC =
            Coin(
                chain = Chain.Terra,
                ticker = "ASTRO-IBC",
                logo = "terra-astroport",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "astroport-fi",
                contractAddress =
                    "ibc/8D8A7F7253615E5F76CB6252A1E1BD921D5EDB7BBAAF8913FB1C77FF125D9995",
                isNativeToken = false,
            )

        val LUNA =
            Coin(
                chain = Chain.Terra,
                ticker = "LUNA",
                logo = "luna",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "terra-luna-2",
                contractAddress = "",
                isNativeToken = true,
            )

        val TPT =
            Coin(
                chain = Chain.Terra,
                ticker = "TPT",
                logo = "terra-poker-token",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tpt",
                contractAddress =
                    "terra13j2k5rfkg0qhk58vz63cze0uze4hwswlrfnm0fa4rnyggjyfrcnqcrs5z2",
                isNativeToken = false,
            )

        val all = listOf(ASTRO, ASTRO_IBC, LUNA, TPT)
    }

    object TerraClassic {
        val ASTROC =
            Coin(
                chain = Chain.TerraClassic,
                ticker = "ASTROC",
                logo = "terra-astroport",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "astroport",
                contractAddress = "terra1xj49zyqrwpv5k928jwfpfy2ha668nwdgkwlrg3",
                isNativeToken = false,
            )

        val LUNC =
            Coin(
                chain = Chain.TerraClassic,
                ticker = "LUNC",
                logo = "lunc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "terra-luna",
                contractAddress = "",
                isNativeToken = true,
            )

        val USTC =
            Coin(
                chain = Chain.TerraClassic,
                ticker = "USTC",
                logo = "ustc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "terrausd",
                contractAddress = "uusd",
                isNativeToken = false,
            )

        val all = listOf(ASTROC, LUNC, USTC)
    }

    object ThorChain {
        val RUNE =
            Coin(
                chain = Chain.ThorChain,
                ticker = "RUNE",
                logo = "rune",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "thorchain",
                contractAddress = "",
                isNativeToken = true,
            )

        val TCY =
            Coin(
                chain = Chain.ThorChain,
                ticker = "TCY",
                logo = "tcy",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "tcy",
                contractAddress = "tcy",
                isNativeToken = false,
            )

        val RUJI =
            Coin(
                chain = Chain.ThorChain,
                ticker = "RUJI",
                logo = "ruji",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "ruji",
                contractAddress = "x/ruji",
                isNativeToken = false,
            )

        val KUJI =
            Coin(
                chain = Chain.ThorChain,
                ticker = "KUJI",
                logo = "kuji",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "kujira",
                contractAddress = "thor.kuji",
                isNativeToken = false,
            )

        val FUZN =
            Coin(
                chain = Chain.ThorChain,
                ticker = "FUZN",
                logo = "fuzn",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "fuzion",
                contractAddress = "thor.fuzn",
                isNativeToken = false,
            )

        val NSTK =
            Coin(
                chain = Chain.ThorChain,
                ticker = "NSTK",
                logo = "nstk",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "unstake-fi",
                contractAddress = "thor.nstk",
                isNativeToken = false,
            )

        val WINK =
            Coin(
                chain = Chain.ThorChain,
                ticker = "WINK",
                logo = "wink",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "winkhub",
                contractAddress = "thor.wink",
                isNativeToken = false,
            )

        val LVN =
            Coin(
                chain = Chain.ThorChain,
                ticker = "LVN",
                logo = "levana",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "levana-protocol",
                contractAddress = "thor.lvn",
                isNativeToken = false,
            )

        val RKUJI =
            Coin(
                chain = Chain.ThorChain,
                ticker = "RKUJI",
                logo = "rkuji",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "kujira",
                contractAddress = "thor.rkuji",
                isNativeToken = false,
            )

        val sTCY =
            Coin(
                chain = Chain.ThorChain,
                ticker = "sTCY",
                logo = "stcy",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "tcy",
                contractAddress = "x/staking-tcy",
                isNativeToken = false,
            )

        val yRUNE =
            Coin(
                chain = Chain.ThorChain,
                ticker = "yRUNE",
                logo = "yrune",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress =
                    "x/nami-index-nav-thor1mlphkryw5g54yfkrp6xpqzlpv4f8wh6hyw27yyg4z2els8a9gxpqhfhekt-rcpt",
                isNativeToken = false,
            )

        val yTCY =
            Coin(
                chain = Chain.ThorChain,
                ticker = "yTCY",
                logo = "ytcy",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress =
                    "x/nami-index-nav-thor1h0hr0rm3dawkedh44hlrmgvya6plsryehcr46yda2vj0wfwgq5xqrs86px-rcpt",
                isNativeToken = false,
            )

        val bRUNE =
            Coin(
                chain = Chain.ThorChain,
                ticker = "bRUNE",
                logo = "brune",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "x/brune",
                isNativeToken = false,
            )

        val ybRUNE =
            Coin(
                chain = Chain.ThorChain,
                ticker = "ybRUNE",
                logo = "ybrune",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "x/staking-x/brune",
                isNativeToken = false,
            )

        val all =
            listOf(
                RUNE,
                TCY,
                RUJI,
                KUJI,
                FUZN,
                NSTK,
                WINK,
                LVN,
                RKUJI,
                sTCY,
                yRUNE,
                yTCY,
                bRUNE,
                ybRUNE,
            )
    }

    object Ton {
        val TON =
            Coin(
                chain = Chain.Ton,
                ticker = "GRAM",
                logo = "gram",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "the-open-network",
                contractAddress = "",
                isNativeToken = true,
            )

        val USDT =
            Coin(
                chain = Chain.Ton,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tether",
                contractAddress = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs",
                isNativeToken = false,
            )

        val NOT =
            Coin(
                chain = Chain.Ton,
                ticker = "NOT",
                logo = "not",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "notcoin",
                contractAddress = "EQAvlWFDxGF2lXm67y4yzC17wYKD9A0guwPkMs1gOsM__NOT",
                isNativeToken = false,
            )

        val DOGS =
            Coin(
                chain = Chain.Ton,
                ticker = "DOGS",
                logo = "dogs",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "dogs-2",
                contractAddress = "EQCvxJy4eG8hyHBFsZ7eePxrRsUQSFE_jpptRAYBmcG_DOGS",
                isNativeToken = false,
            )

        val CATI =
            Coin(
                chain = Chain.Ton,
                ticker = "CATI",
                logo = "cati",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "catizen",
                contractAddress = "EQD-cvR0Nz6XAyRBvbhz-abTrRC6sI5tvHvvpeQraV9UAAD7",
                isNativeToken = false,
            )

        val HMSTR =
            Coin(
                chain = Chain.Ton,
                ticker = "HMSTR",
                logo = "hmstr",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "hamster-kombat",
                contractAddress = "EQAJ8uWd7EBqsmpSWaRdf_I-8R8-XHwh3gsNKhy-UrdrPcUo",
                isNativeToken = false,
            )

        val STON =
            Coin(
                chain = Chain.Ton,
                ticker = "STON",
                logo = "ston",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "ston-2",
                contractAddress = "EQA2kCVNwVsil2EM2mB0SkXytxCqQjS4mttjDpnXmwG9T6bO",
                isNativeToken = false,
            )

        val STTON =
            Coin(
                chain = Chain.Ton,
                ticker = "stTON",
                logo = "stton",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "bemo-staked-ton",
                contractAddress = "EQDNhy-nxYFgUqzfUzImBEP67JqsyMIcyk2S5_RwNNEYku0k",
                isNativeToken = false,
            )

        val TSTON =
            Coin(
                chain = Chain.Ton,
                ticker = "tsTON",
                logo = "tston",
                address = "",
                decimal = 9,
                hexPublicKey = "",
                priceProviderID = "tonstakers",
                contractAddress = "EQC98_qAmNEptUtPc7W6xdHh_ZHrBUFpw5Ft_IzNU20QAJav",
                isNativeToken = false,
            )

        val all = listOf(TON, USDT, NOT, DOGS, CATI, HMSTR, STON, STTON, TSTON)
    }

    object Tron {
        val TRX =
            Coin(
                chain = Chain.Tron,
                ticker = "TRX",
                logo = "tron",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tron",
                contractAddress = "",
                isNativeToken = true,
            )

        val USDT =
            Coin(
                chain = Chain.Tron,
                ticker = "USDT",
                logo = "usdt",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "tether",
                contractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                isNativeToken = false,
            )

        val USDC =
            Coin(
                chain = Chain.Tron,
                ticker = "USDC",
                logo = "usdc",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "usd-coin",
                contractAddress = "TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8",
                isNativeToken = false,
            )

        val USDD =
            Coin(
                chain = Chain.Tron,
                ticker = "USDD",
                logo = "usdd",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "usdd",
                contractAddress = "TXDk8mbtRbXeYuMNS83CfKPaYYT8XWv9Hz",
                isNativeToken = false,
            )

        val stUSDT =
            Coin(
                chain = Chain.Tron,
                ticker = "stUSDT",
                logo = "stusdt",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "staked-usdt",
                contractAddress = "TThzxNRLrW2Brp9DcTQU8i4Wd9udCWEdZ3",
                isNativeToken = false,
            )

        val all = listOf(TRX, USDT, USDC, USDD, stUSDT)
    }

    object ZkSync {
        val ETH =
            Coin(
                chain = Chain.ZkSync,
                ticker = "ETH",
                logo = "zsync_era",
                address = "",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(ETH)
    }

    object Zcash {
        val ZEC =
            Coin(
                chain = Chain.Zcash,
                ticker = "ZEC",
                logo = "zec",
                decimal = 8,
                address = "",
                hexPublicKey = "",
                priceProviderID = "zcash",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(ZEC)
    }

    object Cardano {
        val ADA =
            Coin(
                chain = Chain.Cardano,
                ticker = "ADA",
                logo = "ada",
                address = "",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "cardano",
                contractAddress = "",
                isNativeToken = true,
            )
        val all = listOf(ADA)
    }

    object Qbtc {
        val QBTC =
            Coin(
                chain = Chain.Qbtc,
                ticker = "QBTC",
                logo = "qbtc",
                address = "",
                decimal = 8,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "",
                isNativeToken = true,
            )

        val all = listOf(QBTC)
    }

    val coins: Map<Chain, List<Coin>> =
        mapOf(
            Chain.Akash to Akash.all,
            Chain.Arbitrum to Arbitrum.all,
            Chain.Mantle to Mantle.all,
            Chain.Avalanche to Avalanche.all,
            Chain.Base to Base.all,
            Chain.Bitcoin to Bitcoin.all,
            Chain.BitcoinCash to BitcoinCash.all,
            Chain.Blast to Blast.all,
            Chain.BscChain to BscChain.all,
            Chain.CronosChain to CronosChain.all,
            Chain.Dash to Dash.all,
            Chain.Dogecoin to Dogecoin.all,
            Chain.Dydx to Dydx.all,
            Chain.Ethereum to Ethereum.all,
            Chain.GaiaChain to GaiaChain.all,
            Chain.Hyperliquid to Hyperliquid.all,
            Chain.Kujira to Kujira.all,
            Chain.Litecoin to Litecoin.all,
            Chain.MayaChain to MayaChain.all,
            Chain.Noble to Noble.all,
            Chain.Optimism to Optimism.all,
            Chain.Osmosis to Osmosis.all,
            Chain.Polkadot to Polkadot.all,
            Chain.Bittensor to Bittensor.all,
            Chain.Polygon to Polygon.all,
            Chain.Ripple to Ripple.all,
            Chain.Solana to Solana.all,
            Chain.Sui to Sui.all,
            Chain.Terra to Terra.all,
            Chain.TerraClassic to TerraClassic.all,
            Chain.ThorChain to ThorChain.all,
            Chain.Ton to Ton.all,
            Chain.Tron to Tron.all,
            Chain.ZkSync to ZkSync.all,
            Chain.Zcash to Zcash.all,
            Chain.Cardano to Cardano.all,
            Chain.Sei to Sei.all,
            Chain.Robinhood to Robinhood.all,
            Chain.Qbtc to Qbtc.all,
        )

    val all: List<Coin> = coins.values.flatten()
}
