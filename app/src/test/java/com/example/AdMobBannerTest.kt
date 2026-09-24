package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.AdMobConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdMobBannerTest {

    @Test
    fun verifyAdMobConstants() {
        assertEquals("pub-7856116751759167", AdMobConfig.PUBLISHER_ID)
        assertEquals("ca-app-pub-7856116751759167~6140452184", AdMobConfig.APP_ID)
        assertEquals("ca-app-pub-7856116751759167/1890301703", AdMobConfig.BANNER_AD_UNIT_ID)
    }

    @Test
    fun verifyAdMobAppIdInManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        assertNotNull("Application info should not be null", appInfo)
        assertNotNull("Meta-data in AndroidManifest should not be null", appInfo.metaData)

        val adMobAppId = appInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID")
        assertEquals(
            "AdMob Application ID in manifest must match exactly",
            "ca-app-pub-7856116751759167~6140452184",
            adMobAppId
        )
    }

    @Test
    fun verifyNetworkPermissionsDeclared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "INTERNET permission must be declared for AdMob ads",
            requestedPermissions.contains("android.permission.INTERNET")
        )
        assertTrue(
            "ACCESS_NETWORK_STATE permission must be declared for AdMob ads",
            requestedPermissions.contains("android.permission.ACCESS_NETWORK_STATE")
        )
        assertTrue(
            "AD_ID permission must be declared for Google Mobile Ads",
            requestedPermissions.contains("com.google.android.gms.permission.AD_ID")
        )
    }

    @Test
    fun verifyAdServicesConfigFileExists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resId = context.resources.getIdentifier("gma_ad_services_config", "xml", context.packageName)
        assertTrue("gma_ad_services_config.xml resource must exist in res/xml", resId != 0)
    }
}
