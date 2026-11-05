package cafe.miafetta.tsuki.ui.glass

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.roundToInt

// shortcut: private HyperOS 4 recipe, revalidate after ROM changes or replace with a public API.
// Calibrated small-capsule shape channels
// the notification recipe produces a bright inner band.
internal fun miGlassParams(rounded: Boolean = true) = floatArrayOf(
    0f, 2f, .5f, .8f, 0f, 1.4f, 0f, 0f,
    0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    0f, 1f, 1f, 2f, 0f, 0f, 800f, 1.2f,
    0f, -.4f, .6f, -.8f, 0f, .7f, .8f, 1.15f,
    0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
).also {
    // OS4 SystemUI GlassToken conversion maps index 19 to shape.edge; use the tested positive edge for rectangles (zero disables blur
    // on OS4).
    if (!rounded) it[19] = 1f
}

internal fun glassBlurRadius(value: Float) = value.takeIf { it.isFinite() }?.coerceIn(0f, 40f) ?: 24f
internal fun miGlassRadiusPixels(value: Float) = (110f * glassBlurRadius(value) / 24f).roundToInt()

internal fun initializeMiGlass(radius: Float, width: Int, height: Int, rounded: Boolean,
    call: (String, Array<out Any>) -> Boolean): Boolean {
    val pixels = miGlassRadiusPixels(radius)
    return call("setMiBackgroundBlurMode", arrayOf(1)) && call("setMiViewBlurMode", arrayOf(1)) &&
        call("setMiViewMaterialType", arrayOf(1)) && call("setMiGlass", arrayOf(miGlassParams(rounded))) &&
        call("setMiGlassBlurRadius", arrayOf(pixels, pixels)) &&
        call("setMiBackgroundBlurEnhanceFlag", arrayOf(if (rounded) 8192 else 0, 12288)) &&
        call("setMiGlassSdfMaxSize", arrayOf(width.toFloat(), height.toFloat()))
}

internal fun glassBackend(enabled: Boolean, active: Boolean, native: Boolean, miuix: Boolean): String = when {
    !enabled -> "opaque"
    !active -> "inactive"
    native -> "MiGlass"
    miuix -> "miuix-blur"
    else -> "opaque"
}

// A failed instance stays failed until the View/configuration is replaced.
internal class GlassSession(private val rounded: Boolean = true, private val call: (String, Array<out Any>) -> Boolean) {
    var applied = false
        private set
    var failed = false
        private set
    private var size = 0 to 0
    private var radius = 0f
    fun update(allowed: Boolean, value: Float, width: Int, height: Int): Boolean {
        if (!allowed) {
            clear()
            return false
        }
        if (failed || width <= 0 || height <= 0) return false
        val success = if (!applied) {
            call("clearMiBackgroundBlendColor", emptyArray()) && initializeMiGlass(value, width, height, rounded, call)
        } else {
            (radius == value || call("setMiGlassBlurRadius", arrayOf(miGlassRadiusPixels(value), miGlassRadiusPixels(value)))) &&
                (size == width to height || call("setMiGlassSdfMaxSize", arrayOf(width.toFloat(), height.toFloat())))
        }
        if (!success) {
            clear()
            failed = true
            return false
        }
        applied = true
        radius = value
        size = width to height
        return true
    }
    fun clear() {
        // Attempt every reset even when an earlier reset fails.
        for ((name, args) in listOf(
            "setMiViewMaterialType" to arrayOf<Any>(0),
            "setMiGlassBlurRadius" to arrayOf<Any>(0, 0),
            "setMiBackgroundBlurEnhanceFlag" to arrayOf<Any>(0, 12288),
            "clearMiBackgroundBlendColor" to emptyArray(),
            "setMiViewBlurMode" to arrayOf<Any>(0),
            "setMiBackgroundBlurMode" to arrayOf<Any>(0))) call(name, args)
        applied = false
    }
}

@android.annotation.SuppressLint("PrivateApi") // User-authorized optional ROM probe
// denied access falls back.
internal object MiGlassCapability {
    private val signatures = mapOf(
        "setMiBackgroundBlurMode" to arrayOf(Int::class.javaPrimitiveType!!),
        "setMiViewBlurMode" to arrayOf(Int::class.javaPrimitiveType!!),
        "setMiViewMaterialType" to arrayOf(Int::class.javaPrimitiveType!!),
        "setMiGlass" to arrayOf(FloatArray::class.java),
        "setMiGlassBlurRadius" to arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
        "setMiBackgroundBlurEnhanceFlag" to arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
        "setMiGlassSdfMaxSize" to arrayOf(Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!),
        "clearMiBackgroundBlendColor" to emptyArray())
    val methods: Map<String, java.lang.reflect.Method?> by lazy {
        signatures.mapValues { (name, types) -> attempt { View::class.java.getMethod(name, *types) } }
    }
    val supported by lazy { attempt {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
            .invoke(null, "persist.sys.bionic_material_supported", "false") == "true"
    } == true && methods.values.all { it != null } }
}

internal class MiGlassView @JvmOverloads constructor(context: Context, private val rounded: Boolean = true) : View(context) {
    private val methods = MiGlassCapability.methods
    private val supported = MiGlassCapability.supported
    private val session = GlassSession(rounded) { name, args ->
        attempt {
            methods[name]?.invoke(this, *args)
            methods[name] != null
        } == true
    }
    private var radius = 24f
    private var enabled = false
    private var reported: Boolean? = null
    var report: (Boolean) -> Unit = {}
    private val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { applyMaterial() }
    }
    init {
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }
    fun configure(value: Float, active: Boolean, progress: Float) {
        alpha = progress.coerceIn(0f, 1f)
        visibility = if (reported == true && alpha > 0f) VISIBLE else INVISIBLE
        if (radius == value && enabled == active && reported != null) return
        radius = value
        enabled = active
        applyMaterial()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attempt {
            for (key in listOf("background_blur_enable", "material_style"))
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        }
        applyMaterial()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        (background as GradientDrawable).cornerRadius = if (rounded) minOf(w, h) / 2f else 0f
        invalidateOutline()
        applyMaterial()
    }
    private fun applyMaterial() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) return
        val permitted = attempt {
            Settings.Secure.getInt(context.contentResolver, "background_blur_enable", 0) == 1 &&
                Settings.Secure.getInt(context.contentResolver, "material_style", 0) == 1
        } == true
        val success = session.update(enabled && supported && isHardwareAccelerated && permitted, radius, width, height)
        visibility = if (success && alpha > 0f) VISIBLE else INVISIBLE
        if (reported != success) {
            reported = success
            if (cafe.miafetta.tsuki.BuildConfig.DEBUG) android.util.Log.d("TsukiGlass",
                "native=$success supported=$supported hardware=$isHardwareAccelerated permitted=$permitted failed=${session.failed}")
            report(success)
        }
        invalidate()
    }
    fun release() {
        session.clear()
        report(false)
    }
    override fun onDetachedFromWindow() {
        attempt { context.contentResolver.unregisterContentObserver(observer) }
        release()
        super.onDetachedFromWindow()
    }
}

private inline fun <T> attempt(block: () -> T): T? = try { block() }
catch (_: ReflectiveOperationException) { null }
catch (_: RuntimeException) { null }
catch (_: LinkageError) { null }
