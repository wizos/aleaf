package com.leaf.example.aleaf

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private var state = State.STOPPED

    private enum class State {
        STARTING, STARTED, STOPPING, STOPPED
    }
    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "vpn_stopped" -> {
                    state = State.STOPPED
                    findViewById<Button>(R.id.go).text = "Go"
                }
                "vpn_started", "vpn_pong" -> {
                    state = State.STARTED
                    findViewById<Button>(R.id.go).text = "Stop"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        findViewById<Button>(R.id.go).text = "Go"

        findViewById<Button>(R.id.go).setOnClickListener { view ->
            if (state == State.STARTED) {
                state = State.STOPPING
                findViewById<Button>(R.id.go).text = "Disconnecting"
                sendBroadcast(Intent("stop_vpn"))
            } else if (state == State.STOPPED) {
                state = State.STARTING
                findViewById<Button>(R.id.go).text = "Connecting"
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else {
                    onActivityResult(1, Activity.RESULT_OK, null);
                }
            }
        }

        registerReceiver(broadcastReceiver, IntentFilter("vpn_pong"))
        registerReceiver(broadcastReceiver, IntentFilter("vpn_started"))
        registerReceiver(broadcastReceiver, IntentFilter("vpn_stopped"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, SimpleVpnService::class.java)
            startService(intent)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onResume() {
        super.onResume()
        sendBroadcast(Intent("vpn_ping"))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }
}