package com.homecontrol.feature.cameras

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/**
 * Best-effort check for whether this phone is currently on the same LAN as
 * a camera at a given IP -- lets [resolveCameraStreamUri] skip straight to
 * the remote address (instead of waiting out a full connection attempt
 * against an address that's obviously unreachable) when it's clearly not,
 * e.g. on mobile data or a different Wi-Fi network entirely.
 *
 * "Same LAN" is approximated as "on Wi-Fi, in the same /24 as the camera's
 * configured IP" -- there's no way to read a camera's actual subnet mask
 * from here, and /24 covers the overwhelming majority of home/office
 * networks this app targets.
 */
class LocalNetworkChecker(private val context: Context) {

    fun isLikelyOnSameNetwork(cameraIp: String): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false

        val myIp = connectivityManager.getLinkProperties(activeNetwork)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address?.hostAddress
            ?: return false

        return sameSlash24Subnet(myIp, cameraIp)
    }

    private fun sameSlash24Subnet(ip1: String, ip2: String): Boolean {
        val a = ip1.split(".")
        val b = ip2.split(".")
        return a.size == 4 && b.size == 4 && a[0] == b[0] && a[1] == b[1] && a[2] == b[2]
    }
}
