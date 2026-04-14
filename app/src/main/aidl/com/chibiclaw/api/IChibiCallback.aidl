// IChibiCallback.aidl
package com.chibiclaw.api;

interface IChibiCallback {
    void onStateChanged(String state);
    void onResponse(String message);
    void onError(String error);
    void onConfirmationRequired(String question, String requestId);
}
