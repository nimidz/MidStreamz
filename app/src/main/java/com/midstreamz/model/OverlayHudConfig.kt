package com.midstreamz.model

enum class OverlayAnimIn(val displayName: String) {
    FADE_IN("Fadein"),
    SLIDE_RIGHT("Geser Kanan"),
    SLIDE_LEFT("Geser Kiri"),
    SLIDE_UP("Geser Atas"),
    SLIDE_DOWN("Geser Bawah")
}

enum class OverlayAnimOut(val displayName: String) {
    FADE_OUT("Memudar"),
    SLIDE_RIGHT("Geser Kanan"),
    SLIDE_LEFT("Geser Kiri"),
    SLIDE_UP("Geser Atas"),
    SLIDE_DOWN("Geser Bawah")
}

enum class OverlayAnimType(val displayName: String) {
    SLIDE_RIGHT("Geser Kanan"),
    SLIDE_LEFT("Geser Kiri"),
    FADE_IN("Fade In"),
    FADE_OUT("Fade Out")
}

enum class OverlayTextStyle(val displayName: String) {
    NORMAL("Normal"),
    BOLD("Tebal (Bold)"),
    ITALIC("Miring (Italic)"),
    BOLD_ITALIC("Tebal & Miring")
}

enum class OverlayBgStyle(val displayName: String) {
    ROUNDED("Rounded (16dp)"),
    PILL("Pill (Kapsul)"),
    SHARP("Kotak (Sharp)"),
    GLASS("Glassmorphism")
}

data class OverlayItemConfig(
    var isEnabled: Boolean = true,
    var animIn: OverlayAnimIn = OverlayAnimIn.FADE_IN,
    var animOut: OverlayAnimOut = OverlayAnimOut.FADE_OUT,
    var animation: OverlayAnimType = OverlayAnimType.FADE_IN,
    var template: String,

    // Tampilan Text
    var textColorHex: String = "#FFFFFF",
    var textStyle: OverlayTextStyle = OverlayTextStyle.BOLD,
    var outlineEnabled: Boolean = true,
    var outlineColorHex: String = "#000000",
    var outlineWidthDp: Float = 2f,

    // Latar Belakang
    var bgColorHex: String = "#0F172A",
    var bgOpacity: Float = 0.85f,
    var bgStyle: OverlayBgStyle = OverlayBgStyle.ROUNDED,

    // Warna Nama berdasarkan Role
    var userColorHex: String = "#60A5FA",
    var moderatorColorHex: String = "#34D399",
    var followerColorHex: String = "#F472B6",

    // Posisi, Skala & Kunci (Vertikal / Lanskap)
    var portraitX: Int = 30,
    var portraitY: Int = 180,
    var landscapeX: Int = 30,
    var landscapeY: Int = 60,
    var scale: Float = 1.0f,
    var isLocked: Boolean = false
)

data class FullOverlayConfig(
    var followConfig: OverlayItemConfig = OverlayItemConfig(
        animIn = OverlayAnimIn.FADE_IN,
        animOut = OverlayAnimOut.FADE_OUT,
        template = "\$name telah memfollow",
        textColorHex = "#FCE7F3",
        bgColorHex = "#4A044E",
        bgOpacity = 0.90f,
        portraitY = 140,
        landscapeY = 40
    ),
    var commentConfig: OverlayItemConfig = OverlayItemConfig(
        animIn = OverlayAnimIn.FADE_IN,
        animOut = OverlayAnimOut.FADE_OUT,
        template = "\$name: \$message",
        textColorHex = "#F8FAFC",
        bgColorHex = "#0F172A",
        bgOpacity = 0.85f,
        portraitY = 240,
        landscapeY = 120
    ),
    var giftConfig: OverlayItemConfig = OverlayItemConfig(
        animIn = OverlayAnimIn.FADE_IN,
        animOut = OverlayAnimOut.FADE_OUT,
        template = "Terima kasih \$name \$gift",
        textColorHex = "#FEF3C7",
        bgColorHex = "#78350F",
        bgOpacity = 0.92f,
        portraitY = 340,
        landscapeY = 200
    ),
    var songRequestConfig: OverlayItemConfig = OverlayItemConfig(
        animIn = OverlayAnimIn.FADE_IN,
        animOut = OverlayAnimOut.FADE_OUT,
        template = "\$name memutar \$judul",
        textColorHex = "#E0E7FF",
        bgColorHex = "#1E1B4B",
        bgOpacity = 0.90f,
        portraitY = 440,
        landscapeY = 260
    ),
    var likeOverlayEnabled: Boolean = true,
    var landscapeOnly: Boolean = false,
    var masterOverlayDisabled: Boolean = false,
    var universalUserColorHex: String = "#60A5FA",
    var universalModeratorColorHex: String = "#34D399",
    var universalFollowerColorHex: String = "#F472B6"
)
