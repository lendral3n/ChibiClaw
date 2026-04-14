# 07 — State Machine

## 7 States

| State | Deskripsi | Entry Condition |
|-------|-----------|-----------------|
| `IDLE` | Tidak ada task aktif, siap menerima perintah | App start, task selesai, task dibatalkan |
| `PLANNING` | Gemma sedang reasoning dan membuat action plan | Perintah baru masuk dan lolos approval |
| `EXECUTING` | Sedang menjalankan step dari action plan | Plan siap, atau resume dari PAUSED |
| `VERIFYING` | Mengecek apakah step terakhir berhasil | Setelah step dieksekusi |
| `ERROR_RECOVERY` | Step gagal, sedang re-plan | Verification gagal |
| `WAITING_USER` | Menunggu konfirmasi/input dari user | Aksi HIGH severity, atau Gemma butuh info |
| `PAUSED` | User menekan pause, task ditunda | User interrupt |
| `COMPLETED` | Task selesai, sedang cleanup | Semua step berhasil |

## Transisi yang Valid

Lihat diagram: `diagrams/state-machine.txt`

```
IDLE → PLANNING              (perintah baru masuk)
PLANNING → EXECUTING         (plan siap)
PLANNING → WAITING_USER      (butuh info tambahan sebelum plan)
EXECUTING → VERIFYING        (step selesai dieksekusi)
EXECUTING → PAUSED           (user interrupt)
EXECUTING → WAITING_USER     (aksi HIGH severity butuh konfirmasi)
VERIFYING → EXECUTING        (step berhasil, lanjut step berikutnya)
VERIFYING → ERROR_RECOVERY   (step gagal)
VERIFYING → COMPLETED        (step terakhir berhasil)
ERROR_RECOVERY → EXECUTING   (re-plan berhasil, retry)
ERROR_RECOVERY → IDLE        (max retry exceeded, abort)
WAITING_USER → EXECUTING     (user confirm / provide info)
WAITING_USER → IDLE          (user cancel)
PAUSED → EXECUTING           (user resume)
PAUSED → IDLE                (user cancel)
COMPLETED → IDLE             (cleanup selesai)
```

## Timeout & Safety

- Setiap step di EXECUTING punya timeout **5 detik**
- Jika timeout → otomatis ke VERIFYING (dianggap gagal)
- Max **3 retry** per step di ERROR_RECOVERY
- Setelah 3 retry gagal → abort, state ke IDLE, lapor ke user
- Kill Switch bisa dipanggil dari state manapun → langsung ke IDLE
- WAITING_USER punya timeout **60 detik** → jika user tidak respond, abort
