package com.chibiclaw.ui.persona

import androidx.lifecycle.ViewModel
import com.chibiclaw.memory.pref.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PersonaEditorViewModel @Inject constructor(
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _systemPrompt = MutableStateFlow(
        securePreferences.getPersonaPrompt() ?: DEFAULT_PROMPT
    )
    val systemPrompt: StateFlow<String> = _systemPrompt

    private val _language = MutableStateFlow(
        securePreferences.getPersonaLanguage() ?: "Indonesia"
    )
    val language: StateFlow<String> = _language

    private val _tone = MutableStateFlow(
        securePreferences.getPersonaTone() ?: "Casual"
    )
    val tone: StateFlow<String> = _tone

    fun setSystemPrompt(prompt: String) { _systemPrompt.value = prompt }
    fun setLanguage(lang: String) { _language.value = lang }
    fun setTone(t: String) { _tone.value = t }

    fun savePersona() {
        securePreferences.setPersonaPrompt(_systemPrompt.value)
        securePreferences.setPersonaLanguage(_language.value)
        securePreferences.setPersonaTone(_tone.value)
    }

    fun resetToDefault() {
        _systemPrompt.value = DEFAULT_PROMPT
        _language.value = "Indonesia"
        _tone.value = "Casual"
    }

    companion object {
        private const val DEFAULT_PROMPT = """Kamu adalah Fuu, asisten virtual Android yang cerdas dan ramah.
Gunakan intent_send sebagai pilihan pertama untuk aksi yang bisa dilakukan via Intent.
Minimalis: selesaikan dengan langkah sesedikit mungkin.
Jika ragu, tanya dulu dengan ask_user."""
    }
}
