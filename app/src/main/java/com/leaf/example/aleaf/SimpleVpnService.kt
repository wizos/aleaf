package com.leaf.example.aleaf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.system.Os.setenv
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class SimpleVpnService: VpnService() {
    private lateinit var bgThread: Thread
    private lateinit var protectThread: Thread

    init {
        System.loadLibrary("leafandroid")
    }

    private external fun runLeaf(configPath: String, protectPath: String)
    private external fun stopLeaf()

    private fun stopVpn() {
        stopLeaf()
        stopSelf()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                "signal_stop_vpn" -> {
                    stopVpn()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(broadcastReceiver, IntentFilter("signal_stop_vpn"))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val protectPath = File.createTempFile("leaf_vpn_socket_protect", ".sock", cacheDir).absolutePath
        protectThread = thread(start = true)  {
            val localSocket = LocalSocket()
            localSocket.bind(LocalSocketAddress(protectPath, LocalSocketAddress.Namespace.FILESYSTEM))
            val socket = LocalServerSocket(localSocket.fileDescriptor)
            var buffer = ByteBuffer.allocate(4)
            try {
                while (true) {
                    val stream = socket.accept()
                    buffer.clear()
                    val n = stream.inputStream.read(buffer.array())
                    if (n == 4) {
                        var fd = buffer.getInt()
                        if (!this.protect(fd)) {
                            println("protect failed")
                        }
                        buffer.clear()
                        buffer.putInt(0)
                    } else {
                        buffer.clear()
                        buffer.putInt(1)
                    }
                    stream.outputStream.write(buffer.array())
                }
            } catch (e: IOException) {
                println("${e}")
            }
        }
        bgThread = thread(start = true) {
            val tunFd = Builder().setSession("leaf")
                .setMtu(1500)
                .addAddress("10.255.0.1", 30)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0).establish()
            var configFile = File(filesDir, "config.conf")
            var configContent = """
            [General]
            loglevel = trace
            dns-server = 223.5.5.5
            tun-fd = REPLACE-ME-WITH-THE-FD
            [Proxy]
            Direct = direct
            """
            configContent =
                configContent.replace("REPLACE-ME-WITH-THE-FD", tunFd.detachFd().toLong().toString())
            println(configContent)
            FileOutputStream(configFile).use {
                it.write(configContent.toByteArray())
            }
            setenv("LOG_NO_COLOR", "true", true)
            runLeaf(configFile.absolutePath, protectPath)
        }
        return START_NOT_STICKY
    }
}