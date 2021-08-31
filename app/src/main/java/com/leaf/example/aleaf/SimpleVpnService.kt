package com.leaf.example.aleaf

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.os.Build
import android.system.Os.setenv
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SimpleVpnService: VpnService() {
    private lateinit var leafThread: Thread
    private lateinit var protectThread: Thread
    private var running = AtomicBoolean()
    private val protectServerPort = 1085
    private lateinit var protectServer: ServerSocket

    init {
        System.loadLibrary("leafandroid")
    }

    private external fun runLeaf(configPath: String)
    private external fun stopLeaf()

    private fun stopVpn() {
        stopLeaf()
        // leafThread.interrupt()

        protectServer.close()
        protectThread.interrupt()

        stopForeground(true)
        stopSelf()
        running.set(false)
        sendBroadcast(Intent("vpn_stopped"))
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            when (intent?.action) {
                "stop_vpn" -> {
                    stopVpn()
                }
                "vpn_ping" -> {
                    if (running.get()) {
                        sendBroadcast(Intent("vpn_pong"))
                    }
                }
            }
        }
    }

    fun startForground() {
        @TargetApi(Build.VERSION_CODES.O)
        fun createNotificationChannel(channelId: String, channelName: String): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel(channelId,
                    channelName, NotificationManager.IMPORTANCE_LOW)
                chan.lightColor = Color.BLUE
                chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                service.createNotificationChannel(chan)
                return channelId
            }
            return ""
        }

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel(getString(R.string.app_name), getString(R.string.app_name))
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        val intent = Intent()
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val builder = NotificationCompat.Builder(this, channelId)

        builder.setWhen(System.currentTimeMillis())
        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
        val largeIconBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        builder.setLargeIcon(largeIconBitmap)
        builder.priority = NotificationCompat.PRIORITY_LOW
        builder.setFullScreenIntent(pendingIntent, true)

        val stopIntent = Intent(this, SimpleVpnService::class.java)
        stopIntent.action = "stop_vpn_action"
        val pendingPrevIntent = PendingIntent.getService(this, 0, stopIntent, 0)
        val prevAction = NotificationCompat.Action(android.R.drawable.ic_media_pause, "Stop", pendingPrevIntent)
        builder.addAction(prevAction)

        val bigTextStyle = NotificationCompat.BigTextStyle()
        bigTextStyle.setBigContentTitle("Leaf")
        builder.setStyle(bigTextStyle)

        val notification = builder.build()
        startForeground(1, notification)
    }

    override fun onCreate() {
        super.onCreate()
        startForground()
        registerReceiver(broadcastReceiver, IntentFilter("vpn_ping"))
        registerReceiver(broadcastReceiver, IntentFilter("stop_vpn"))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        unregisterReceiver(broadcastReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (prepare(this) != null) {
            println("VPN not prepared, handling in another activity.")
            return START_NOT_STICKY
        }

        intent?.let {
            when (it.action) {
                "stop_vpn_action" -> {
                    println("Stop from notification action.")
                    stopVpn()
                    return START_NOT_STICKY
                }
                else -> {
                }
            }
        }

        if (!this::protectThread.isInitialized || protectThread.state == Thread.State.TERMINATED) {
            protectThread = thread(start = true) {
                println("protect thread started")
                try {
                    protectServer = ServerSocket(protectServerPort)
                    while (!protectServer.isClosed) {
                        val socket = protectServer.accept()
                        thread(start = true) {
                            try {
                                var buffer = ByteBuffer.allocate(4)
                                buffer.clear()
                                val n = socket.inputStream.read(buffer.array())
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
                                socket.outputStream.write(buffer.array())
                            } catch (e: Exception) {
                                println("protect socket errored: ${e}")
                            }
                            socket.close()
                        }
                    }
                } catch (e: Exception) {
                    println("protect thread errored: ${e}")
                }
                println("protect thread exit")
            }
        }

        leafThread = thread(start = true) {
            println("leaf thread started")
            val tunFd = Builder().setSession("Leaf")
                .setMtu(1500)
                .addAddress("10.255.0.1", 30)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0).establish()

            var configFile = File(filesDir, "config.conf")
            var configContent = """
            [General]
            loglevel = warn
            dns-server = 114.114.114.114, 223.5.5.5
            routing-domain-resolve = true
            tun-fd = REPLACE-ME-WITH-THE-FD

            [Proxy]
            Direct = direct
            Reject = reject

            p13 = trojan, 141.101.121.53, 443, password=123456, sni=sw.kitslabs.com, ws=true, ws-path=/amux, ws-host=sw.kitslabs.com, amux=true
            p14 = trojan, 141.101.115.111, 443, password=123456, sni=sw.kitslabs.com, ws=true, ws-path=/amux, ws-host=sw.kitslabs.com, amux=true
            p15 = trojan, 141.101.114.172, 443, password=123456, sni=sw.kitslabs.com, ws=true, ws-path=/amux, ws-host=sw.kitslabs.com, amux=true
            p16 = trojan, 1.1.1.42, 443, password=123456, sni=sw.kitslabs.com, ws=true, ws-path=/amux, ws-host=sw.kitslabs.com, amux=true
            p17 = trojan, 1.1.1.1, 443, password=123456, sni=sw.kitslabs.com, ws=true, ws-path=/amux, ws-host=sw.kitslabs.com, amux=true
            p18 = trojan, 1.0.0.1, 443, password=123456, sni=sw.kitslabs.com, ws=true, ws-path=/amux, ws-host=sw.kitslabs.com, amux=true


            [Proxy Group]
            Proxy = failover, p13, p14, p15, p16, p17, p18, health-check=true, check-interval=600, fail-timeout=3

            [Rule]
            EXTERNAL, site:category-ads-all, Reject
            GEOIP, cn, Direct
            EXTERNAL, site:cn, Direct
            EXTERNAL, site:geolocation-!cn, Proxy
            FINAL, Proxy
            """
            configContent =
                configContent.replace("REPLACE-ME-WITH-THE-FD", tunFd?.detachFd()?.toLong().toString())
            FileOutputStream(configFile).use {
                it.write(configContent.toByteArray())
            }
            // println(configContent)

            val files = filesDir.list()
            if (!files.contains("site.dat") || !files.contains("geo.mmdb")) {
                val geositeBytes = resources.openRawResource(R.raw.site).readBytes()
                val fos2 = openFileOutput("site.dat", Context.MODE_PRIVATE)
                fos2.write(geositeBytes)
                fos2.close()

                val mmdbBytes = resources.openRawResource(R.raw.geo).readBytes()
                val fos3 = openFileOutput("geo.mmdb", Context.MODE_PRIVATE)
                fos3.write(mmdbBytes)
                fos3.close()
            }

            setenv("SOCKET_PROTECT_SERVER", "127.0.0.1:${protectServerPort}", true)
            setenv("ASSET_LOCATION", filesDir.absolutePath, true)
            setenv("LOG_CONSOLE_OUT", "true", true)
            setenv("LOG_NO_COLOR", "true", true)

            runLeaf(configFile.absolutePath)

            println("leaf thread exit")
        }
        running.set(true)
        sendBroadcast(Intent("vpn_started"))
        return START_STICKY
    }
}