package com.vidgrab.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Sirf zaroori custom icons — material-icons-extended (11MB dex) ki jagah.
 * Filled 24dp Material style.
 */
object VidIcons {

    private fun build(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color(0xFF000000)),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero,
            pathBuilder = block
        )
    }

    /** Download arrow — classic material download */
    val Download: ImageVector by lazy {
        build("VidDownload") {
            fillPath {
                moveTo(19f, 9f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                lineToRelative(7f, 7f)
                lineToRelative(7f, -7f)
                close()
                moveTo(5f, 18f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    /** Clipboard — paste */
    val Paste: ImageVector by lazy {
        build("VidPaste") {
            fillPath {
                moveTo(19f, 2f)
                horizontalLineToRelative(-4.18f)
                curveTo(14.4f, 0.84f, 13.3f, 0f, 12f, 0f)
                curveToRelative(-1.3f, 0f, -2.4f, 0.84f, -2.82f, 2f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 2f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                reflectiveCurveTo(11.45f, 2f, 12f, 2f)
                close()
                moveTo(19f, 20f)
                horizontalLineTo(5f)
                verticalLineTo(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(10f)
                verticalLineTo(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(16f)
                close()
            }
        }
    }

    /** Chain link */
    val Link: ImageVector by lazy {
        build("VidLink") {
            fillPath {
                moveTo(3.9f, 12f)
                curveToRelative(0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
                horizontalLineToRelative(4f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
                reflectiveCurveToRelative(2.24f, 5f, 5f, 5f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-1.9f)
                horizontalLineTo(7f)
                curveToRelative(-1.71f, 0f, -3.1f, -1.39f, -3.1f, -3.1f)
                close()
                moveTo(8f, 13f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineTo(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(17f, 7f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(1.9f)
                horizontalLineToRelative(4f)
                curveToRelative(1.71f, 0f, 3.1f, 1.39f, 3.1f, 3.1f)
                reflectiveCurveToRelative(-1.39f, 3.1f, -3.1f, 3.1f)
                horizontalLineToRelative(-4f)
                verticalLineTo(17f)
                horizontalLineToRelative(4f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
                close()
            }
        }
    }

    /** Moon — appearance */
    val Moon: ImageVector by lazy {
        build("VidMoon") {
            fillPath {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                curveTo(12.92f, 3.04f, 12.46f, 3f, 12f, 3f)
                close()
            }
        }
    }

    /** Shield — privacy */
    val Shield: ImageVector by lazy {
        build("VidShield") {
            fillPath {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                verticalLineTo(5f)
                lineToRelative(-9f, -4f)
                close()
            }
        }
    }

    /** Video library — empty state */
    val VideoLibrary: ImageVector by lazy {
        build("VidVideoLibrary") {
            fillPath {
                moveTo(4f, 6f)
                horizontalLineTo(2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
                moveTo(20f, 2f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(10f, 15.5f)
                verticalLineToRelative(-9f)
                lineToRelative(7.5f, 4.5f)
                lineToRelative(-7.5f, 4.5f)
                close()
            }
        }
    }

    /** Refresh/Update arrow — settings updates section */
    val Update: ImageVector by lazy {
        build("VidUpdate") {
            fillPath {
                moveTo(21f, 10.12f)
                horizontalLineToRelative(-6.78f)
                lineToRelative(2.74f, -2.82f)
                curveToRelative(-2.73f, -2.7f, -7.15f, -2.8f, -9.88f, -0.1f)
                curveToRelative(-2.73f, 2.71f, -2.73f, 7.08f, 0f, 9.79f)
                reflectiveCurveToRelative(7.15f, 2.71f, 9.88f, 0f)
                curveTo(18.32f, 15.65f, 19f, 14.08f, 19f, 12.1f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, 1.98f, -0.88f, 4.08f, -2.64f, 5.84f)
                curveToRelative(-3.51f, 3.48f, -9.21f, 3.48f, -12.72f, 0f)
                curveToRelative(-3.5f, -3.47f, -3.53f, -9.11f, -0.02f, -12.58f)
                reflectiveCurveToRelative(9.14f, -3.47f, 12.65f, 0f)
                lineTo(21f, 3f)
                verticalLineToRelative(7.12f)
                close()
            }
        }
    }
}
