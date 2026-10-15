package com.midstreamz.manager

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.midstreamz.model.EventType
import com.midstreamz.model.FullOverlayConfig
import com.midstreamz.model.LiveChatMessage
import com.midstreamz.model.OverlayAnimIn
import com.midstreamz.model.OverlayAnimOut
import com.midstreamz.model.OverlayAnimType
import com.midstreamz.model.OverlayBgStyle
import com.midstreamz.model.OverlayItemConfig
import com.midstreamz.model.OverlayTextStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayConfigManager(context: Context) {
    private val TAG = "OverlayConfigManager"
    private val prefs = context.getSharedPreferences("midstreamz_overlay_hud_prefs", Context.MODE_PRIVATE)

    var config: FullOverlayConfig = FullOverlayConfig()
        private set

    private val _configState = MutableStateFlow(config)
    val configState: StateFlow<FullOverlayConfig> = _configState.asStateFlow()

    init {
        loadPreferences()
    }

    fun loadPreferences() {
        try {
            val follow = OverlayItemConfig(
                isEnabled = prefs.getBoolean("follow_enabled", true),
                animIn = parseAnimIn(prefs.getString("follow_anim_in", OverlayAnimIn.FADE_IN.name)),
                animOut = parseAnimOut(prefs.getString("follow_anim_out", OverlayAnimOut.FADE_OUT.name)),
                animation = parseAnimType(prefs.getString("follow_anim", OverlayAnimType.FADE_IN.name)),
                template = prefs.getString("follow_template", "\$name telah memfollow") ?: "\$name telah memfollow",
                textColorHex = prefs.getString("follow_text_color", "#FCE7F3") ?: "#FCE7F3",
                textStyle = OverlayTextStyle.valueOf(prefs.getString("follow_text_style", OverlayTextStyle.BOLD.name) ?: OverlayTextStyle.BOLD.name),
                outlineEnabled = prefs.getBoolean("follow_outline_enabled", true),
                outlineColorHex = prefs.getString("follow_outline_color", "#000000") ?: "#000000",
                outlineWidthDp = prefs.getFloat("follow_outline_width", 2f),
                bgColorHex = prefs.getString("follow_bg_color", "#4A044E") ?: "#4A044E",
                bgOpacity = prefs.getFloat("follow_bg_opacity", 0.90f),
                bgStyle = OverlayBgStyle.valueOf(prefs.getString("follow_bg_style", OverlayBgStyle.ROUNDED.name) ?: OverlayBgStyle.ROUNDED.name),
                userColorHex = prefs.getString("follow_user_color", "#60A5FA") ?: "#60A5FA",
                moderatorColorHex = prefs.getString("follow_mod_color", "#34D399") ?: "#34D399",
                followerColorHex = prefs.getString("follow_follower_color", "#F472B6") ?: "#F472B6",
                portraitX = prefs.getInt("follow_port_x", 30),
                portraitY = prefs.getInt("follow_port_y", 140),
                landscapeX = prefs.getInt("follow_land_x", 30),
                landscapeY = prefs.getInt("follow_land_y", 40),
                scale = prefs.getFloat("follow_scale", 1.0f),
                isLocked = prefs.getBoolean("follow_locked", false)
            )

            val comment = OverlayItemConfig(
                isEnabled = prefs.getBoolean("comment_enabled", true),
                animIn = parseAnimIn(prefs.getString("comment_anim_in", OverlayAnimIn.FADE_IN.name)),
                animOut = parseAnimOut(prefs.getString("comment_anim_out", OverlayAnimOut.FADE_OUT.name)),
                animation = parseAnimType(prefs.getString("comment_anim", OverlayAnimType.FADE_IN.name)),
                template = prefs.getString("comment_template", "\$name: \$message") ?: "\$name: \$message",
                textColorHex = prefs.getString("comment_text_color", "#F8FAFC") ?: "#F8FAFC",
                textStyle = OverlayTextStyle.valueOf(prefs.getString("comment_text_style", OverlayTextStyle.BOLD.name) ?: OverlayTextStyle.BOLD.name),
                outlineEnabled = prefs.getBoolean("comment_outline_enabled", true),
                outlineColorHex = prefs.getString("comment_outline_color", "#000000") ?: "#000000",
                outlineWidthDp = prefs.getFloat("comment_outline_width", 2f),
                bgColorHex = prefs.getString("comment_bg_color", "#0F172A") ?: "#0F172A",
                bgOpacity = prefs.getFloat("comment_bg_opacity", 0.85f),
                bgStyle = OverlayBgStyle.valueOf(prefs.getString("comment_bg_style", OverlayBgStyle.ROUNDED.name) ?: OverlayBgStyle.ROUNDED.name),
                userColorHex = prefs.getString("comment_user_color", "#60A5FA") ?: "#60A5FA",
                moderatorColorHex = prefs.getString("comment_mod_color", "#34D399") ?: "#34D399",
                followerColorHex = prefs.getString("comment_follower_color", "#F472B6") ?: "#F472B6",
                portraitX = prefs.getInt("comment_port_x", 30),
                portraitY = prefs.getInt("comment_port_y", 240),
                landscapeX = prefs.getInt("comment_land_x", 30),
                landscapeY = prefs.getInt("comment_land_y", 120),
                scale = prefs.getFloat("comment_scale", 1.0f),
                isLocked = prefs.getBoolean("comment_locked", false)
            )

            val gift = OverlayItemConfig(
                isEnabled = prefs.getBoolean("gift_enabled", true),
                animIn = parseAnimIn(prefs.getString("gift_anim_in", OverlayAnimIn.FADE_IN.name)),
                animOut = parseAnimOut(prefs.getString("gift_anim_out", OverlayAnimOut.FADE_OUT.name)),
                animation = parseAnimType(prefs.getString("gift_anim", OverlayAnimType.FADE_IN.name)),
                template = prefs.getString("gift_template", "Terima kasih \$name \$gift") ?: "Terima kasih \$name \$gift",
                textColorHex = prefs.getString("gift_text_color", "#FEF3C7") ?: "#FEF3C7",
                textStyle = OverlayTextStyle.valueOf(prefs.getString("gift_text_style", OverlayTextStyle.BOLD.name) ?: OverlayTextStyle.BOLD.name),
                outlineEnabled = prefs.getBoolean("gift_outline_enabled", true),
                outlineColorHex = prefs.getString("gift_outline_color", "#000000") ?: "#000000",
                outlineWidthDp = prefs.getFloat("gift_outline_width", 2f),
                bgColorHex = prefs.getString("gift_bg_color", "#78350F") ?: "#78350F",
                bgOpacity = prefs.getFloat("gift_bg_opacity", 0.92f),
                bgStyle = OverlayBgStyle.valueOf(prefs.getString("gift_bg_style", OverlayBgStyle.ROUNDED.name) ?: OverlayBgStyle.ROUNDED.name),
                userColorHex = prefs.getString("gift_user_color", "#60A5FA") ?: "#60A5FA",
                moderatorColorHex = prefs.getString("gift_mod_color", "#34D399") ?: "#34D399",
                followerColorHex = prefs.getString("gift_follower_color", "#F472B6") ?: "#F472B6",
                portraitX = prefs.getInt("gift_port_x", 30),
                portraitY = prefs.getInt("gift_port_y", 340),
                landscapeX = prefs.getInt("gift_land_x", 30),
                landscapeY = prefs.getInt("gift_land_y", 200),
                scale = prefs.getFloat("gift_scale", 1.0f),
                isLocked = prefs.getBoolean("gift_locked", false)
            )

            val song = OverlayItemConfig(
                isEnabled = prefs.getBoolean("song_enabled", true),
                animIn = parseAnimIn(prefs.getString("song_anim_in", OverlayAnimIn.FADE_IN.name)),
                animOut = parseAnimOut(prefs.getString("song_anim_out", OverlayAnimOut.FADE_OUT.name)),
                animation = parseAnimType(prefs.getString("song_anim", OverlayAnimType.FADE_IN.name)),
                template = prefs.getString("song_template", "\$name memutar \$judul") ?: "\$name memutar \$judul",
                textColorHex = prefs.getString("song_text_color", "#E0E7FF") ?: "#E0E7FF",
                textStyle = OverlayTextStyle.valueOf(prefs.getString("song_text_style", OverlayTextStyle.BOLD.name) ?: OverlayTextStyle.BOLD.name),
                outlineEnabled = prefs.getBoolean("song_outline_enabled", true),
                outlineColorHex = prefs.getString("song_outline_color", "#000000") ?: "#000000",
                outlineWidthDp = prefs.getFloat("song_outline_width", 2f),
                bgColorHex = prefs.getString("song_bg_color", "#1E1B4B") ?: "#1E1B4B",
                bgOpacity = prefs.getFloat("song_bg_opacity", 0.90f),
                bgStyle = OverlayBgStyle.valueOf(prefs.getString("song_bg_style", OverlayBgStyle.ROUNDED.name) ?: OverlayBgStyle.ROUNDED.name),
                userColorHex = prefs.getString("song_user_color", "#60A5FA") ?: "#60A5FA",
                moderatorColorHex = prefs.getString("song_mod_color", "#34D399") ?: "#34D399",
                followerColorHex = prefs.getString("song_follower_color", "#F472B6") ?: "#F472B6",
                portraitX = prefs.getInt("song_port_x", 30),
                portraitY = prefs.getInt("song_port_y", 440),
                landscapeX = prefs.getInt("song_land_x", 30),
                landscapeY = prefs.getInt("song_land_y", 260),
                scale = prefs.getFloat("song_scale", 1.0f),
                isLocked = prefs.getBoolean("song_locked", false)
            )

            val likeEnabled = prefs.getBoolean("like_enabled", true)
            val landscapeOnly = prefs.getBoolean("landscape_only", false)
            val masterOverlayDisabled = prefs.getBoolean("master_overlay_disabled", false)
            val universalUserColor = prefs.getString("universal_user_color", "#60A5FA") ?: "#60A5FA"
            val universalModColor = prefs.getString("universal_mod_color", "#34D399") ?: "#34D399"
            val universalFollowerColor = prefs.getString("universal_follower_color", "#F472B6") ?: "#F472B6"

            config = FullOverlayConfig(
                followConfig = follow,
                commentConfig = comment,
                giftConfig = gift,
                songRequestConfig = song,
                likeOverlayEnabled = likeEnabled,
                landscapeOnly = landscapeOnly,
                masterOverlayDisabled = masterOverlayDisabled,
                universalUserColorHex = universalUserColor,
                universalModeratorColorHex = universalModColor,
                universalFollowerColorHex = universalFollowerColor
            )
            _configState.value = config
        } catch (e: Exception) {
            Log.e(TAG, "Error loading overlay prefs", e)
        }
    }

    private fun parseAnimIn(name: String?): OverlayAnimIn {
        return try {
            if (name != null) OverlayAnimIn.valueOf(name) else OverlayAnimIn.FADE_IN
        } catch (_: Exception) {
            OverlayAnimIn.FADE_IN
        }
    }

    private fun parseAnimOut(name: String?): OverlayAnimOut {
        return try {
            if (name != null) OverlayAnimOut.valueOf(name) else OverlayAnimOut.FADE_OUT
        } catch (_: Exception) {
            OverlayAnimOut.FADE_OUT
        }
    }

    private fun parseAnimType(name: String?): OverlayAnimType {
        return try {
            if (name != null) OverlayAnimType.valueOf(name) else OverlayAnimType.FADE_IN
        } catch (_: Exception) {
            OverlayAnimType.FADE_IN
        }
    }

    fun savePreferences() {
        try {
            val editor = prefs.edit()
            saveItemToEditor(editor, "follow", config.followConfig)
            saveItemToEditor(editor, "comment", config.commentConfig)
            saveItemToEditor(editor, "gift", config.giftConfig)
            saveItemToEditor(editor, "song", config.songRequestConfig)
            editor.putBoolean("like_enabled", config.likeOverlayEnabled)
            editor.putBoolean("landscape_only", config.landscapeOnly)
            editor.putBoolean("master_overlay_disabled", config.masterOverlayDisabled)
            editor.putString("universal_user_color", config.universalUserColorHex)
            editor.putString("universal_mod_color", config.universalModeratorColorHex)
            editor.putString("universal_follower_color", config.universalFollowerColorHex)
            editor.apply()
            _configState.value = config.copy()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving overlay prefs", e)
        }
    }

    private fun saveItemToEditor(editor: android.content.SharedPreferences.Editor, prefix: String, item: OverlayItemConfig) {
        editor.putBoolean("${prefix}_enabled", item.isEnabled)
        editor.putString("${prefix}_anim_in", item.animIn.name)
        editor.putString("${prefix}_anim_out", item.animOut.name)
        editor.putString("${prefix}_anim", item.animation.name)
        editor.putString("${prefix}_template", item.template)
        editor.putString("${prefix}_text_color", item.textColorHex)
        editor.putString("${prefix}_text_style", item.textStyle.name)
        editor.putBoolean("${prefix}_outline_enabled", item.outlineEnabled)
        editor.putString("${prefix}_outline_color", item.outlineColorHex)
        editor.putFloat("${prefix}_outline_width", item.outlineWidthDp)
        editor.putString("${prefix}_bg_color", item.bgColorHex)
        editor.putFloat("${prefix}_bg_opacity", item.bgOpacity)
        editor.putString("${prefix}_bg_style", item.bgStyle.name)
        editor.putString("${prefix}_user_color", item.userColorHex)
        editor.putString("${prefix}_mod_color", item.moderatorColorHex)
        editor.putString("${prefix}_follower_color", item.followerColorHex)
        editor.putInt("${prefix}_port_x", item.portraitX)
        editor.putInt("${prefix}_port_y", item.portraitY)
        editor.putInt("${prefix}_land_x", item.landscapeX)
        editor.putInt("${prefix}_land_y", item.landscapeY)
        editor.putFloat("${prefix}_scale", item.scale)
        editor.putBoolean("${prefix}_locked", item.isLocked)
    }

    fun updatePosition(prefix: String, x: Int, y: Int, isLandscape: Boolean) {
        val target = when (prefix) {
            "follow" -> config.followConfig
            "comment" -> config.commentConfig
            "gift" -> config.giftConfig
            "song" -> config.songRequestConfig
            else -> null
        } ?: return

        if (isLandscape) {
            target.landscapeX = x
            target.landscapeY = y
        } else {
            target.portraitX = x
            target.portraitY = y
        }
        savePreferences()
    }

    fun formatText(template: String, chat: LiveChatMessage): String {
        val giftName = chat.amountFormatted ?: if (chat.isTikTok) "Gift TikTok" else "Super Chat"
        return template
            .replace("\$name", chat.authorName)
            .replace("\$message", chat.message)
            .replace("\$judul", chat.message)
            .replace("\$title", chat.message)
            .replace("\$gift", giftName)
            .replace("\$amount", chat.amountFormatted ?: "")
    }

    fun getAuthorColor(itemConfig: OverlayItemConfig, chat: LiveChatMessage): Int {
        return try {
            when {
                chat.isModerator || chat.isOwner -> Color.parseColor(config.universalModeratorColorHex)
                chat.isMember || chat.type == EventType.SUBSCRIBER -> Color.parseColor(config.universalFollowerColorHex)
                else -> Color.parseColor(config.universalUserColorHex)
            }
        } catch (_: Exception) {
            Color.WHITE
        }
    }
}
