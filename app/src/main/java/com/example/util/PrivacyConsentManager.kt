package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object PrivacyConsentManager {
    private const val PREFS_NAME = "all_file_reader_user_consent_prefs"
    private const val KEY_PRIVACY_AGREED = "key_privacy_policy_agreed_lifetime"

    const val PRIVACY_POLICY_URL = "https://code-canvas-63.lovable.app/editor/6f6dd37f-9cc2-4974-8281-c830ea9e1600"
    const val DEVELOPER_COMPANY = "Gm's RR company"
    const val APP_NAME = "All file reader Genz"
    const val CONTACT_EMAIL = "mailksahibgg108@gmail.com"

    /**
     * Checks if the user has accepted the Privacy Policy agreement.
     * Returns true if already accepted, false on the very first launch.
     */
    fun isPrivacyPolicyAgreed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PRIVACY_AGREED, false)
    }

    /**
     * Persistently marks the Privacy Policy as agreed for the complete lifetime of the app installation.
     */
    fun setPrivacyPolicyAgreed(context: Context, agreed: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PRIVACY_AGREED, agreed).apply()
    }

    /**
     * Helper to open the official online Privacy Policy URL in the browser.
     */
    fun openPrivacyPolicyWeb(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PrivacyConsentManager", "Unable to open browser intent: ${e.message}", e)
        }
    }
}
