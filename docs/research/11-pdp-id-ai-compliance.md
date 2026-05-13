# Research 11 — PDP-ID + Kominfo + OJK Compliance untuk AI Assistant Indonesia

> **Konteks**: Riset compliance untuk ChibiClaw (Android AI assistant, akses kontak/lokasi/SMS/audio/notif/accessibility, opsional cloud LLM transmission). Output ini untuk arah kebijakan internal personal project + roadmap kalau di-productize.
> **Penulis**: research agent (Claude Opus 4.7)
> **Tanggal dibuat**: 2026-05-13
> **Tanggal akses sumber**: 2026-05-13
> **Status**: Draft 1 — pre-implementation reference

---

## DISCLAIMER NON-LEGAL (WAJIB BACA)

Dokumen ini **BUKAN nasihat hukum** dan **BUKAN substitusi konsultasi advokat data privacy bersertifikat**. Penulis bukan advokat. Riset ini disusun untuk:

1. Memberi arah kebijakan internal ChibiClaw (personal project) per Mei 2026.
2. Menjadi titik awal diskusi dengan advokat ketika ChibiClaw siap di-productize.
3. Mendokumentasikan rujukan resmi (UU, PP, Permenkominfo, POJK) berikut URL akses.

Untuk eksekusi nyata — registrasi PSE, penunjukan DPO, drafting privacy notice yang mengikat secara hukum, jawaban atas surat dari Kominfo/lembaga PDP — **wajib konsultasi advokat data privacy bersertifikat** (APPDI, IAPP, atau anggota Peradi/PERADIN yang memiliki spesialisasi cyber/data). Banyak pasal di UU PDP masih menunggu Peraturan Pemerintah (PP) pelaksanaan, sehingga interpretasi yang berkembang di praktik akan menentukan posisi hukum aktual.

> Apabila terdapat area abu-abu (gray area), dokumen ini akan **eksplisit menandai** dengan tag **[GRAY AREA]** dan rekomendasi "perlu konsultasi advokat".

---

## Executive Summary

ChibiClaw sebagai AI assistant Android yang akses data sensitif (kontak, lokasi, SMS, mic always-on, notification, accessibility, optional cloud transmission) berada di **persimpangan tiga rezim regulasi utama** di Indonesia per Mei 2026:

1. **UU No. 27/2022 tentang Pelindungan Data Pribadi (UU PDP)** — berlaku penuh sejak 17 Oktober 2024, sanksi administratif sudah aktif. Memuat hak subjek data, kewajiban Pengendali/Prosesor, definisi data spesifik termasuk biometrik, breach notification 3x24 jam, dan denda hingga 2% pendapatan tahunan. **Peraturan Pemerintah (PP) pelaksanaan belum disahkan** per Mei 2026 — banyak detail teknis masih diatur Permenkominfo lama (Permenkominfo 20/2016) yang relevan secara mutatis mutandis.
2. **Regulasi Komdigi (eks Kominfo) PSE Privat** — Permenkominfo 10/2021 + 5/2020 mewajibkan pendaftaran Sistem Elektronik. Untuk personal project belum profit, status pendaftaran adalah **[GRAY AREA]** — namun begitu ChibiClaw masuk Play Store dengan model komersial/freemium, kewajiban PSE Privat hampir pasti aktif.
3. **POJK + Bank Indonesia (BI)** — kalau ChibiClaw membantu transfer bank/QRIS otomatis, ketentuan POJK 11/2022 (Penyelenggaraan TI Bank), POJK AI Banking 2025, dan PBI No. 3/2025 (QRIS) ikut berlaku. Tier risk-nya jauh lebih ketat (perlakukan setara dengan PJP — Penyedia Jasa Pembayaran).

Tambahan dimensi internasional yang **akan menyentuh ChibiClaw** kalau go global:

- **EU AI Act Article 50** (effective 2 Agustus 2026) — transparansi AI generatif, watermarking konten sintetis, deepfake disclosure.
- **GDPR (opt-in)** vs **CCPA/CPRA 2026 (opt-out)** — voice biometrik diperlakukan sebagai data sensitif di kedua rezim.
- **ISO/IEC 42001:2023** (AI Management System) — voluntary tapi makin jadi de-facto requirement untuk vendor AI yang scale.

**Confidence level**: medium-high untuk pasal UU PDP yang sudah jelas dirujuk; **low-medium** untuk implementasi praktis karena PP PDP belum terbit dan badan pengawas independen (Lembaga PDP) belum sepenuhnya operasional per Mei 2026.

---

## 1. UU PDP 27/2022 — Kewajiban Pengendali Data Pribadi

### 1.1 Status keberlakuan per Mei 2026

