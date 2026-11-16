package com.nuvio.app.core.network

import okhttp3.Dns
import java.net.Inet6Address
import java.net.InetAddress

/** Prefer IPv6 while retaining every IPv4 address for OkHttp's connection fallback. */
internal class DesktopIPv6FirstDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        delegate.lookup(hostname).sortedBy { if (it is Inet6Address) 0 else 1 }
}
