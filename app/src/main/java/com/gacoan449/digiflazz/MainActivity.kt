package com.gacoan449.digiflazz

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var username: EditText
    private lateinit var apiKey: EditText
    private lateinit var sku: EditText
    private lateinit var customer: EditText
    private lateinit var amount: EditText
    private lateinit var result: TextView
    private var last: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        fun field(h: String, secret: Boolean = false) = EditText(this).apply {
            hint = h
            if (secret) inputType = 0x81
        }

        username = field("Digiflazz username")
        apiKey = field("Digiflazz API key", true)
        sku = field("buyer_sku_code")
        customer = field("Customer ID / nomor GoPay")
        amount = field("Nominal (contoh 10000)")

        val buy = Button(this).apply { text = "BELI / KIRIM TRANSAKSI" }
        val retry = Button(this).apply { text = "CEK ULANG PENDING TERAKHIR" }
        result = TextView(this).apply { text = "Siap." }

        listOf(username, apiKey, sku, customer, amount, buy, retry, result)
            .forEach { box.addView(it) }

        setContentView(ScrollView(this).apply { addView(box) })

        buy.setOnClickListener { send(false) }
        retry.setOnClickListener { send(true) }
    }

    // Digiflazz: md5(username + apiKey + ref_id)
    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun send(status: Boolean) {
        val u = username.text.toString().trim()
        val k = apiKey.text.toString().trim()
        val s = sku.text.toString().trim()
        val c = customer.text.toString().trim()
        val a = amount.text.toString().trim()

        if (u.isEmpty() || k.isEmpty() || s.isEmpty() || c.isEmpty() || a.isEmpty()) {
            result.text = "Lengkapi username, API key, SKU, tujuan, nominal."
            return
        }

        val ref = if (status) {
            last?.optString("ref_id", "") ?: ""
        } else {
            "android_" + System.currentTimeMillis() + "_" + (100000..999999).random()
        }

        if (status && ref.isEmpty()) {
            result.text = "Belum ada transaksi untuk dicek ulang."
            return
        }

        val sign = md5(u + k + ref)

        val body = JSONObject().apply {
            put("username", u)
            put("buyer_sku_code", s)
            put("customer_no", c)
            put("ref_id", ref)
            put("sign", sign)
        }

        thread {
            try {
                val conn = URL("https://api.digiflazz.com/v1/transaction")
                    .openConnection() as HttpURLConnection

                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val text = (if (conn.responseCode in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }).bufferedReader().readText()

                last = body

                runOnUiThread {
                    result.text = "HTTP ${conn.responseCode}\n$text"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    result.text = "Error: ${e.message}"
                }
            }
        }
    }
}
