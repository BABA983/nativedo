package io.github.baba983.nativedo.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.baba983.nativedo.feature.chat.data.ChatAttachmentState
import io.github.baba983.nativedo.feature.chat.data.StagedChatAttachment
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme

/**
 * The chat composer's staged-file band: one chip per file the reader picked, above the field and
 * above the keyboard, saying what each file is and what the site has done with it.
 *
 * <p>It is a preview, not an attachment sheet: picking a file is the system picker's own job
 * ([ChatChannelFragment] opens it), and a forum article/poll sheet would offer a chat channel things
 * it cannot post. A chip carries the file's own thumbnail or document mark, the name the picker
 * reported, and its upload state; its remove button is the only action here. Nothing is sent from
 * this band - the composer's own Send button is the one write.</p>
 */
class ComposerAttachmentStrip(
    context: Context,
    private val provider: Theme.ResourcesProvider?,
) : HorizontalScrollView(context) {

    /** Called with the staged id whose remove button was pressed. */
    var onRemove: ((Long) -> Unit)? = null

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val chips = LinkedHashMap<Long, Chip>()

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(6f), AndroidUtilities.dp(8f), AndroidUtilities.dp(6f))
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    /**
     * Draws [items] in pick order, adding, updating and dropping chips in place so a progress tick
     * does not rebuild every thumbnail.
     */
    fun bind(items: List<StagedChatAttachment>) {
        val live = items.map { it.id }.toSet()
        for (id in chips.keys.toList()) {
            if (id !in live) {
                row.removeView(chips.remove(id))
            }
        }
        items.forEachIndexed { index, item ->
            val chip = chips[item.id] ?: Chip(item).also { created ->
                val at = index.coerceAtMost(row.childCount)
                row.addView(created, at, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    AndroidUtilities.dp(52f),
                ).apply {
                    rightMargin = AndroidUtilities.dp(8f)
                })
                chips[item.id] = created
            }
            chip.bind(item)
        }
        visibility = if (items.isEmpty()) GONE else VISIBLE
    }

    /** Re-reads the theme colors in place after a day/night switch. */
    fun updateColors() {
        for (chip in chips.values) {
            chip.updateColors()
        }
    }

    private inner class Chip(private val item: StagedChatAttachment) : LinearLayout(context) {

        private val thumbnail = ImageView(context)
        private val name = TextView(context)
        private val status = TextView(context)
        private val remove = ImageView(context)
        private var hasPicture = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(2f), AndroidUtilities.dp(6f))

            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP)
            thumbnail.clipToOutline = true
            addView(thumbnail, LayoutParams(AndroidUtilities.dp(40f), AndroidUtilities.dp(40f)))

            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(6f), 0)
            }
            name.apply {
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                maxWidth = AndroidUtilities.dp(150f)
                text = item.name
            }
            status.apply {
                textSize = 11f
                maxLines = 1
            }
            labels.addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            labels.addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(labels, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

            remove.apply {
                scaleType = ImageView.ScaleType.CENTER
                setImageResource(R.drawable.msg_close)
                setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
                isClickable = true
                isFocusable = true
                setOnClickListener { onRemove?.invoke(item.id) }
            }
            addView(remove, LayoutParams(AndroidUtilities.dp(32f), AndroidUtilities.dp(32f)))

            val picture = if (item.mimeType?.startsWith("image/") == true) decodeThumbnail(item) else null
            hasPicture = picture != null
            if (picture != null) {
                thumbnail.setImageBitmap(picture)
            } else {
                thumbnail.setImageResource(R.drawable.msg_filehq)
                thumbnail.scaleType = ImageView.ScaleType.CENTER
                thumbnail.setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f), AndroidUtilities.dp(8f))
            }
            bind(item)
            updateColors()
        }

        fun bind(current: StagedChatAttachment) {
            status.text = statusText(current)
            remove.contentDescription = "移除附件 ${current.name}"
            applyStatusColor(current)
        }

        private fun applyStatusColor(current: StagedChatAttachment) {
            status.setTextColor(
                Theme.getColor(
                    if (current.state == ChatAttachmentState.Failed) Theme.key_text_RedRegular
                    else Theme.key_windowBackgroundWhiteGrayText,
                    provider,
                ),
            )
        }

        fun updateColors() {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = AndroidUtilities.dp(14f).toFloat()
                setColor(Theme.getColor(Theme.key_windowBackgroundWhite, provider))
            }
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider))
            applyStatusColor(item)
            remove.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, provider))
            remove.background = Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, provider),
                Theme.RIPPLE_MASK_ALL,
            )
            if (hasPicture) {
                thumbnail.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = AndroidUtilities.dp(10f).toFloat()
                    setColor(Theme.getColor(Theme.key_windowBackgroundWhite, provider))
                }
            } else {
                thumbnail.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider))
            }
        }

        private fun statusText(item: StagedChatAttachment): String = when (item.state) {
            ChatAttachmentState.Staged -> formatSize(item.sizeBytes)
            ChatAttachmentState.Uploading -> "上传中 ${(item.progress * 100).toInt()}%"
            ChatAttachmentState.Uploaded -> "已就绪 · ${formatSize(item.sizeBytes)}"
            ChatAttachmentState.Failed -> "上传失败，发送时重试"
        }
    }

    /**
     * A small decoded copy for the chip, never the file as the site will see it: bounds first, then
     * a sampled decode, so a 12-megapixel photo does not become a 40 dp chip's bitmap.
     */
    private fun decodeThumbnail(item: StagedChatAttachment): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(item.file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMBNAIL_PX && bounds.outHeight / (sample * 2) >= THUMBNAIL_PX) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(item.file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
        })
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private companion object {
        const val THUMBNAIL_PX = 96
    }
}
