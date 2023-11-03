/*
 * Copyright 2022 Stream.IO, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package io.getstream.avatarview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.annotation.Px
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import io.getstream.avatarview.internal.InternalAvatarViewApi
import io.getstream.avatarview.internal.arrayPositions
import io.getstream.avatarview.internal.dp
import io.getstream.avatarview.internal.getEnum
import io.getstream.avatarview.internal.getIntArray
import io.getstream.avatarview.internal.internalBlue
import io.getstream.avatarview.internal.internalGreen
import io.getstream.avatarview.internal.isRtlLayout
import io.getstream.avatarview.internal.parseInitials
import io.getstream.avatarview.internal.use
import io.getstream.avatarview.internal.viewProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * AvatarView supports segmented style images, borders, indicators, and initials.
 */
public class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr), CoroutineScope {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val indicatorOutlinePaint = Paint().apply { style = Paint.Style.FILL }
    private val indicatorPaint = Paint().apply { style = Paint.Style.FILL }
    private val backgroundPaint = Paint().apply { style = Paint.Style.FILL }
    private val textPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    /** The border width of AvatarView. */
    @get:Px
    public var avatarBorderWidth: Int by viewProperty(3.dp)

    /** The border color of AvatarView. */
    @get:ColorInt
    public var avatarBorderColor: Int by viewProperty(Color.WHITE)

    /** The border color array of AvatarView. */
    public var avatarBorderColorArray: IntArray by viewProperty(intArrayOf())

    /** The border radius of AvatarView. */
    @get:Px
    public var avatarBorderRadius: Float by viewProperty(6.dp.toFloat())

    /** The shape of the AvatarView. */
    public var avatarShape: AvatarShape by viewProperty(AvatarShape.CIRCLE)

    /** The initials to be drawn instead of an image. */
    public var avatarInitials: String? by viewProperty(null)

    /** The text size of the initials. */
    @get:Px
    public var avatarInitialsTextSize: Int by viewProperty(-1)

    /** Sets the indicator drawable on border position */
    public var isAvatarIndicatorDrawableOnBorderPosition: Boolean by viewProperty(false)

    /** The text size ratio of the initials. */
    @get:FloatRange(from = 0.0, to = 1.0)
    public var avatarInitialsTextSizeRatio: Float by viewProperty(0.33f)

    /** The text color of the initials. */
    @get:ColorInt
    public var avatarInitialsTextColor: Int by viewProperty(Color.WHITE)

    /** The text styles color of the initials. */
    public var avatarInitialsStyle: Int by viewProperty(Typeface.NORMAL)

    /** The background color of the initials. */
    @get:ColorInt
    public var avatarInitialsBackgroundColor: Int by viewProperty(internalBlue)

    /** Sets the visibility of the indicator. */
    public var indicatorEnabled: Boolean by viewProperty(false)

    /** The position of the indicator. */
    public var indicatorPosition: IndicatorPosition by viewProperty(IndicatorPosition.TOP_RIGHT)

    /** The color of the indicator. */
    @get:ColorInt
    public var indicatorColor: Int by viewProperty(internalGreen)

    /** The border color of the indicator. */
    @get:ColorInt
    public var indicatorBorderColor: Int by viewProperty(Color.WHITE)

    /** The border color array of the indicator. */
    public var indicatorBorderColorArray: IntArray by viewProperty(intArrayOf())

    /** The width size criteria of the indicator drawable. */
    public var indicatorDrawableWidthCriteria: Float by viewProperty(7f)

    /** The height size criteria of the indicator drawable. */
    public var indicatorDrawableHeightCriteria: Float by viewProperty(7f)

    /** The size criteria of the indicator. */
    public var indicatorSizeCriteria: Float by viewProperty(8f)

    /** The border size criteria of the indicator. This must be bigger than the [indicatorSizeCriteria]. */
    public var indicatorBorderSizeCriteria: Float by viewProperty(10f)

    /** A custom indicator view. */
    public var indicatorDrawable: Drawable? by viewProperty(null)

    /** Supports RTL layout is enabled or not. */
    public var supportRtlEnabled: Boolean by viewProperty(true)

    /** A placeholder that should be shown when loading an image. */
    public var placeholder: Drawable? by viewProperty(null)

    /** An error placeholder that should be shown when request failed. */
    public var errorPlaceholder: Drawable? by viewProperty(null)

    /**
     * The maximum section size of the avatar when loading multiple images.
     * This size must between 1 and 4.
     */
    public var maxSectionSize: Int = 4
        set(value) {
            field = value.coerceIn(1..4)
        }

    /** Internal coroutine scope for AvatarView. */
    @property:InternalAvatarViewApi
    public override val coroutineContext: CoroutineContext =
        SupervisorJob() + Dispatchers.Main

