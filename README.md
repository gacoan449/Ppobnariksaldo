# Digiflazz Balance Emptier

Panel lokal minimal untuk melakukan transaksi prabayar Digiflazz ke **Token PLN** atau **GoPay**.

## 1. Isi credential

Salin `.env.example` menjadi `.env`:

```bash
cp .env.example .env
```

Isi:

```env
DIGIFLAZZ_USERNAME=USERNAME_DIGIFLAZZ
DIGIFLAZZ_API_KEY=API_KEY_DIGIFLAZZ
```

**Jangan commit file `.env` dan jangan taruh API key di HTML/JavaScript frontend.**

## 2. Jalankan

Node.js 18+ diperlukan.

```bash
npm install
npm start
```

Buka `http://localhost:3000`.

## 3. Transaksi

Panel menyediakan:
- Token PLN
- GoPay
- nominal Rp10.000, Rp20.000, Rp50.000, Rp100.000, Rp200.000
- tujuan sesuai produk
- `buyer_sku_code`
- ref_id otomatis
- sign MD5 otomatis di server
- pengecekan ulang transaksi Pending memakai ref_id yang sama

Endpoint transaksi: `POST https://api.digiflazz.com/v1/transaction`.

### Penting tentang SKU

`buyer_sku_code` adalah kode produk pada akun Digiflazz. Jangan menebak SKU. Masukkan SKU persis dari daftar produk akunmu. Dengan begitu panel tidak berisiko mengirim nominal/produk yang salah.

### Pending

Jika transaksi Pending, tombol cek ulang mengirim request transaksi yang sama dengan `ref_id` yang sama. Jangan melakukan pengecekan berulang lebih sering dari satu menit.

### Data contoh yang kamu berikan

- PLN: `14257966193` — Rp10.000
- GoPay: `085642131263` — Rp200.000

Nomor tersebut belum ditanam sebagai default agar tidak ada transaksi tidak sengaja ketika aplikasi dijalankan.
