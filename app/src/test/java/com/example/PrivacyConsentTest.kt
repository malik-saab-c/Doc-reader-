package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.PrivacyConsentManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyConsentTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Reset state for isolation
        val prefs = context.getSharedPreferences("all_file_reader_user_consent_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun verifyFirstLaunchRequiresPrivacyAgreement() {
        // On very first launch, privacy policy is NOT agreed
        val isAgreedInitial = PrivacyConsentManager.isPrivacyPolicyAgreed(context)
        assertFalse("Privacy agreement must not be agreed on first launch", isAgreedInitial)
    }

    @Test
    fun verifyPrivacyPolicyAgreedOncePersistsLifetime() {
        // User accepts privacy policy on first launch
        PrivacyConsentManager.setPrivacyPolicyAgreed(context, true)

        // Verifies user is now marked as agreed
        assertTrue("Privacy policy should be marked as agreed", PrivacyConsentManager.isPrivacyPolicyAgreed(context))

        // Subsequent checks continue to return true (lifetime persistence)
        val secondCheck = PrivacyConsentManager.isPrivacyPolicyAgreed(context)
        assertTrue("Subsequent launches must remember agreement", secondCheck)
    }

    @Test
    fun verifyPrivacyPolicyConstants() {
        assertEquals("https://code-canvas-63.lovable.app/editor/6f6dd37f-9cc2-4974-8281-c830ea9e1600", PrivacyConsentManager.PRIVACY_POLICY_URL)
        assertEquals("Gm's RR company", PrivacyConsentManager.DEVELOPER_COMPANY)
        assertEquals("All file reader Genz", PrivacyConsentManager.APP_NAME)
        assertEquals("mailksahibgg108@gmail.com", PrivacyConsentManager.CONTACT_EMAIL)
    }
}
