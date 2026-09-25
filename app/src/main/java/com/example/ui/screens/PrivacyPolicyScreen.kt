package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.PrivacyConsentManager

@Composable
fun PrivacyPolicyScreen(
    onAgreeAndContinue: () -> Unit
) {
    val context = LocalContext.current
    var isAgreedChecked by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("privacy_policy_screen_scaffold"),
        containerColor = PureWhite,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_bottom_bar"),
                color = PureWhite,
                shadowElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Checkbox row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isAgreedChecked = !isAgreedChecked }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAgreedChecked,
                            onCheckedChange = { isAgreedChecked = it },
                            modifier = Modifier.testTag("privacy_agreement_checkbox"),
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandAccent,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I have read and agree to the Privacy Policy and Terms of Use",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            PrivacyConsentManager.setPrivacyPolicyAgreed(context, true)
                            onAgreeAndContinue()
                        },
                        enabled = isAgreedChecked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("privacy_agree_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandAccent,
                            contentColor = PureWhite,
                            disabledContainerColor = CardBorder,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text(
                            text = "Agree & Continue to App",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Continue",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Badge & Branding
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BrandAccentLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Privacy Shield",
                    tint = BrandAccent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Privacy Policy & Agreement",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${PrivacyConsentManager.APP_NAME} by ${PrivacyConsentManager.DEVELOPER_COMPANY}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandAccent
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Please review our privacy principles. We believe in complete transparency and your fundamental right to personal data protection.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // External Link Button Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { PrivacyConsentManager.openPrivacyPolicyWeb(context) }
                    .testTag("privacy_open_web_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BrandAccentLight),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandAccent.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Web Policy",
                        tint = BrandAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "View Online Privacy Policy Page",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandAccent
                        )
                        Text(
                            text = PrivacyConsentManager.PRIVACY_POLICY_URL,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Policy Sections
            PolicyItemCard(
                icon = Icons.Default.Lock,
                title = "1. Zero Document Data Uploads",
                description = "All files (PDFs, Word documents, Excel spreadsheets, PowerPoint presentations, Text files, and Images) are processed 100% locally on your device. We do not transmit, copy, or upload any file content to external servers."
            )

            Spacer(modifier = Modifier.height(12.dp))

            PolicyItemCard(
                icon = Icons.Default.FolderShared,
                title = "2. Device Storage Permissions",
                description = "Storage access is requested strictly for the purpose of allowing you to browse, read, edit, and organize files stored on your internal or external storage. Access is limited strictly to files you choose to open or create."
            )

            Spacer(modifier = Modifier.height(12.dp))

            PolicyItemCard(
                icon = Icons.Default.Info,
                title = "3. Advertising & AdMob Disclosures",
                description = "To keep ${PrivacyConsentManager.APP_NAME} free of charge, we integrate Google AdMob (Publisher ID: pub-7856116751759167). Google AdMob may collect and process pseudonymous identifiers and device data solely for the delivery of advertisements in full compliance with Google Play and Amazon Appstore Content Policies."
            )

            Spacer(modifier = Modifier.height(12.dp))

            PolicyItemCard(
                icon = Icons.Default.Security,
                title = "4. User Consent & Revocation",
                description = "By continuing, you confirm that you have read and agree to the data practices described. You may revoke permissions at any time via Android System Settings. This agreement dialog appears only on first launch."
            )

            Spacer(modifier = Modifier.height(12.dp))

            PolicyItemCard(
                icon = Icons.Default.CheckCircle,
                title = "5. Developer Contact & Inquiries",
                description = "If you have any questions or feedback regarding this policy, please reach out to ${PrivacyConsentManager.DEVELOPER_COMPANY} at ${PrivacyConsentManager.CONTACT_EMAIL}."
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PolicyItemCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BrandAccentLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
