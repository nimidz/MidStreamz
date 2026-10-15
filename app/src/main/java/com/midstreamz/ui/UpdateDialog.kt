package com.midstreamz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.midstreamz.manager.AppUpdateManager
import com.midstreamz.manager.DownloadState
import com.midstreamz.manager.UpdateInfo
import com.midstreamz.ui.theme.*
import java.util.Locale

@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    appUpdateManager: AppUpdateManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadState by appUpdateManager.downloadState.collectAsState()
    val isPermissionNeeded by appUpdateManager.isPermissionNeeded.collectAsState()
    var snoozeChecked by remember { mutableStateOf(false) }
    val currentVersion = remember { appUpdateManager.getCurrentVersionName() }

    val isDownloading = downloadState is DownloadState.Downloading
    val isCompleted = downloadState is DownloadState.Completed
    val isError = downloadState is DownloadState.Error

    Dialog(
        onDismissRequest = {
            if (!isDownloading) {
                if (snoozeChecked) {
                    appUpdateManager.snoozeUpdateFor7Days(updateInfo.versionName)
                }
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header with Badge & Version
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(PrimaryBlue.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update tersedia ${updateInfo.tagName}",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Versi saat ini: v$currentVersion",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = BorderDark.copy(alpha = 0.5f))

                // 2. Changelog / List Update Title & Content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "List Update/Perbaikan",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Changelog Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgDark)
                            .border(1.dp, BorderDark.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes.ifBlank { "Pembaruan versi terbaru untuk meningkatkan performa dan stabilitas streaming." },
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }

                // 3. Download Progress or Status Indicator
                AnimatedVisibility(
                    visible = isDownloading || isCompleted || isError,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgDark)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (val state = downloadState) {
                            is DownloadState.Downloading -> {
                                val percent = (state.progress * 100).toInt().coerceIn(0, 100)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mengunduh... $percent%",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${formatFileSize(state.bytesDownloaded)} / ${formatFileSize(state.totalBytes)}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = PrimaryBlue,
                                    trackColor = PrimaryBlue.copy(alpha = 0.2f),
                                )
                            }
                            is DownloadState.Completed -> {
                                if (isPermissionNeeded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = AmberSuperChat,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Izin Pemasangan Diperlukan",
                                                color = AmberSuperChat,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "Aktifkan izin 'Install aplikasi tidak dikenal' untuk MidStreamz, lalu kembali ke aplikasi untuk memasang otomatis.",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                        Button(
                                            onClick = { appUpdateManager.openInstallPermissionSettings() },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberSuperChat)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Aktifkan Izin di Pengaturan",
                                                color = Color.Black,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Download selesai. Memulai pemasangan...",
                                            color = EmeraldSuccess,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            is DownloadState.Error -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = RoseError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Gagal mengunduh: ${state.message}",
                                        color = RoseError,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            DownloadState.Idle -> {}
                        }
                    }
                }

                // 4. Checkbox: 'Jangan beritahu saya lagi selama 7 hari kedepan'
                if (!isDownloading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                snoozeChecked = !snoozeChecked
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = snoozeChecked,
                            onCheckedChange = { snoozeChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryBlue,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Jangan beritahu saya lagi selama 7 hari kedepan",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // 5. Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isDownloading) {
                        // [ Update & Install ]
                        Button(
                            onClick = {
                                if (isCompleted && appUpdateManager.pendingInstallApk != null) {
                                    if (isPermissionNeeded) {
                                        appUpdateManager.openInstallPermissionSettings()
                                    } else {
                                        appUpdateManager.installApk(context, appUpdateManager.pendingInstallApk!!)
                                    }
                                } else {
                                    appUpdateManager.startDownload(updateInfo)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(
                                imageVector = if (isCompleted) Icons.Default.InstallMobile else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCompleted) "Pasang Sekarang" else "Update & Install",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Row: [ Update Manual ] and [ Update Later ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // [ Update Manual ]
                            OutlinedButton(
                                onClick = {
                                    appUpdateManager.openManualReleasePage(updateInfo.htmlUrl)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Update Manual",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // [ Update Later ]
                            TextButton(
                                onClick = {
                                    if (snoozeChecked) {
                                        appUpdateManager.snoozeUpdateFor7Days(updateInfo.versionName)
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "Update Later",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // While downloading, show a subtle cancel button
                        TextButton(
                            onClick = { onDismiss() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Lanjutkan di Latar Belakang",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format bytes to readable string (e.g. 14.2 MB)
 */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format(Locale.US, "%.1f MB", mb)
}
