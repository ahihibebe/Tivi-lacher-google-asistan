package vn.launcher.tv.data

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import vn.launcher.tv.model.NetworkPillState
import vn.launcher.tv.model.SystemBarsState
import java.net.Inet4Address
import java.net.NetworkInterface

/** System hardware/network telemetry readers for TV launcher status widgets and settings. */
object SystemTelemetry {

    fun readSystemBars(context: Context): SystemBarsState? = try {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.totalBytes
        val free = st.availableBytes
        val used = total - free
        val sPct = if (total > 0) ((used * 100) / total).toInt() else 0

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val ramUsed = mi.totalMem - mi.availMem
        val rPct = if (mi.totalMem > 0) ((ramUsed * 100) / mi.totalMem).toInt() else 0

        SystemBarsState(
            enabled = true,
            storageUsedGb = used / 1073741824.0,
            storageTotalGb = total / 1073741824.0,
            storagePercent = sPct,
            ramUsedGb = ramUsed / 1073741824.0,
            ramTotalGb = mi.totalMem / 1073741824.0,
            ramPercent = rPct
        )
    } catch (_: Exception) {
        null
    }

    fun readNetworkPill(context: Context, visible: Boolean, en: Boolean): NetworkPillState {
        var isEth = false
        var isOffline = false
        val label = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val nc = if (net != null) cm.getNetworkCapabilities(net) else null
            when {
                nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    isEth = true
                    "LAN"
                }
                nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> readWifiName(context)
                else -> {
                    isOffline = true
                    if (en) "Offline" else "Chưa kết nối"
                }
            }
        } catch (_: Exception) {
            "Wi-Fi"
        }
        return NetworkPillState(
            visible = visible,
            label = label,
            isEthernet = isEth,
            isOffline = isOffline
        )
    }

    @Suppress("DEPRECATION")
    private fun readWifiName(context: Context): String {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ssid = wm?.connectionInfo?.ssid
            if (!ssid.isNullOrEmpty() && !ssid.contains("unknown ssid") && ssid != "0x") {
                return if (ssid.length > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid.substring(1, ssid.length - 1)
                } else {
                    ssid
                }
            }
        } catch (_: Exception) {
        }
        return "Wi-Fi"
    }

    fun isUsbStorageConnected(context: Context): Boolean {
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null) {
                for (v in sm.storageVolumes) {
                    if (v.isRemovable && Environment.MEDIA_MOUNTED == v.state) return true
                }
            }
        } catch (_: Throwable) {
        }
        try {
            val um = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            if (um != null) {
                for (d in um.deviceList.values) {
                    for (i in 0 until d.interfaceCount) {
                        if (d.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return false
    }

    fun readBluetoothStatus(context: Context, en: Boolean): String = try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val ad = bm?.adapter
        when {
            ad == null -> if (en) "Not available" else "Không có"
            ad.isEnabled -> if (en) "On" else "Đang bật"
            else -> if (en) "Off" else "Đã tắt"
        }
    } catch (_: Throwable) {
        "—"
    }

    fun lanIp(): String? {
        var fallback: String? = null
        try {
            val nis = NetworkInterface.getNetworkInterfaces() ?: return null
            while (nis.hasMoreElements()) {
                val ni = nis.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val n = ni.name
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        if (n.startsWith("wlan") || n.startsWith("eth")) return a.hostAddress
                        if (fallback == null) fallback = a.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return fallback
    }
}