    init {
        initAttributes(attrs, defStyleAttr)
    }

    private fun initAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        attrs ?: return
        context.obtainStyledAttributes(attrs, R.styleable.AvatarView, defStyleAttr, 0)
            .use { typedArray ->
                avatarBorderWidth = typedArray.getDimensionPixelSize(
                    R.styleable.AvatarView_avatarViewBorderWidth, avatarBorderWidth
                )
                isAvatarIndicatorDrawableOnBorderPosition = typedArray.getBoolean(
                    R.styleable.AvatarView_isAvatarViewIndicatorDrawableOnBorderPosition, isAvatarIndicatorDrawableOnBorderPosition
                )
                avatarBorderColor = typedArray.getColor(
                    R.styleable.AvatarView_avatarViewBorderColor, avatarBorderColor
                )
                avatarBorderColorArray = typedArray.getIntArray(
                    R.styleable.AvatarView_avatarViewBorderColorArray, intArrayOf()
                )
                avatarBorderRadius = typedArray.getDimension(
                    R.styleable.AvatarView_avatarViewBorderRadius,
                    avatarBorderRadius
                )
                avatarInitials = typedArray.getString(
                    R.styleable.AvatarView_avatarViewInitials
                )
                avatarInitialsTextSize = typedArray.getDimensionPixelSize(
                    R.styleable.AvatarView_avatarViewInitialsTextSize,
                    avatarInitialsTextSize
                )
                avatarInitialsTextSizeRatio = typedArray.getFloat(
                    R.styleable.AvatarView_avatarViewInitialsTextSizeRatio,
                    avatarInitialsTextSizeRatio
                )
                avatarInitialsTextColor = typedArray.getColor(
                    R.styleable.AvatarView_avatarViewInitialsTextColor,
                    avatarInitialsTextColor
                )
                avatarInitialsBackgroundColor = typedArray.getColor(
                    R.styleable.AvatarView_avatarViewInitialsBackgroundColor,
                    avatarInitialsBackgroundColor
                )
                avatarInitialsStyle = typedArray.getInt(
                    R.styleable.AvatarView_avatarViewInitialsTextStyle,
                    avatarInitialsStyle
                )
                avatarShape = typedArray.getEnum(
                    R.styleable.AvatarView_avatarViewShape,
                    avatarShape
                )
                indicatorEnabled = typedArray.getBoolean(
                    R.styleable.AvatarView_avatarViewIndicatorEnabled,
                    indicatorEnabled
                )
                indicatorPosition = typedArray.getEnum(
                    R.styleable.AvatarView_avatarViewIndicatorPosition,
                    indicatorPosition
                )
                indicatorColor = typedArray.getColor(
                    R.styleable.AvatarView_avatarViewIndicatorColor, indicatorColor
                )
                indicatorBorderColor = typedArray.getColor(
                    R.styleable.AvatarView_avatarViewIndicatorBorderColor,
                    indicatorBorderColor
                )
                indicatorBorderColorArray = typedArray.getIntArray(
                    R.styleable.AvatarView_avatarViewIndicatorBorderColorArray,
                    indicatorBorderColorArray
                )
                indicatorSizeCriteria = typedArray.getFloat(
                    R.styleable.AvatarView_avatarViewIndicatorSizeCriteria,
                    indicatorSizeCriteria
                )
                indicatorDrawableWidthCriteria = typedArray.getFloat(
                    R.styleable.AvatarView_avatarViewIndicatorDrawableWidthCriteria,
                    indicatorDrawableWidthCriteria
                )
                indicatorDrawableHeightCriteria = typedArray.getFloat(
                    R.styleable.AvatarView_avatarViewIndicatorDrawableHeightCriteria,
                    indicatorDrawableHeightCriteria
                )
                indicatorBorderSizeCriteria = typedArray.getFloat(
                    R.styleable.AvatarView_avatarViewIndicatorBorderSizeCriteria,
                    indicatorBorderSizeCriteria
                )
                indicatorDrawable = typedArray.getDrawable(
                    R.styleable.AvatarView_avatarViewIndicatorDrawable
                )
                supportRtlEnabled = typedArray.getBoolean(
                    R.styleable.AvatarView_avatarViewSupportRtlEnabled,
                    supportRtlEnabled
                )
                maxSectionSize = typedArray.getInt(
                    R.styleable.AvatarView_avatarViewMaxSectionSize,
                    maxSectionSize
                )
                placeholder = typedArray.getDrawable(
                    R.styleable.AvatarView_avatarViewPlaceholder
                )
                errorPlaceholder = typedArray.getDrawable(
                    R.styleable.AvatarView_avatarViewErrorPlaceholder
                )
            }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(0, widthMeasureSpec)
        val height = resolveSize(0, heightMeasureSpec)
        val avatarViewSize = width.coerceAtMost(height)
        setMeasuredDimension(avatarViewSize, avatarViewSize)
    }

    override fun onDraw(canvas: Canvas) {
        if (avatarInitials.isNullOrEmpty()) {
            if (drawable != null) {
                super.onDraw(canvas)
                applyPaintStyles()
                drawBorder(canvas)
                drawIndicator(canvas)
            }
        } else {
            applyPaintStyles()
            drawColor(canvas)
            drawInitials(canvas)
            drawBorder(canvas)
            drawIndicator(canvas)
        }
    }

    /** Applies custom attributes to [AvatarView]. */
    private fun applyPaintStyles() {
        borderPaint.color = avatarBorderColor
        borderPaint.strokeWidth = avatarBorderWidth.toFloat()
        val padding = (avatarBorderWidth - AVATAR_SIZE_EXTRA).coerceAtLeast(0)
        setPadding(padding, padding, padding, padding)
        indicatorOutlinePaint.color = indicatorBorderColor
        indicatorPaint.color = indicatorColor
        backgroundPaint.color = avatarInitialsBackgroundColor
        textPaint.color = avatarInitialsTextColor
        textPaint.typeface = Typeface.defaultFromStyle(avatarInitialsStyle)
        textPaint.textSize = avatarInitialsTextSize.takeIf { it != -1 }?.toFloat()
            ?: (avatarInitialsTextSizeRatio * width)
    }

    /** Draws a border to [AvatarView]. */
    private fun drawBorder(canvas: Canvas) {
        if (avatarBorderWidth == 0) return

        if (avatarShape == AvatarShape.ROUNDED_RECT) {
            canvas.drawRoundRect(
                BORDER_OFFSET,
                BORDER_OFFSET,
                width.toFloat() - BORDER_OFFSET,
                height.toFloat() - BORDER_OFFSET,
                avatarBorderRadius,
                avatarBorderRadius,
                borderPaint.applyGradientShader(avatarBorderColorArray, width / 2f, height / 2f)
            )
        } else {
            canvas.drawCircle(
                width / 2f,
                height / 2f,
                width / 2f - avatarBorderWidth / 2,
                borderPaint.applyGradientShader(avatarBorderColorArray, width / 2f, height / 2f)
            )
        }
    }

    /** Draws initials to [AvatarView]. */
    private fun drawInitials(canvas: Canvas) {
        val initials = avatarInitials ?: return
        canvas.drawText(
            initials.parseInitials,
            width.toFloat() / 2,
            (canvas.height / 2) - ((textPaint.descent() + textPaint.ascent()) / 2),
            textPaint
        )
    }

    /** Draws color of the initials to [AvatarView]. */
    private fun drawColor(canvas: Canvas) {
        if (avatarShape == AvatarShape.ROUNDED_RECT) {
            canvas.drawRoundRect(
                0F,
                0F,
                width.toFloat(),
                height.toFloat(),
                avatarBorderRadius,
                avatarBorderRadius,
                backgroundPaint
            )
        } else {
            canvas.drawCircle(
                width / 2f,
                height / 2f,
                width / 2f,
                backgroundPaint
            )
        }
    }

    /** Draws an indicator to [AvatarView]. */
    private fun drawIndicator(canvas: Canvas) {
        if (indicatorEnabled) {
            val isRtlEnabled = supportRtlEnabled && isRtlLayout

            val centerX = width / 2f
            val centerY = height / 2f
            val radius = width / 2f - avatarBorderWidth / 2
            val customIndicator = indicatorDrawable
            if (customIndicator != null) with(customIndicator) {
                val indicatorW = width / indicatorDrawableWidthCriteria
                val indicatorH = height / indicatorDrawableHeightCriteria
                val cx: Float = if (isAvatarIndicatorDrawableOnBorderPosition) {
                    getIndicatorPointXOnCircle(centerX, radius, isRtlEnabled) - (indicatorW / 2f)
                } else {
                    getIndicatorStartX(indicatorW, isRtlEnabled)
                }
                val cy: Float = if (isAvatarIndicatorDrawableOnBorderPosition) {
                    getIndicatorPointYOnCircle(centerY, radius) - (indicatorH / 2f)
                } else {
                    getIndicatorStartY(indicatorH)
                }
                val eX: Float = cx + indicatorW
                val eY: Float =  cy + indicatorH
                setBounds(
                    cx.toInt(),
                    cy.toInt(),
                    eX.toInt(),
                    eY.toInt()
                )
                draw(canvas)
            } else {
                val cx: Float = when (indicatorPosition) {
                    IndicatorPosition.TOP_LEFT,
                    IndicatorPosition.BOTTOM_LEFT,
                    -> if (isRtlEnabled) width - (width / indicatorSizeCriteria)
                    else width / indicatorSizeCriteria
                    IndicatorPosition.TOP_RIGHT,
                    IndicatorPosition.BOTTOM_RIGHT,
                    -> if (isRtlEnabled) width / indicatorSizeCriteria
                    else width - (width / indicatorSizeCriteria)
                }

                val cy: Float = when (indicatorPosition) {
                    IndicatorPosition.TOP_LEFT,
                    IndicatorPosition.TOP_RIGHT,
                    -> height / indicatorSizeCriteria
                    IndicatorPosition.BOTTOM_LEFT,
                    IndicatorPosition.BOTTOM_RIGHT,
                    -> height - height / indicatorSizeCriteria
                }
                canvas.drawCircle(
                    cx,
                    cy,
                    width / indicatorSizeCriteria,
                    indicatorOutlinePaint.applyGradientShader(indicatorBorderColorArray, cx, cy)
                )
                canvas.drawCircle(cx, cy, width / indicatorBorderSizeCriteria, indicatorPaint)
            }
        }
    }

    private fun getIndicatorPointXOnCircle(centerX: Float, radius: Float, isRtlEnabled: Boolean): Float {
        val angle = when(indicatorPosition) {
            IndicatorPosition.TOP_LEFT -> if (isRtlEnabled) ANGLE_OF_TURN_45 else ANGLE_OF_TURN_135
            IndicatorPosition.TOP_RIGHT -> if (isRtlEnabled) ANGLE_OF_TURN_135 else ANGLE_OF_TURN_45
            IndicatorPosition.BOTTOM_LEFT -> if (isRtlEnabled) ANGLE_OF_TURN_315 else ANGLE_OF_TURN_225
            IndicatorPosition.BOTTOM_RIGHT -> if (isRtlEnabled) ANGLE_OF_TURN_225 else ANGLE_OF_TURN_315
        }
        return centerX + (radius * cos(angle)).toFloat()
    }

    private fun getIndicatorPointYOnCircle(centerY: Float, radius: Float): Float {
        val angle = when(indicatorPosition) {
            IndicatorPosition.TOP_LEFT -> ANGLE_OF_TURN_225
            IndicatorPosition.TOP_RIGHT -> ANGLE_OF_TURN_315
            IndicatorPosition.BOTTOM_LEFT -> ANGLE_OF_TURN_135
            IndicatorPosition.BOTTOM_RIGHT -> ANGLE_OF_TURN_45
        }
        return centerY + (radius * sin(angle)).toFloat()
    }

    private fun getIndicatorStartX(indicatorW: Float, isRtlEnabled: Boolean): Float {
        return when(indicatorPosition) {
            IndicatorPosition.TOP_LEFT,
            IndicatorPosition.BOTTOM_LEFT,
            -> if (isRtlEnabled) width - indicatorW
            else 0f
            IndicatorPosition.TOP_RIGHT,
            IndicatorPosition.BOTTOM_RIGHT,
            -> if (isRtlEnabled) indicatorW
            else width - indicatorW
        }
    }

    private fun getIndicatorStartY(indicatorH: Float): Float {
        return when (indicatorPosition) {
            IndicatorPosition.TOP_LEFT,
            IndicatorPosition.TOP_RIGHT,
            -> 0f
            IndicatorPosition.BOTTOM_LEFT,
            IndicatorPosition.BOTTOM_RIGHT,
            -> height - indicatorH
        }
    }

    /** Apply gradient shader to a [Paint]. */
    private fun Paint.applyGradientShader(colorArray: IntArray, cx: Float, cy: Float): Paint =
        apply {
            if (colorArray.isNotEmpty()) {
                shader = SweepGradient(
                    cx,
                    cy,
                    colorArray,
                    colorArray.arrayPositions
                )
            }
        }

    public fun setIndicatorRes(@DrawableRes drawableRes: Int) {
        indicatorDrawable = ResourcesCompat.getDrawable(resources, drawableRes, null)
    }

    override fun onDetachedFromWindow() {
        coroutineContext.cancel()
        super.onDetachedFromWindow()
    }

    internal companion object {

        internal const val AVATAR_SIZE_EXTRA = 1

        private const val BORDER_OFFSET = 4F

        private const val ANGLE_OF_TURN_45 = 45 * (PI / 180)
        private const val ANGLE_OF_TURN_135 = 135 * (PI / 180)
        private const val ANGLE_OF_TURN_225 = 225 * (PI / 180)
        private const val ANGLE_OF_TURN_315 = 315 * (PI / 180)
    }
}
