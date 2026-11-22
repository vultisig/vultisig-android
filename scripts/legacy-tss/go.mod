module github.com/vultisig/vultisig-android/legacy-tss-build

go 1.26.0

toolchain go1.26.11

require (
	github.com/vultisig/mobile-tss-lib v0.0.0-20250828220621-7d3bfb721494
	golang.org/x/mobile v0.0.0-20250606033058-a2a15c67f36f
)

require (
	github.com/agl/ed25519 v0.0.0-20200225211852-fd4d107ace12 // indirect
	github.com/bnb-chain/tss-lib/v2 v2.0.2 // indirect
	github.com/btcsuite/btcd v0.24.2 // indirect
	github.com/btcsuite/btcd/btcec/v2 v2.3.2 // indirect
	github.com/btcsuite/btcd/chaincfg/chainhash v1.1.0 // indirect
	github.com/btcsuite/btcutil v1.0.3-0.20201208143702-a53e38424cce // indirect
	github.com/decred/dcrd/dcrec/edwards/v2 v2.0.3 // indirect
	github.com/decred/dcrd/dcrec/secp256k1/v4 v4.2.0 // indirect
	github.com/gogo/protobuf v1.3.3 // indirect
	github.com/hashicorp/errwrap v1.1.0 // indirect
	github.com/hashicorp/go-multierror v1.1.1 // indirect
	github.com/ipfs/go-log v1.0.5 // indirect
	github.com/ipfs/go-log/v2 v2.1.3 // indirect
	github.com/opentracing/opentracing-go v1.2.0 // indirect
	github.com/otiai10/primes v0.0.0-20210501021515-f1b2be525a11 // indirect
	github.com/pkg/errors v0.9.1 // indirect
	go.uber.org/atomic v1.10.0 // indirect
	go.uber.org/multierr v1.10.0 // indirect
	go.uber.org/zap v1.24.0 // indirect
	golang.org/x/crypto v0.59.0 // indirect
	golang.org/x/mod v0.25.0 // indirect
	golang.org/x/sync v0.15.0 // indirect
	golang.org/x/sys v0.50.0 // indirect
	golang.org/x/tools v0.34.0 // indirect
	google.golang.org/protobuf v1.34.2 // indirect
)

replace (
	github.com/agl/ed25519 => github.com/binance-chain/edwards25519 v0.0.0-20200305024217-f36fc4b53d43
	github.com/gogo/protobuf => github.com/regen-network/protobuf v1.3.2-alpha.regen.4
)
