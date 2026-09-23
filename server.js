require("dotenv").config();
const express = require("express");
const crypto = require("crypto");
const path = require("path");

const app = express();
const PORT = Number(process.env.PORT || 3000);

app.use(express.json({ limit: "16kb" }));
app.use(express.static(path.join(__dirname, "public")));

function md5(value) {
  return crypto.createHash("md5").update(value, "utf8").digest("hex");
}

function makeRefId(prefix) {
  const now = new Date();
  const stamp = now.toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
  return prefix + "-" + stamp + "-" + crypto.randomBytes(3).toString("hex").toUpperCase();
}

app.post("/api/transaction", async (req, res) => {
  const username = process.env.DIGIFLAZZ_USERNAME;
  const apiKey = process.env.DIGIFLAZZ_API_KEY;

  if (!username || !apiKey) {
    return res.status(500).json({
      success: false,
      message: "Isi DIGIFLAZZ_USERNAME dan DIGIFLAZZ_API_KEY di file .env."
    });
  }

  const { product, buyer_sku_code, customer_no, amount, ref_id } = req.body || {};

  if (!["pln", "gopay"].includes(product)) {
    return res.status(400).json({ success: false, message: "Produk harus PLN atau GoPay." });
  }
  if (!buyer_sku_code || !customer_no || !amount) {
    return res.status(400).json({ success: false, message: "SKU, tujuan, dan nominal wajib diisi." });
  }
  if (!/^\\d{6,20}$/.test(String(customer_no))) {
    return res.status(400).json({ success: false, message: "Nomor tujuan hanya boleh angka." });
  }

  const finalRefId = ref_id || makeRefId(product.toUpperCase());
  const sign = md5(username + apiKey + finalRefId);

  const payload = {
    username,
    buyer_sku_code: String(buyer_sku_code),
    customer_no: String(customer_no),
    ref_id: finalRefId,
    sign
  };

  try {
    const response = await fetch("https://api.digiflazz.com/v1/transaction", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    const data = await response.json().catch(() => ({ data: null }));
    res.status(response.ok ? 200 : response.status).json({
      success: response.ok,
      request: { buyer_sku_code: payload.buyer_sku_code, customer_no: payload.customer_no, ref_id: finalRefId },
      data
    });
  } catch (error) {
    res.status(502).json({ success: false, message: "Gagal terhubung ke Digiflazz.", error: error.message });
  }
});

app.post("/api/status", async (req, res) => {
  const username = process.env.DIGIFLAZZ_USERNAME;
  const apiKey = process.env.DIGIFLAZZ_API_KEY;
  const { buyer_sku_code, customer_no, ref_id } = req.body || {};

  if (!username || !apiKey) return res.status(500).json({ success:false, message:"Credential belum diisi." });
  if (!buyer_sku_code || !customer_no || !ref_id) return res.status(400).json({ success:false, message:"SKU, tujuan, dan ref_id wajib untuk cek pending." });

  const sign = md5(username + apiKey + ref_id);
  const payload = { username, buyer_sku_code, customer_no, ref_id, sign };

  try {
    const response = await fetch("https://api.digiflazz.com/v1/transaction", {
      method:"POST",
      headers:{ "Content-Type":"application/json" },
      body:JSON.stringify(payload)
    });
    const data = await response.json().catch(() => ({ data:null }));
    res.status(response.ok ? 200 : response.status).json({ success:response.ok, request:{buyer_sku_code,customer_no,ref_id}, data });
  } catch(error) {
    res.status(502).json({success:false,message:"Gagal terhubung ke Digiflazz.",error:error.message});
  }
});

app.listen(PORT, () => console.log("Digiflazz Balance Emptier: http://localhost:" + PORT));
