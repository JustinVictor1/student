package com.example.studentapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver

    private lateinit var statusText: TextView
    private lateinit var studentIdInput: EditText
    private lateinit var searchButton: Button
    private lateinit var classesList: ListView
    private lateinit var chatInput: EditText
    private lateinit var sendButton: Button

    var isWifiP2pEnabled = false
    var isConnected = false
    private var studentId: String = ""
    private val chatMessages = mutableListOf<ChatMessage>()

    private val PERMISSIONS_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.INTERNET
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()

        if (allPermissionsGranted()) {
            initializeWifiDirect()
        } else {
            requestPermissions()
        }
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        studentIdInput = findViewById(R.id.studentIdInput)
        searchButton = findViewById(R.id.searchButton)
        classesList = findViewById(R.id.classesList)
        chatInput = findViewById(R.id.chatInput)
        sendButton = findViewById(R.id.sendButton)

        searchButton.setOnClickListener { searchForClasses() }
        sendButton.setOnClickListener { sendMessage() }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeWifiDirect()
            } else {
                Toast.makeText(this, "Permissions not granted. The app cannot function properly.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeWifiDirect() {
        try {
            wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
            channel = wifiP2pManager.initialize(this, mainLooper, null)
            receiver = WifiDirectBroadcastReceiver(wifiP2pManager, channel, this)

            val intentFilter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }

            registerReceiver(receiver, intentFilter)
            updateUI()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchForClasses() {
        studentId = studentIdInput.text.toString().trim()
        if (validateStudentId(studentId)) {
            try {
                wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Toast.makeText(this@MainActivity, "Searching for classes...", Toast.LENGTH_SHORT).show()
                    }

                    override fun onFailure(reasonCode: Int) {
                        Toast.makeText(this@MainActivity, "Failed to search for classes", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Invalid Student ID", Toast.LENGTH_SHORT).show()
        }
    }

    fun onPeersAvailable(deviceList: List<WifiP2pDevice>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList.map { it.deviceName })
        classesList.adapter = adapter
        classesList.setOnItemClickListener { _, _, position, _ ->
            connectToClass(deviceList[position])
        }
    }

    private fun connectToClass(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        try {
            wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(this@MainActivity, "Connecting to class...", Toast.LENGTH_SHORT).show()
                    sendIAmHereMessage()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(this@MainActivity, "Failed to connect to class", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendIAmHereMessage() {
        // Simulate sending "I am here" message
        // In a real implementation, you would send this message over the network
        receiveChallenge()
    }

    private fun receiveChallenge() {
        // Simulate receiving a challenge
        val challengeNumber = SecureRandom().nextInt()
        sendChallengeResponse(challengeNumber)
    }

    private fun sendChallengeResponse(challenge: Int) {
        val hashedStudentId = hashStudentId(studentId)
        val encryptedResponse = encryptChallenge(challenge, hashedStudentId)
        // In a real implementation, you would send this response over the network
        // For this example, we'll simulate a successful authentication
        isConnected = true
        updateUI()
    }

    private fun encryptChallenge(challenge: Int, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val bytes = mac.doFinal(challenge.toString().toByteArray())
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun sendMessage() {
        val message = chatInput.text.toString().trim()
        if (message.isNotEmpty()) {
            val encryptedMessage = encryptMessage(message, hashStudentId(studentId))
            // In a real implementation, you would send this encrypted message over the network
            chatMessages.add(ChatMessage("Me", message))
            updateChatUI()
            chatInput.text.clear()
        }
    }

    private fun encryptMessage(message: String, key: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(message.toByteArray())
        return android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
    }

    private fun decryptMessage(encryptedMessage: String, key: String): String {
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val encryptedBytes = android.util.Base64.decode(encryptedMessage, android.util.Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }

    private fun hashStudentId(studentId: String): String {
        val bytes = studentId.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun validateStudentId(studentId: String): Boolean {
        return studentId.isNotEmpty() && studentId.length == 8 // Example validation
    }

    fun updateUI() {
        when {
            !isWifiP2pEnabled -> showWifiOffUI()
            !isConnected -> showNotConnectedUI()
            else -> showConnectedUI()
        }
    }

    private fun showWifiOffUI() {
        statusText.text = "WiFi is off. Please turn it on."
        studentIdInput.visibility = View.GONE
        searchButton.visibility = View.GONE
        classesList.visibility = View.GONE
        chatInput.visibility = View.GONE
        sendButton.visibility = View.GONE
    }

    private fun showNotConnectedUI() {
        statusText.text = "Not connected. Enter your Student ID and search for classes."
        studentIdInput.visibility = View.VISIBLE
        searchButton.visibility = View.VISIBLE
        classesList.visibility = View.VISIBLE
        chatInput.visibility = View.GONE
        sendButton.visibility = View.GONE
    }

    private fun showConnectedUI() {
        statusText.text = "Connected to class"
        studentIdInput.visibility = View.GONE
        searchButton.visibility = View.GONE
        classesList.visibility = View.GONE
        chatInput.visibility = View.VISIBLE
        sendButton.visibility = View.VISIBLE

        updateChatUI()
    }

    private fun updateChatUI() {
        // Implement chat UI update logic
        // For example, update a RecyclerView with chat messages
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}