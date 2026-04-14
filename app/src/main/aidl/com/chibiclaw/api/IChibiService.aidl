// IChibiService.aidl
package com.chibiclaw.api;

import com.chibiclaw.api.IChibiCallback;

interface IChibiService {
    void sendCommand(String jsonCommand);
    String getStatus();
    void stopCurrentTask();
    void pauseCurrentTask();
    void resumeCurrentTask();
    void registerCallback(IChibiCallback callback);
    void unregisterCallback(IChibiCallback callback);
}
