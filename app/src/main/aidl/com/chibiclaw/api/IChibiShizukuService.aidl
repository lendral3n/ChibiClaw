// IChibiShizukuService.aidl — Phase 3 UserService di-bind via Shizuku
//
// UserService berjalan di proses Shizuku (UID 2000 ADB / UID 0 Sui), bisa
// eksekusi shell command tanpa ChibiClaw process punya akses tersebut.
//
// Pattern referensi: rikka.shizuku.demo `IUserService` + binder bind/unbind
// via `Shizuku.bindUserService(...)`.

package com.chibiclaw.api;

interface IChibiShizukuService {
    /**
     * Execute shell command, return stdout as string. Blocking call.
     * @param command shell command (mis. "am force-stop com.spotify.music")
     * @param timeoutMs max time wait sebelum return (0 = no timeout)
     * @return stdout combined dengan stderr (lebih simple untuk parsing LLM)
     */
    String exec(String command, long timeoutMs) = 1;

    /**
     * Shutdown user service. Dipanggil di adapter close.
     */
    void destroy() = 16777114; // Shizuku reserved id untuk destroy
}
