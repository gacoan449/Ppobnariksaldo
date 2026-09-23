package com.gacoan449.digiflazz

import android.os.Bundle
import android.text.InputType
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
    private lateinit var developmentMode: CheckBox
    private var last: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        fun field(h: String, secret: Boolean = false) = EditText(this).apply {
            hint = h
            setSingleLine(true)
            if (secret) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        val info = TextView(this).apply {
            text = "Gunakan Username + API Key dari mode API yang sama (Development/Production).\n" +
                    "RC41 = signature/API Key tidak cocok. Untuk transaksi nyata, jangan aktifkan Development/Test."
            setPadding(0, 0, 0, 16)
        }

        username = field("Digiflazz username")
        apiKey = field("Digiflazz API key", true)
        sku = field("buyer_sku_code")
        customer = field("Customer ID / nomor GoPay")
        amount = field("Nominal (contoh 10000)")

        developmentMode = CheckBox(this).apply {
            text = "Development / Test (bukan transaksi nyata)"
            isChecked = false
        }

        val buy = Button(this).apply { text = "BELI / KIRIM TRANSAKSI" }
        val retry = Button(this).apply { text = "CEK ULANG PENDING TERAKHIR" }
        result = TextView(this).apply { text = "Siap." }

        listOf(info, username, apiKey, sku, customer, amount, developmentMode, buy, retry, result)
            .forEach { box.addView(it) }

        setContentView(ScrollView(this).apply { addView(box) })

        buy.setOnClickListener { send(false) }
        retry.setOnClickListener { send(true) }
    }

    // Digiflazz: md5(username + apiKey + ref_id)
    // Manual lowercase hex: always exactly 32 ASCII characters.
    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        val out = StringBuilder(32)
        for (byte in digest) {
            val v = byte.toInt() and 0xff
            out.append(hex[v ushr 4])
            out.append(hex[v and 0x0f])
        }
        return out.toString()
    }

    // Removes invisible characters that can appear when copying credentials.
    private fun cleanCredential(value: String): String {
        return value
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\u2060", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
    }

    private fun send(status: Boolean) {
        val u = cleanCredential(username.text.toString())
        val k = cleanCredential(apiKey.text.toString())
        val s = cleanCredential(sku.text.toString())
        val c = cleanCredential(customer.text.toString())
        val a = cleanCredential(amount.text.toString())

        if (u.isEmpty() || k.isEmpty() || s.isEmpty() || c.isEmpty() || a.isEmpty()) {
            result.text = "Lengkapi username, API key, SKU, tujuan, nominal."
            return
        }

        if (!a.matches(Regex("\\d+"))) {
            result.text = "Nominal harus berupa angka."
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
            if (developmentMode.isChecked) {
                put("testing", true)
            }
            put("sign", sign)
        }

        thread {
            try {
                val conn = URL("https://api.digiflazz.com/v1/transaction")
                    .openConnection() as HttpURLConnection

                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.doOutput = true

                conn.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val text = stream?.bufferedReader()?.use { it.readText() }
                    ?: "Tidak ada response body."

                last = body

                val message = when {
                    text.contains("\"rc\":\"41\"") ->
                        "RC41: Signature tidak valid. Pastikan Username + API Key berasal dari mode API yang sama (Development/Production), lalu salin ulang API Key dari Pengaturan Koneksi API."
                    text.contains("\"rc\":\"45\"") ->
                        "RC45: IP tidak dikenali. Whitelist IP pada mode API yang sesuai di Pengaturan Koneksi API Digiflazz."
                    else -> "HTTP " + responseCode + "\n" + text
                }

                runOnUiThread {
                    result.text = message
                }
            } catch (e: Exception) {
                runOnUiThread {
                    result.text = "Error: " + e.message
                }
            }
        }
    }
}
