package com.example.mangrove.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** true while the phone has internet; updates live when it connects or disconnects. */
@Composable
fun rememberOnline(): Boolean {
    val ctx = LocalContext.current
    var online by remember { mutableStateOf(isOnline(ctx)) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = isOnline(ctx) }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return online
}