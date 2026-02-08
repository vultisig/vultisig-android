package com.vultisig.wallet.data.networkutils

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

class Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)

        val ipv4Addresses = addresses.filterIsInstance<Inet4Address>()
        val ipv6Addresses = addresses.filter { it !is Inet4Address }

        return if (ipv4Addresses.isNotEmpty()) {
            ipv4Addresses + ipv6Addresses
        } else {
            addresses
        }
    }
}