const fs = require('fs');

const inputFile = '../commondata/tokens/tokens.json';
const outputFile = '../data/src/main/kotlin/com/vultisig/wallet/data/models/Coins.kt';

// Mapping from lowercase JSON chain name → actual Kotlin Chain name
const chainMap = {
    thorchain: "ThorChain",
    mayachain: "MayaChain",
    arbitrum: "Arbitrum",
    avalanche: "Avalanche",
    base: "Base",
    bscchain: "BscChain",
    cronoschain: "CronosChain",
    bsc: "BscChain",
    blast: "Blast",
    ethereum: "Ethereum",
    optimism: "Optimism",
    polygon: "Polygon",
    zksync: "ZkSync",
    bitcoin: "Bitcoin",
    bitcoincash: "BitcoinCash",
    litecoin: "Litecoin",
    dogecoin: "Dogecoin",
    dash: "Dash",
    gaiachain: "GaiaChain",
    kujira: "Kujira",
    dydx: "Dydx",
    osmosis: "Osmosis",
    terra: "Terra",
    terraclassic: "TerraClassic",
    noble: "Noble",
    akash: "Akash",
    solana: "Solana",
    polkadot: "Polkadot",
    sui: "Sui",
    ton: "Ton",
    ripple: "Ripple",
    tron: "Tron",
};

const data = JSON.parse(fs.readFileSync(inputFile, 'utf8'));

let out = 'package com.vultisig.wallet.data.models\n';
out += '\n';
out += 'object Coins {\n';
out += '    val coins = mapOf<Chain, List<Coin>>(\n';

for (const [chainRaw, tokens] of Object.entries(data)) {
    const chainKey = chainRaw.toLowerCase();
    const chainKotlin = chainMap[chainKey];
    if (!chainKotlin) {
        console.warn(`⚠️ Unknown chain: ${chainRaw}`);
        continue;
    }

    out += `\n\n        Chain.${chainKotlin} to listOf(\n`;
    for (const token of tokens) {
        out += '            Coin(\n';
        out += `                chain = Chain.${chainKotlin},\n`;
        out += `                ticker = "${token.ticker}",\n`;
        out += `                logo = "${token.logo}",\n`;
        out += `                address = "",\n`;
        out += `                decimal = ${token.decimals},\n`;
        out += `                hexPublicKey = "",\n`;
        out += `                priceProviderID = "${token.price_provider_id || ""}",\n`;
        out += `                contractAddress = "${token.contract_address || ""}",\n`;
        out += `                isNativeToken = ${token.is_native_token},\n`;
        out += '            ),\n';
    }
    out += '        ),';
}

out += '\n    )\n';
out += '}\n';

fs.writeFileSync(outputFile, out);
console.log('✅ Coins.kt generated!');