UU No. 27/2022 disahkan **17 Oktober 2022** dan berlaku penuh **17 Oktober 2024** (masa transisi 2 tahun untuk adjustment). Sanksi administratif (termasuk denda) aktif sejak Oktober 2024. Rujukan resmi: [Naskah UU di JDIH BPK RI](https://peraturan.bpk.go.id/Details/229798/uu-no-27-tahun-2022) dan [JDIH Kemkomdigi](https://jdih.komdigi.go.id/produk_hukum/view/id/832/t/undangundang+nomor+27+tahun+2022).

**[GRAY AREA]** PP PDP (Peraturan Pemerintah pelaksanaan) **belum disahkan** per Mei 2026 menurut sumber terbuka — penyusunan dimulai Januari 2023 namun beberapa item kunci (tolok ukur kesetaraan negara penerima untuk Pasal 56, struktur Lembaga PDP) masih dalam proses. Konsultasi advokat untuk verifikasi status RPP terkini.

### 1.2 Definisi: Data Pribadi vs Data Pribadi Spesifik

**Pasal 4 UU PDP** membagi dua kategori:

**Data Pribadi Umum (Pasal 4 ayat 3)**:
- Nama lengkap, jenis kelamin, kewarganegaraan, agama (umum), status perkawinan
- Data pribadi yang dikombinasikan untuk identifikasi seseorang

**Data Pribadi Spesifik (Pasal 4 ayat 2)** — perlakuan ekstra ketat:
- Data dan informasi kesehatan
- Data biometrik (sidik jari, retina, wajah, **suara**, DNA — sesuai praktik internasional)
- Data genetika
- Catatan kejahatan
- Data anak
- Data keuangan pribadi
- Data lainnya sesuai ketentuan peraturan perundang-undangan

> **Implikasi ChibiClaw**:
> - **Voice biometrik** (Fuu voice clone TTS, voice fingerprint dari wake word) **sangat besar kemungkinan masuk Data Spesifik**. APPDI dan praktisi privacy umum menafsirkan suara sebagai biometrik begitu karakteristik akustik unik bisa dipakai mengidentifikasi individu. **[GRAY AREA]** — definisi explicit "voice" di UU PDP Indonesia belum se-tegas GDPR Article 9; konfirmasi via advokat.
> - **Sidik jari/wajah** untuk unlock app — biometrik spesifik.
> - **Kontak + notification chat** — data pribadi pihak ketiga (kontak Budi yang nomornya tersimpan adalah subjek data juga). Multi-subjek consent rumit (lihat Section 1.6).

### 1.3 Hak Subjek Data (Pasal 5–13)

Berdasarkan kompilasi dari [BP Lawyers UU PDP](https://bplawyers.co.id/2022/11/15/uu-pdp-berlaku-ini-isi-pengaturan-perlindungan-data-pribadi-di-indonesia/) dan [Hukumonline](https://www.hukumonline.com/klinik/a/uu-pdp--landasan-hukum-pelindungan-data-pribadi-lt5d588c1cc649e/):

| Pasal | Hak |
|-------|-----|
| Pasal 5 | Hak atas informasi (kejelasan identitas Pengendali, dasar hukum, tujuan, akuntabilitas) |
| Pasal 6 | Hak melengkapi/memperbarui/mengoreksi data |
| Pasal 7 | Hak akses dan memperoleh salinan |
| Pasal 8 | Hak mengakhiri pemrosesan, menghapus, dan/atau memusnahkan (Right to Erasure) |
| Pasal 9 | Hak menarik persetujuan |
| Pasal 10 | Hak menolak tindakan pengambilan keputusan otomatis (Automated Decision-Making) — relevan untuk AI assistant |
| Pasal 11 | Hak menunda atau membatasi pemrosesan |
| Pasal 12 | Hak menggugat dan menerima ganti rugi |
| Pasal 13 | Hak portabilitas data (data portability) |
| Pasal 14 | Pelaksanaan hak melalui permohonan tercatat (elektronik/non-elektronik) |
| Pasal 15 | Pengecualian (kepentingan pertahanan/keamanan, penegakan hukum, kepentingan umum, statistik, dll) |

> **Implikasi ChibiClaw**: harus menyediakan UI di Settings untuk:
> - Export data (JSON/CSV) per kategori (audit log, transcript voice, kontak yang pernah diakses).
> - Hapus akun + purge data lokal dan cloud (kalau pernah sync).
> - Withdraw consent granular (lihat consent flow Section 6.2).
> - Opt-out automated decision (mode "ask before doing").

### 1.4 Kewajiban Pengendali Data Pribadi (Pasal 20–50)

**Highlight pasal yang langsung mengikat ChibiClaw**:

- **Pasal 20**: persetujuan harus sah, jelas, sukarela, eksplisit (untuk Data Spesifik).
- **Pasal 21**: privacy notice **wajib memuat** (a) legalitas pemrosesan, (b) tujuan, (c) jenis dan relevansi data, (d) periode retensi, (e) detail tentang data yang dikumpulkan, (f) jangka waktu pemrosesan, (g) hak subjek data.
- **Pasal 22**: persetujuan tertulis atau terekam.
- **Pasal 25**: persetujuan dapat ditarik kapan saja.
- **Pasal 26**: anak-anak — persetujuan dari orang tua/wali.
- **Pasal 28**: pemrosesan data spesifik wajib disertai DPIA (Data Protection Impact Assessment).
- **Pasal 35**: kerahasiaan data.
- **Pasal 36**: pencegahan akses tidak sah.
- **Pasal 39**: rekaman aktivitas pemrosesan (record of processing activities — analog ke GDPR Art. 30).
- **Pasal 46**: **notifikasi kegagalan pelindungan (data breach)** ke subjek data dan otoritas dalam **paling lambat 3x24 jam** sejak diketahui. Konfirmasi: [ITGID Sanksi UU PDP](https://itgid.org/insight/artikel-it/sanksi-uu-pdp-analisis-risiko-mitigasi-denda-maksimal/).
- **Pasal 47**: jika ada dampak hak/kepentingan subjek data, wajib publikasi dan/atau notifikasi luas.

### 1.5 DPO (Data Protection Officer) — Pasal 53

**Tiga kriteria** (sebelumnya kumulatif, **PASCA PUTUSAN MK** menjadi alternatif — cukup salah satu terpenuhi):

1. Pemrosesan untuk kepentingan **pelayanan publik**.
2. Kegiatan inti memerlukan **pemantauan teratur dan sistematis atas data dalam skala besar**.
3. Kegiatan inti memproses **Data Spesifik** atau Data Pidana **dalam skala besar**.

Putusan MK ini sangat penting — sumber: [Prolegal](https://prolegal.id/mk-kabulkan-uji-materi-aturan-pelindungan-data-pribadi-penunjukan-dpo-jadi-lebih-ketat/).

> **Implikasi ChibiClaw**:
> - **Phase 1 (personal, <50 user)**: DPO **belum wajib**. Skala besar belum terpenuhi.
> - **Phase 2 (beta 5–10 tester)**: belum wajib.
> - **Phase 3 (public stable, 10k+ user yang menyetujui voice clone)**: **kemungkinan wajib** karena memproses Data Spesifik (voice biometrik) skala besar. Threshold "skala besar" belum didefinisi quantitatively di UU/RPP — **[GRAY AREA]**.
> - **Kualifikasi DPO**: profesionalitas, pengetahuan hukum + praktik perlindungan data, dapat dijalankan tugasnya. Tidak harus advokat. Bisa kontrak outsourced DPO.

### 1.6 Cross-Border Transfer (Pasal 56)

**Tiga skema** berdasarkan [Periskop](https://periskop.id/artikel/20250723/membedah-aturan-transfer-data-ke-luar-negeri-dalam-uu-pdp):

1. Negara penerima punya level perlindungan **setara atau lebih tinggi** dari UU PDP.
2. Ada **instrumen hukum mengikat** — perjanjian internasional, atau **Standard Contractual Clauses (SCC)** yang mengikat penerima tunduk pada standar UU PDP.
3. **Persetujuan eksplisit subjek data** setelah diberi informasi jelas tentang risiko.

> **[GRAY AREA] PP tolok ukur kesetaraan belum terbit.** Per Mei 2026, daftar resmi negara "adequate" untuk Indonesia tidak ada. Praktik aman: **andalkan skema 2 (SCC) + skema 3 (explicit consent)**.

> **Implikasi ChibiClaw untuk Cloud LLM** (Anthropic Claude, OpenAI GPT, Google Gemini):
> - **Anthropic**: tidak punya region Indonesia per Mei 2026. Data lewat AWS US/EU.
> - **OpenAI**: Azure OpenAI ada region Singapore/Japan tapi tidak Indonesia native.
> - **Google Gemini**: Vertex AI ada region Jakarta (asia-southeast2) — **paling defensible** untuk argumen "tidak melintasi border" (walau secara legal masih layer Google US/Alphabet).
> - **Rekomendasi**: kalau cloud LLM dipakai, **wajib SCC** di T&C (kalau B2B contract) atau **explicit consent dengan disclosure risiko** (untuk B2C — pengguna sentuh tombol "saya setuju kirim data ke server di luar Indonesia").

### 1.7 Sanksi (Pasal 57 administratif + Pasal 67–73 pidana)

**Sanksi administratif (Pasal 57)** — berurutan/akumulatif:

1. Peringatan tertulis.
2. Penghentian sementara pemrosesan.
3. Penghapusan/pemusnahan data.
4. **Denda administratif paling tinggi 2% dari pendapatan tahunan** atas variabel pelanggaran.

Konfirmasi sumber: [ITGID Sanksi UU PDP](https://itgid.org/insight/artikel-it/sanksi-uu-pdp-analisis-risiko-mitigasi-denda-maksimal/), [CNN Indonesia](https://www.cnnindonesia.com/teknologi/20240317021038-192-1075175/sanksi-uu-pdp-berlaku-oktober-2024-bagaimana-kasus-yang-sudah-lewat).

**Sanksi pidana (Pasal 67–73)**:

- Mengumpulkan data tidak sah untuk merugikan: **maks 5 tahun penjara, denda 5 miliar**.
- Mengungkapkan data: maks 4 tahun, denda 4 miliar.
- Memalsukan data: maks 6 tahun, denda 6 miliar.
- Sanksi tambahan: perampasan keuntungan, penutupan aktivitas, pembekuan badan hukum.

> **Implikasi ChibiClaw**: untuk personal project tanpa revenue, denda 2% praktis nol — tapi **sanksi peringatan tertulis + perintah hapus data** tetap berlaku dan dapat di-publikasi (reputasi rusak). Pidana bersifat personal kalau ada intent merugikan.

---

## 2. Komdigi (eks Kominfo) — PSE Privat

### 2.1 Dasar hukum

- **Permenkominfo No. 5/2020** tentang Penyelenggara Sistem Elektronik Lingkup Privat (sebagaimana diubah Permenkominfo No. 10/2021).
- Pendaftaran via portal [pse.komdigi.go.id](https://pse.komdigi.go.id/panduan).

### 2.2 Kategori wajib daftar

Sesuai [layanan.komdigi PSE FAQ](https://layanan.kominfo.go.id/faqs/19427216359acbe7f486d68029557702), kategori PSE Privat yang **wajib daftar**:

1. Perdagangan barang/jasa (marketplace, e-commerce).
2. Layanan transaksi keuangan.
3. Pengiriman materi digital berbayar (paid content delivery).
4. Layanan komunikasi (SMS, voice/video call, email, chat, social media platform).
5. Mesin pencari, layanan jejaring, blog, layanan pendidikan, kesehatan, pemerintahan.
6. Sistem yang memproses data pribadi untuk kegiatan operasional pelayanan publik.

> **Implikasi ChibiClaw**:
> - **Phase 1 (personal, internal use)**: **bukan PSE Privat wajib daftar** karena bukan layanan publik. Aman.
> - **Phase 2 (beta tester closed)**: **[GRAY AREA]** — jika beta lewat Play Store internal testing yang tertutup, masih bisa argument "bukan layanan publik". Jika beta terbuka (open testing), risiko sudah masuk kategori 4 (layanan komunikasi/AI productivity).
> - **Phase 3 (public stable di Play Store)**: **WAJIB daftar PSE Privat**. AI assistant yang memproses pesan/chat user masuk kategori komunikasi.

### 2.3 Prasyarat pendaftaran (Domestik)

Berdasarkan [pse.komdigi panduan tahapan](https://pse.komdigi.go.id/panduan/tahapan-pendaftaran-pse-lingkup-privat-domestik):

1. **NIB (Nomor Induk Berusaha)** via OSS (Online Single Submission) — perlu badan usaha (PT/CV/Yayasan/Perkumpulan) atau perseorangan UMK.
2. **Izin Berusaha terkait** sesuai bidang.
3. **KBLI yang sesuai** — untuk AI assistant kemungkinan KBLI 62012 (Aktivitas Pengembangan Aplikasi Perdagangan Melalui Internet) atau 63122 (Portal Web/Platform Digital Dengan Tujuan Komersial).
4. Biaya pendaftaran **gratis** sampai terbit TDPSE (Tanda Daftar PSE).

> **Implikasi ChibiClaw**: harus bentuk badan hukum dulu (CV minimum, biaya ~Rp 2-5 juta jasa notaris). Tanpa NIB, tidak bisa daftar PSE. Solusi untuk Phase 2: bisa pinjam badan hukum afiliasi (BIGNET-ID) **kalau ada perjanjian internal yang jelas**.

### 2.4 Sanksi tidak daftar

Pemblokiran akses platform di Indonesia. Preseden: PayPal, Yahoo, Steam pernah diblokir 2022 sebelum daftar. Untuk ChibiClaw di Play Store, dampak praktis: Google bisa diminta unlist di region ID (request take-down dari Kominfo).

---

## 3. Konsekuensi Spesifik AI Assistant ChibiClaw

### 3.1 Audio recording wake word always-on mic

**Risiko**: mic always-on bisa nangkap konversasi privat orang ketiga (yang **tidak consent**). Di GDPR Art. 6 dan UK ICO guidance, ini sudah dianggap masalah serius — voice assistant Amazon/Google pernah didenda.

**Compliance ChibiClaw**:

1. **Wake-word detection lokal di-device** — tidak streaming audio ke cloud. Voice activity detection (VAD) pakai model kecil seperti Porcupine atau Picovoice. Audio hanya disimpan transient (~3-5 detik buffer), tidak persisted ke disk.
2. **Visual indicator** ketika mic aktif (LED notification, ikon di status bar) — sesuai praktik Android privacy indicator (Android 12+ sudah ada green dot di top-right).
3. **Privacy notice eksplisit**: "ChibiClaw mendengarkan secara terus-menerus untuk wake word 'Hai Fuu'. Audio diproses lokal di device kamu, tidak dikirim ke server."
4. **Toggle off mic always-on** — user harus bisa matikan dan tetap pakai via tombol manual.
5. **Recording pasca wake-word** — opsi: (a) langsung process intent, JANGAN simpan rekaman; atau (b) kalau perlu simpan untuk training, **explicit opt-in terpisah** + retention max 30 hari + bisa delete.

### 3.2 Voice biometrik (Fuu voice clone)

**Pasal 4 ayat 2 UU PDP** + interpretasi APPDI: voice adalah biometrik. Membangun voice clone berarti memproses Data Spesifik.

**Compliance ChibiClaw**:

- Voice **user** untuk pelatihan personalisasi: **wajib explicit consent** dengan disclosure "kami akan ekstrak fitur akustik suara Anda untuk fine-tune model personalisasi. Voice embedding disimpan lokal di [path]. Anda bisa hapus kapan saja."
- Voice **karakter Fuu** (TTS output): kalau pakai voice asli orang lain (dub), **wajib lisensi voice + consent dari pemilik suara** + watermark sintetis (EU AI Act Article 50, lihat Section 6.4).
- **DPIA (Data Protection Impact Assessment) wajib** sesuai Pasal 28 karena Data Spesifik.

### 3.3 LLM cloud transmission (kontak/chat user dikirim ke OpenAI/Anthropic)

**Risiko tertinggi**. Konteks kontak (nama + nomor + relasi) dan konten notifikasi (chat WA) adalah:
- Data pribadi **user** (terkait dirinya).
- Data pribadi **pihak ketiga** (kontak Budi, isi chat dari teman). Subjek data pihak ketiga **tidak consent** ke ChibiClaw.

**Compliance ChibiClaw**:

1. **Default: full local mode** — Gemma 4 E4B di device. Tidak ada transmisi.
2. **Cloud mode opt-in eksplisit per-action** atau global setting dengan disclosure jelas.
3. **PII redaction sebelum kirim ke cloud** — replace nama kontak dengan `[CONTACT_1]`, nomor dengan `[PHONE_1]`. Mapping disimpan lokal, hasil LLM di-rehydrate sebelum dieksekusi.
4. **No logging by provider** — pakai API setting yang explicit zero-retention (Anthropic Claude API punya opt-out `Zero Data Retention`; OpenAI Enterprise tier). **[GRAY AREA]** untuk Anthropic ZDR untuk personal API key — verifikasi T&C terkini.
5. **Audit log lokal** setiap kali ada transmisi cloud (lihat Section 7).

### 3.4 Persistent screen capture (vision-first)

**Risiko sangat tinggi** karena screenshot bisa berisi:
- Saldo bank (m-banking app).
- Pesan pribadi (WhatsApp).
- Foto/dokumen pribadi.
- Login credentials (form input).

**Compliance ChibiClaw**:

1. **Screenshot hanya saat dibutuhkan untuk task spesifik** — tidak kontinyu di background.
2. **App blacklist** — daftar app yang tidak akan di-screenshot (m-banking, password manager, gallery app pribadi). User bisa tambah.
3. **OCR + analisis lokal dulu**, transmisi cloud hanya kalau lokal gagal + user konfirmasi.
4. **Auto-redact PII** dengan vision model lokal sebelum transmisi (mask kotak hitam di area kartu kredit/saldo).
5. **Screenshot retention**: in-memory only kecuali user opt-in untuk debug log (max 7 hari).
6. **Android 16/17 sensitive flag** — screenshot otomatis di-blur untuk app yang set `accessibilityDataSensitive`/`setSecure` flag. Hormati flag tersebut.

### 3.5 Notification listener (read chat content)

**Permission Android**: `BIND_NOTIFICATION_LISTENER_SERVICE` — sangat sensitif. Membaca notifikasi WhatsApp = membaca chat antara user dan pihak ketiga.

**Compliance ChibiClaw**:

1. **App allowlist**, bukan blanket access — user pilih app mana yang notifikasinya boleh dibaca.
2. **Content filter** — kalau notif berisi pattern OTP/PIN/credit card, jangan transmit ke cloud, jangan store.
3. **No persistence** — notif di-process di RAM saja, di-discard setelah action selesai (kecuali user explicit save).
4. **Disclosure di privacy notice**: "ChibiClaw membaca konten notifikasi dari [list app yang user pilih] untuk trigger respons. Konten chat dari pihak ketiga juga akan terbaca."

### 3.6 Accessibility service (UI control)

Sama-sama sensitif. Bisa baca semua text di layar (termasuk password kalau tidak masked).

**Compliance ChibiClaw**:

- `serviceInfo.flags` set untuk **tidak menerima `FLAG_REPORT_VIEW_IDS` dari app sensitif** (Android system enforces this lewat `accessibilityDataSensitive`).
- Log accessibility action per action_id, audit retention 90 hari (lihat Section 7).
- Disclosure: "ChibiClaw butuh Accessibility Service untuk klik tombol di app lain atas perintah Anda. Service ini bisa membaca seluruh layar — kami tidak menyimpan konten layar di luar yang Anda perintahkan."

---

## 4. OJK & Bank Indonesia — Kalau ChibiClaw Sentuh Keuangan

### 4.1 OJK AI Governance for Indonesian Banking (April 2025)

OJK rilis [Buku Panduan Tata Kelola Kecerdasan Artifisial Perbankan Indonesia](https://ojk.go.id/en/Publikasi/Roadmap-dan-Pedoman/Perbankan/Pages/Indonesia-Artificial-Intelligence-Governance-for-Banking.aspx) April 2025. Enam prinsip dasar:

1. Berbasis Pancasila.
2. Bermanfaat.
3. Adil dan jujur.
4. Akuntabel.
5. Transparan dan dapat dijelaskan (explainability).
6. Ketahanan dan keamanan.

Komplementer dengan:
- **POJK 11/POJK.03/2022** — Penyelenggaraan TI oleh Bank Umum.
- **SEOJK 29/SEOJK.03/2022** — Ketahanan dan Keamanan Siber Bank Umum.
- **SEOJK 24/SEOJK.03/2023** — Penilaian Tingkat Maturitas Digital Bank Umum.

> **Implikasi ChibiClaw**: panduan ini target **bank**, bukan app pihak ketiga. Tapi kalau ChibiClaw integrate dengan bank API (open finance / open banking), bank wajib audit ChibiClaw sebagai vendor.

### 4.2 Panduan Kode Etik AI di Fintech (Des 2024)

[OJK + IAKD update Kode Etik AI fintech](https://www.aicerts.ai/blog/indonesias-ojk-updates-ai-ethics-code-to-tackle-fintech-risks/). Fokus: automated decision making, data misuse, unequal outcomes. Relevan kalau ChibiClaw bantu kredit scoring atau financial advice.

### 4.3 PBI No. 3/2025 — QRIS

Bank Indonesia [PBI Gubernur No. 3/2025](https://www.bi.go.id/en/fungsi-utama/sistem-pembayaran/ritel/kanal-layanan/qris/default.aspx) effective 19 Februari 2025. Mengatur standar nasional QR code pembayaran.

> **Implikasi ChibiClaw**: kalau "Tolong bayar QRIS warung A" trigger automation pembayaran:
> - ChibiClaw bukan PJP (Penyedia Jasa Pembayaran) — TIDAK BOLEH ambil/teruskan uang.
> - Hanya boleh **invoke** aplikasi PJP existing user (Gopay/OVO/DANA/m-banking) via Intent dan biarkan user confirm.
> - Konfirmasi manual user **wajib** — autopayment tanpa konfirmasi setara dengan ChibiClaw menjadi PJP shadow (illegal).
> - PBI 10/2025 (Activity-Based Licensing untuk PJP) — kalau scale up, perlu lisensi PJP yang sangat berat. **Stay out of becoming PJP.**

### 4.4 Permenkominfo 20/2016 (Perlindungan Data Pribadi dalam Sistem Elektronik)

[Permenkominfo 20/2016](https://peraturan.bpk.go.id/Home/Details/150543/permenkominfo-no-20-tahun-2016). Masih berlaku untuk **PSE pelayanan publik** — data center & DRC **wajib di wilayah RI**. Tidak otomatis berlaku PSE privat, tapi argumentasi defensif tetap dipakai praktisi.

Untuk Phase 3 ChibiClaw kalau argumentasikan "bukan pelayanan publik" — **[GRAY AREA]**, konsultasi advokat.

---

## 5. Comparative Regulation Internasional

### 5.1 EU AI Act 2026 (effective 2 Agustus 2026)

[Article 50 EU AI Act](https://artificialintelligenceact.eu/article/50/) — transparency obligations:

- **Provider AI sistem generatif**: output (audio/image/video/text) **wajib watermark** machine-readable dan detectable as AI-generated.
- **Deployer (yang publish konten AI realistis termasuk deepfake)**: **wajib disclose** ke pengguna bahwa konten artifisial.
- Pengecualian: law enforcement, karya seni/fiksi yang jelas.
- Code of Practice voluntary tersedia ([digital-strategy.ec.europa.eu](https://digital-strategy.ec.europa.eu/en/policies/code-practice-ai-generated-content)).

**Risk tier EU AI Act**:
- **Unacceptable risk** (prohibited): social scoring, manipulative AI. ChibiClaw tidak masuk.
- **High-risk**: AI di critical infrastructure, education, employment, law enforcement, biometric ID. **Voice biometric authentication** untuk akses sistem **MASUK high-risk**. Voice clone untuk produktivitas pribadi **borderline** — **[GRAY AREA]**.
- **Limited risk** (transparency): chatbot, deepfake. ChibiClaw fit di sini untuk respons AI biasa.
- **Minimal risk**: spam filter, game AI. Tidak relevan.

> **Implikasi ChibiClaw kalau target EU**: 
> - Watermarking audio output Fuu TTS (audio fingerprint atau inaudible signal).
> - "I am AI" disclosure di first interaction.
> - Logging AI decision untuk audit.

### 5.2 GDPR (opt-in) vs CCPA/CPRA 2026 (opt-out)

| Aspek | GDPR | CCPA/CPRA 2026 |
|-------|------|----------------|
| Default consent | **Opt-in** untuk semua processing | **Opt-out** untuk sale/sharing |
| Data minor | Parental consent <16 | Affirmative consent <16 (CPRA 2026 expanded) |
| Voice biometric | Article 9 Special Category — explicit consent | Sensitive PI — opt-out + heightened restrictions |
| Cross-border | SCC, BCR, adequacy decisions | Limited specific rules |
| Penalty | EUR 20M atau 4% revenue | USD 7,500 per intentional violation |

Konfirmasi: [TrustScan GDPR vs CCPA 2026](https://trustscan.dev/blog/gdpr-vs-ccpa-2026).

> **Praktik aman**: bangun program compliance dengan **standar GDPR (stricter)**, layer CCPA on top. UU PDP Indonesia secara struktur lebih dekat ke GDPR — strategi opt-in-default tetap cocok.

### 5.3 Singapore PDPA + Korea PIPA + Japan APPI

Highlight perbedaan signifikan ([CookieScript APAC](https://cookie-script.com/privacy-laws/asia-pacific-nuances-appi-pipa-pdpa), [Pertama Partners APAC 2026](https://www.pertamapartners.com/insights/cross-border-data-transfers-asia)):

- **Singapore PDPA**: biometrik diakui sebagai PD; deemed consent ada (lebih fleksibel dari GDPR). Voice = personal data.
- **Korea PIPA**: paling ketat di APAC. **Explicit consent + notifikasi regulator** untuk cross-border tertentu. Amandemen terbaru izinkan pakai PII untuk AI training under "public interest" dengan review.
- **Japan APPI**: punya EU adequacy decision (2019). Voice = personal information. Soft-start enforcement.

> **Implikasi ChibiClaw kalau go regional**: prinsipal target adalah **opt-in + SCC + lokalisasi voice biometric** — strategi defensif sekali drafting yang cover semua.

---

## 6. Practical Implementation untuk ChibiClaw

### 6.1 Privacy Notice — outline konten wajib (Pasal 21 UU PDP)

Struktur disarankan (Bahasa Indonesia resmi, juga sediakan versi kasual untuk akses cepat):

```
1. Identitas Pengendali Data Pribadi
   - Nama, badan hukum (Phase 3+), alamat, kontak DPO (kalau ada)
2. Dasar Hukum Pemrosesan
   - Persetujuan eksplisit Anda (Pasal 20 UU PDP)
3. Jenis Data yang Diproses
   - Daftar granular (kontak, lokasi, audio mic, notif, screenshot)
4. Tujuan Pemrosesan
   - Per kategori data
5. Pihak Ketiga Penerima Data
   - Cloud LLM provider (Anthropic/OpenAI/Google) kalau opt-in
   - Region: AS/Singapore/Jakarta
6. Cross-border Transfer
   - Disclosure risiko + dasar hukum (SCC / explicit consent)
7. Periode Retensi
   - Lokal: 90 hari audit log, 0 hari transcript voice (default)
   - Cloud: zero data retention provider config
8. Hak Anda
   - List Pasal 5-13 + cara akses (Settings → Data & Privacy)
9. Mekanisme Penarikan Persetujuan
   - Toggle per kategori di Settings
10. Cara Mengajukan Pengaduan
    - Email DPO + escalation ke Lembaga PDP / Komdigi
11. Tanggal Berlaku + Versi
```

Template referensi: [Telkomsel Kebijakan Privasi](https://www.telkomsel.com/kebijakan-privasi), [JakOne Mobile Privacy Notice](https://jakone.mobi/jakone-blog/pemberitahuanprivasi-jakonemobile), [INAgov Privacy Notice](https://inagov.go.id/privacy-notice).

### 6.2 Consent flow UX — granular per-permission

Flow yang disarankan saat first install:

```
Welcome screen
  ↓
Tier 1 (essential, opt-in tapi mandatory untuk basic function):
  - Mic untuk wake word + voice command [HARUS untuk Fuu]
    > Pelajari lebih lanjut: link ke section privacy notice
  - Audio processing local-only? [Toggle, default ON]
  ↓
Tier 2 (extended, opt-in granular):
  - Akses Kontak (untuk "kirim WA ke Budi")
    > Tanpa ini: ChibiClaw masih jalan, tapi tidak bisa otomatis cari nomor
  - Akses Lokasi (untuk "navigasi ke kantor")
  - Akses SMS (untuk kirim SMS atomatis)
  - Akses Notifikasi (untuk respon ke chat masuk)
  - Akses Accessibility (untuk UI control app lain)
  ↓
Tier 3 (advanced, opt-in dengan disclosure khusus):
  - Cloud LLM (kirim query ke server provider AS/Singapore)
    > Disclosure: risiko cross-border, daftar provider
  - Voice clone training (Fuu pakai suara Anda)
    > Disclosure: voice biometric = Data Spesifik UU PDP
  - Vision-first (screenshot otomatis)
    > Disclosure: app blacklist default + custom
```

**Prinsip granular consent**: setiap toggle satu permission satu purpose. **Bundled consent** ("setuju semua untuk lanjut") **tidak sah** menurut UU PDP Pasal 20 (harus spesifik). Konfirmasi di [Hukumonline UU PDP](https://www.hukumonline.com/klinik/a/uu-pdp--landasan-hukum-pelindungan-data-pribadi-lt5d588c1cc649e/).

### 6.3 Data minimization

Implementation choices:

- **Voice transcript**: **JANGAN simpan** transcript voice ke disk. Process → intent label → simpan **intent saja** (`{intent: "send_message", recipient_hash: "xyz", timestamp}`). Audio buffer di RAM saja.
- **Contact data**: **JANGAN sync seluruh contact book ke cloud**. Local search → cherry-pick kontak target → hanya hash kontak yang ditransmit kalau perlu konteks.
- **Screenshot**: in-memory tensor, tidak persist ke disk kecuali user explicit save untuk debug.
- **Location**: round ke 100m precision untuk request umum. Full precision hanya untuk turn-by-turn nav, lalu segera discard.

### 6.4 Right to be forgotten — implementation

UI Settings → Data & Privacy → "Hapus akun ChibiClaw":

```
[Konfirmasi 1] Anda akan menghapus semua data ChibiClaw:
  - Audit log lokal
  - Voice embedding personalisasi
  - Cache intent history
  - Settings & preferences

[Konfirmasi 2] Juga hapus dari cloud (kalau pernah sync)?
  - Send "DELETE" request ke backend ChibiClaw
  - Forward delete request ke LLM provider yang punya log (jika applicable)

[Final confirm] Hapus akun
  ↓
Delete chain:
  1. Wipe Room DB (audit_log, embeddings, intents)
  2. Wipe SharedPreferences encrypted
  3. Revoke OAuth tokens (cloud LLM if any)
  4. Send tombstone request ke backend (kalau Phase 3)
  5. Show confirmation + ID untuk follow-up
```

Untuk delete cloud LLM history: 
- **Anthropic API**: ZDR mode = nothing to delete. Non-ZDR = standard 30-day retention, request via support.
- **OpenAI**: data deletion request via Privacy Portal.
- **Google AI**: API tier biasanya 0-day retention with Vertex AI.

### 6.5 Opt-out cloud LLM — full local mode

**Hard requirement**: ChibiClaw harus tetap **fungsional secara substantif** dalam local-only mode dengan Gemma 4 E4B. Kalau cloud LLM jadi necessary path, maka opt-in ke cloud bukan benar-benar voluntary (Pasal 20 sah-consent).

Architecture:
- **Local tier** (Gemma 4 E4B + LiteRT-LM): NLU, intent classification, simple Q&A, voice command parsing.
- **Cloud tier** (opt-in): complex reasoning, long-context, vision grounding fallback, knowledge retrieval.
- Routing: tampilkan ke user **sebelum** transmit cloud (banner "Query ini akan dikirim ke Claude di server AS — lanjut?").

---

## 7. Audit Log Implementation

### 7.1 Schema (Room DB)

```kotlin
@Entity(tableName = "audit_log")
data class AuditLogEntry(
  @PrimaryKey val actionId: String, // UUID
  val timestamp: Long,              // epoch millis
  val actionType: String,           // "voice_command", "contact_access", 
                                    // "cloud_transmit", "screenshot", 
                                    // "notif_read", "accessibility_action"
  val dataType: String,             // "audio", "contact", "location",
                                    // "sms", "screenshot", "notif_content"
  val dataSummary: String,          // redacted, e.g. "contact:[CONTACT_1]"
                                    // never store raw PII
  val cloudDestination: String?,    // null kalau local; 
                                    // else: "anthropic.us-west-2", 
                                    //       "openai.eu", 
                                    //       "google.asia-southeast2"
  val consentSnapshot: String,      // JSON snapshot of consent state 
                                    // at time of action
  val resultStatus: String,         // "success", "denied", "error"
  val durationMs: Long
)
```

### 7.2 Encryption at rest

- **SQLCipher** untuk Room DB — passphrase derived dari Android Keystore-backed key (StrongBox kalau available di Pixel/Samsung flagship).
- Atau alternatif: **Room + EncryptedFile** dari Android Security library, key via `MasterKey.Builder(AES256_GCM)`.
- Audit log JANGAN dibackup ke Google Drive Auto Backup (`android:allowBackup="false"` + `android:fullBackupContent` exclude list).

### 7.3 Retention policy

- **Default 90 hari** auto-rotate (delete entries older than 90d via WorkManager periodic job, daily 03:00 local time).
- **User configurable**: 7d / 30d / 90d / 180d / 1y.
- **Force keep certain types**: cloud transmission events bisa di-flag "keep until explicit delete" untuk akuntabilitas.

### 7.4 Export to user (CSV/JSON)

UI: Settings → Data & Privacy → Export → pilih format → email atau save ke Documents (with SAF).

Output JSON schema:
```json
{
  "exported_at": "2026-05-13T14:30:00+07:00",
  "user_id_hash": "sha256:...",
  "entries": [
    {
      "action_id": "...",
      "timestamp": "...",
      "action_type": "...",
      "data_type": "...",
      "data_summary": "[REDACTED for export]",
      "cloud_destination": "...",
      "consent_at_time": {...},
      "result_status": "...",
      "duration_ms": 0
    }
  ],
  "summary": {
    "total_actions": 0,
    "actions_by_type": {...},
    "cloud_transmissions": 0
  }
}
```

### 7.5 Backup secure

JANGAN ikutkan ke Android Auto Backup. Untuk user yang mau pindah device:
- Export → encrypted ZIP dengan passphrase user → SAF save.
- Import di device baru: pilih file → verify passphrase → restore.

---

## 8. AI-Specific Compliance (EU AI Act + ISO 42001)

### 8.1 Transparency (EU AI Act Art. 50)

Apa yang ChibiClaw harus implement kalau target EU (atau best-practice global):

1. **"You are talking to AI" disclosure** at first interaction setiap session.
2. **Watermarking output audio Fuu** — inaudible signal atau metadata header tag (`generator: "ChibiClaw-Fuu-v4"`).
3. **Synthetic content flag** di every TTS file kalau di-save oleh user.

### 8.2 ISO/IEC 42001:2023 — AI Management System

Reference: [ISO 42001](https://www.iso.org/standard/42001), [Microsoft ISO 42001 offering](https://learn.microsoft.com/en-us/compliance/regulatory/offering-iso-42001), [Lorikeet Deep Dive 2026](https://lorikeetsecurity.com/blog/iso-42001-ai-management-system-2026).

**Relevance untuk ChibiClaw**: voluntary; **tidak wajib personal project**. Tapi:
- Cover ~70% requirement EU AI Act high-risk → fastest path to AI Act conformance.
- Plan-Do-Check-Act framework (PDCA) cocok jadi compliance scaffold.
- Klausul utama (10 klausul + Annex A controls): AI policy, risk assessment, lifecycle, data quality, transparency, monitoring.

**Phase 3 ChibiClaw kalau scale**: ambil sertifikasi via DNV, BSI, A-LIGN, Schellman, atau KPMG. Estimasi biaya audit ~USD 15k-50k tergantung scope.

### 8.3 Klasifikasi risk tier (estimasi)

ChibiClaw umum (productivity assistant + Fuu companion):
- **EU AI Act**: **Limited risk** — wajib transparency disclosure + watermark.
- Kalau ada fitur biometric authentication (voice unlock): **bisa naik ke high-risk**.

ChibiClaw + integrasi banking automation:
- **High-risk** by default (financial decision automation).
- Wajib: conformity assessment, technical documentation, human oversight, accuracy testing, post-market monitoring.

---

## 9. Sumber Dokumen Resmi (Reference)

### 9.1 Hukum positif Indonesia
- **UU No. 27/2022 PDP** — [JDIH BPK RI](https://peraturan.bpk.go.id/Details/229798/uu-no-27-tahun-2022) | [JDIH Kemkomdigi](https://jdih.komdigi.go.id/produk_hukum/view/id/832/t/undangundang+nomor+27+tahun+2022) | [Hukumonline PDF](https://learning.hukumonline.com/wp-content/uploads/2023/07/Undang-Undang-No.27-Tahun-2022-Hukumonline.pdf)
- **Permenkominfo 20/2016 Perlindungan Data Pribadi dalam SE** — [JDIH BPK](https://peraturan.bpk.go.id/Home/Details/150543/permenkominfo-no-20-tahun-2016)
- **Permenkominfo 5/2020 + 10/2021 PSE Privat** — [pse.komdigi.go.id panduan](https://pse.komdigi.go.id/panduan)
- **PP PDP** — **belum disahkan per Mei 2026** (pantau [pdp.id RPP tracker](https://pdp.id/rpp-ppdp/1))

### 9.2 OJK & BI
- **OJK AI Governance Banking April 2025** — [ojk.go.id](https://ojk.go.id/en/Publikasi/Roadmap-dan-Pedoman/Perbankan/Pages/Indonesia-Artificial-Intelligence-Governance-for-Banking.aspx)
- **POJK 11/POJK.03/2022** — Penyelenggaraan TI Bank Umum
- **SEOJK 29/SEOJK.03/2022** — Ketahanan & Keamanan Siber Bank
- **PBI No. 3/2025 QRIS** — [bi.go.id QRIS](https://www.bi.go.id/en/fungsi-utama/sistem-pembayaran/ritel/kanal-layanan/qris/default.aspx)
- **PBI No. 10/2025** — Activity-Based Licensing PJP

### 9.3 BSSN
- **Website resmi** — [bssn.go.id publikasi](https://bssn.go.id/publikasi/)
- **Lanskap Keamanan Siber 2023** (latest public) — [bssn.go.id PDF](https://www.bssn.go.id/wp-content/uploads/2024/03/Lanskap-Keamanan-Siber-Indonesia-2023.pdf)
- **Panduan AI security** — pantau publikasi periodik di bssn.go.id

### 9.4 Internasional
- **EU AI Act Article 50** — [artificialintelligenceact.eu](https://artificialintelligenceact.eu/article/50/)
- **EU AI Act Code of Practice AI content** — [digital-strategy.ec.europa.eu](https://digital-strategy.ec.europa.eu/en/policies/code-practice-ai-generated-content)
- **ISO/IEC 42001:2023** — [iso.org](https://www.iso.org/standard/42001)
- **GDPR official** — [gdpr.eu](https://gdpr.eu/)
- **Singapore PDPA** — [pdpc.gov.sg](https://www.pdpc.gov.sg/)

### 9.5 Praktisi & blog hukum
- **Hukumonline** — [Klinik UU PDP](https://www.hukumonline.com/klinik/a/uu-pdp--landasan-hukum-pelindungan-data-pribadi-lt5d588c1cc649e/)
- **BP Lawyers** — [UU PDP Pengaturan](https://bplawyers.co.id/2022/11/15/uu-pdp-berlaku-ini-isi-pengaturan-perlindungan-data-pribadi-di-indonesia/)
- **APPDI** (Asosiasi Praktisi Pelindungan Data Indonesia) — [appdi.or.id](https://appdi.or.id/memahami-peran-data-protection-officer-dalam-ekosistem-pelindungan-data-pribadi/)
- **ITGID** — [Sanksi UU PDP](https://itgid.org/insight/artikel-it/sanksi-uu-pdp-analisis-risiko-mitigasi-denda-maksimal/)
- **Prolegal** — [Putusan MK DPO](https://prolegal.id/mk-kabulkan-uji-materi-aturan-pelindungan-data-pribadi-penunjukan-dpo-jadi-lebih-ketat/)
- **Periskop** — [Pasal 56 Cross-Border](https://periskop.id/artikel/20250723/membedah-aturan-transfer-data-ke-luar-negeri-dalam-uu-pdp)
- **Dentons HPRP** — [OJK AI Banking Overview](https://dentons.hprplawyers.com/en/insights/articles/2025/september/2/smarter-banks-safer-systems-an-overview-of-ojks-artificial-intelligence)

### 9.6 Studi kasus kebocoran data
- **PDNS 2024 (LockBit ransomware)** — [Tempo PDNS kronologi](https://www.tempo.co/hukum/kronologi-dugaan-korupsi-pusat-data-nasional-sementara-1543851) | [LK2 FHUI Pembobolan PDN](https://lk2fhui.law.ui.ac.id/portfolio/pembobolan-pusat-data-nasional-pembelajaran-pemerintah-dalam-penguatan-keamanan-perlindungan-data-nasional/)
- **BSI ransomware 2023** — referensi di [Tempo polemik data](https://www.tempo.co/digital/polemik-data-pribadi-5-kasus-kebocoran-data-di-indonesia-selama-2023-2024-2052924)
- **Tanggung jawab pemerintah** — [Hukumonline Klinik](https://www.hukumonline.com/klinik/a/tanggung-jawab-pemerintah-atas-kebocoran-data-pribadi-lt66881c826cc33/)

---

## 10. Roadmap Compliance ChibiClaw — Phased

### Phase 1: Personal Project (sekarang, 1-3 user developer)

**Scope**: ChibiClaw di device Lendra + 1-2 dev. Tidak distribusi. Test internal.

**Bare minimum compliance**:
- [ ] Privacy notice **draft v0.1** ditampilkan first launch (link ke MD file di repo OK untuk fase ini).
- [ ] Opt-in audio recording toggle di Settings (default OFF kalau dev mode).
- [ ] No cloud LLM transmission tanpa eksplisit dev flag.
- [ ] Audit log basic di Room (skema 7.1) — bisa simple insert, belum perlu encryption.
- [ ] `android:allowBackup="false"` di manifest.
- [ ] README disclaimer: "Personal project, not for distribution".

**Effort**: ~1-2 hari dev. **Biaya**: Rp 0.

### Phase 2: Beta Closed (5-10 tester via Play Store Internal Testing)

**Scope**: tester signed up dengan email, NDA opsional.

**Compliance additions**:
- [ ] Privacy notice v1.0 ditulis advokat-aware (review minimal oleh APPDI member kalau ada akses — biaya konsultasi sekali ~Rp 3-7 juta).
- [ ] Granular consent flow first-launch (Tier 1-2-3 sesuai 6.2).
- [ ] Audit log dengan SQLCipher + retention 90d.
- [ ] Data export (JSON) functional.
- [ ] Right to delete functional (local + cloud kalau pernah sync).
- [ ] Tester agreement explicit consent untuk Data Spesifik (voice biometrik).
- [ ] DPIA internal draft (Pasal 28) — assess risk per data type.
- [ ] **JANGAN daftar PSE Privat** kalau bisa di-argument "private testing closed group" — siap-siap diminta daftar kalau open testing.

**Effort**: ~2-3 minggu. **Biaya**: Rp 5-10 juta (konsultasi awal + tooling).

### Phase 3: Public Stable di Play Store

**Scope**: 1k+ user publik, monetisasi (freemium / IAP).

**Compliance full stack**:
- [ ] **Badan hukum** terbentuk (CV/PT) — Rp 5-15 juta notaris + biaya OSS.
- [ ] **NIB + KBLI** — gratis tapi proses 2-4 minggu.
- [ ] **PSE Privat daftar** ke pse.komdigi.go.id — gratis tapi siapkan dokumen (IT security policy, ToS, Privacy Policy final, struktur tim).
- [ ] **DPO designation** kalau threshold "skala besar" tercapai — bisa internal full-time (gaji Rp 15-40 juta/bulan jika certified) atau outsourced (Rp 5-15 juta/bulan).
- [ ] **Privacy notice v2.0** final review advokat data privacy (~Rp 10-25 juta drafting + review).
- [ ] **DPIA formal** untuk setiap fitur Data Spesifik (voice biometric, vision-first).
- [ ] **Data Processing Agreement (DPA)** dengan cloud LLM provider (Anthropic/OpenAI/Google) — SCC standard sudah cover, tapi verifikasi.
- [ ] **Incident response plan** dokumentasi + drill — siap 3x24 jam breach notification.
- [ ] **Transparency report tahunan** — terutama kalau ada user request data dari pemerintah.
- [ ] **EU AI Act Art. 50 compliance** kalau target EU — watermarking audio, "I am AI" disclosure.
- [ ] **ISO 42001 certification** opsional (~USD 15-50k) — boost trust untuk B2B.
- [ ] **Cyber insurance** — Rp 20-100 juta/tahun premi tergantung skala.

**Estimate total Phase 3 setup**: Rp 60-150 juta upfront + Rp 30-80 juta/tahun running (DPO outsourced + audit + insurance).

**Effort**: 2-4 bulan dengan dedicated person.

---

## 11. Action Items Prioritas Tertinggi (Quick Wins)

Untuk sekarang (Phase 1 menuju Phase 2):

1. **Buat privacy notice draft sekarang** (sebelum tester pertama install) — pakai template Section 6.1.
2. **Implement audit log schema 7.1** segera di Room DB — bahkan kalau Phase 1, ini fondasi tidak bisa ditambal belakangan.
3. **Default cloud LLM OFF** — full local Gemma 4 dulu, cloud opt-in dengan disclosure jelas.
4. **`android:allowBackup="false"`** di manifest — satu baris, jutaan masalah dihindari.
5. **Wake-word detection lokal**, audio buffer in-RAM only — design decision ini menentukan apakah ChibiClaw fundamentally "privacy-safe" atau "privacy-leaky".
6. **PII redaction layer** sebelum apa pun transmit cloud — biasakan dari Phase 1 supaya jadi reflex.
7. **Mulai konsultasi 1-2 advokat data privacy** untuk awareness (tidak perlu retainer dulu) — APPDI member directory di [appdi.or.id](https://appdi.or.id/).

---

## 12. Open Questions untuk Konsultasi Advokat

Catatan ini dibawa ke advokat data privacy nanti:

1. Apakah voice biometric features (Fuu voice fingerprint dari wake word) **eksplisit** termasuk Data Spesifik Pasal 4 ayat 2 UU PDP? Bagaimana praktik intepretasi APPDI?
2. Threshold "skala besar" untuk Pasal 53 (DPO wajib) — apakah ada angka praktis (10k user? 100k user?) yang diterima Lembaga PDP?
3. Personal project di Play Store sebelum bentuk badan hukum — apakah PSE Privat tetap wajib? Atau ada waiver UMK?
4. Konteks notifikasi chat dari pihak ketiga (chat WA dari Budi yang masuk ke device user) — apakah ChibiClaw dianggap memproses data pribadi Budi tanpa consent Budi? Bagaimana mitigasi praktis?
5. Cloud LLM dengan Anthropic API (data ke us-west-2) — SCC standard cukup? Atau perlu explicit consent dengan disclosure?
6. Status PP PDP terkini per akses advokat — apakah ada draft yang sudah circulating publik?
7. Voice clone Fuu (kalau di-train pakai aktris dub): ToS dan lisensi voice yang aman untuk distribusi komersial.
8. Hard-deletion cloud LLM provider logs — bagaimana defensible documentation kalau user lakukan right to be forgotten?

---

## Closing Notes

ChibiClaw adalah AI assistant yang sangat menarik dari sudut teknis, tapi **regulatory surface area-nya sangat lebar**. UU PDP 27/2022 + EU AI Act + ISO 42001 + OJK AI banking + Permenkominfo PSE membentuk lattice compliance yang tidak bisa di-skip kalau target serious distribution.

Strategi **defensive-by-default**: opt-in granular, local-first, audit log dari Phase 1, full transparency. Strategi ini cost-effective karena tidak perlu refactor besar di Phase 3.

> **Catatan akhir**: dokumen ini akan **basi dalam 6-12 bulan** ketika PP PDP terbit dan Lembaga PDP penuh operasional. Update wajib begitu peraturan turun. Pantau:
> - [pdp.id](https://www.pdp.id/) untuk RPP tracker.
> - JDIH Kemkomdigi untuk Permen baru.
> - APPDI newsletter untuk praktik berkembang.

**Confidence final**: medium overall. High untuk pasal yang dirujuk eksplisit; low untuk threshold operasional yang belum di-PP. Konsultasi advokat tetap diperlukan untuk eksekusi.

---

*Last updated: 2026-05-13 — research agent (Claude Opus 4.7)*
