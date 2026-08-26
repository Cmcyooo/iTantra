package com.itantra.app.comm

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the local iTantra Call Sign (friendly display name).
 * Persists the name in SharedPreferences.
 */
class CallSignManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("itantra_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_CALL_SIGN = "call_sign"
        private const val DEFAULT_CALL_SIGN = "iTantra User"
    }

    fun getCallSign(): String {
        return prefs.getString(KEY_CALL_SIGN, DEFAULT_CALL_SIGN) ?: DEFAULT_CALL_SIGN
    }

    fun saveCallSign(name: String) {
        prefs.edit().putString(KEY_CALL_SIGN, name.trim()).apply()
    }

    fun savePeerCallSign(address: String, name: String) {
        prefs.edit().putString("peer_$address", name.trim()).apply()
    }

    fun getPeerCallSign(address: String): String? {
        return prefs.getString("peer_$address", null)
    }
}
