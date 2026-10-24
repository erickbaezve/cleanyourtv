package com.miusuario.tvlauncher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import com.miusuario.tvlauncher.model.Categoria
import com.miusuario.tvlauncher.model.LayoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Detalles(val version: String, val instalada: Long, val actualizada: Long)

@Immutable
data class AppInfo(
    val pkg: String,
    val label: String,
    /** `android:banner` (16:9) a 320x180, o null si la app no tiene. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
    /** Color dominante del banner o del icono (Palette). */
    val color: Color,
    val categoria: Categoria,
)

/**
 * Lee las apps con LEANBACK_LAUNCHER, sus banners, iconos y color dominante.
 * Nunca consulta TvContract ni Watch Next: sin recomendaciones.
 */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val cache = HashMap<String, Pair<Long, AppInfo>>()
    private val heroCache = LruCache<String, ImageBitmap>(3)

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val apps = resolved.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
            val updated = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
            cache[pkg]?.takeIf { it.first == updated }?.second ?: runCatching { build(ri) }.getOrNull()?.also {
                cache[pkg] = updated to it
            }
        }
        cache.keys.retainAll(seen)
        apps.sortedBy { it.label.lowercase() }
    }

    private fun build(ri: ResolveInfo): AppInfo {
        val ai = ri.activityInfo
        val label = ri.loadLabel(pm).toString()
        val bannerDrawable: Drawable? = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
        val banner = bannerDrawable?.let { render(it, 320, 180) }
        val iconBmp = render(ri.loadIcon(pm), 160, 160)
        val palette = Palette.from(banner ?: iconBmp).maximumColorCount(12).generate()
        val rgb = palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb ?: 0xFF2A3A4A.toInt()
        return AppInfo(
            pkg = ai.packageName,
            label = label,
            banner = banner?.asImageBitmap(),
            icon = iconBmp.asImageBitmap(),
            color = Color(rgb),
            categoria = categoria(ai.applicationInfo),
        )
    }

    /**
     * Banner grande solo para la app enfocada, que ocupa todo el fondo (se guardan 3 en caché, ~2 MB cada uno).
     * Si la app trae un banner de alta resolución se conserva el detalle; si es pequeño, se escala con filtro.
     */
    suspend fun heroBanner(pkg: String): ImageBitmap? = withContext(Dispatchers.Default) {
        heroCache.get(pkg) ?: runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            val activityBanner = launch?.component?.let { pm.getActivityInfo(it, 0).loadBanner(pm) }
            (activityBanner ?: ai.loadBanner(pm))?.let { render(it, 960, 540).asImageBitmap() }
        }.getOrNull()?.also { heroCache.put(pkg, it) }
    }

    fun launch(pkg: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** Ajustes del sistema de Fire TV (o de Android si no existen). */
    fun openSystemSettings() {
        val fire = Intent().setClassName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.TvSettingsActivity")
        val fallback = Intent(android.provider.Settings.ACTION_SETTINGS)
        for (i in listOf(fire, fallback)) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
    }

    /** Detalles para el panel de Información. */
    suspend fun detalles(pkg: String): Detalles? = withContext(Dispatchers.Default) {
        runCatching {
            val pi = pm.getPackageInfo(pkg, 0)
            Detalles(pi.versionName ?: "", pi.firstInstallTime, pi.lastUpdateTime)
        }.getOrNull()
    }

    /** Pantalla de la app en los ajustes del sistema (forzar detención, borrar caché, desinstalar...). */
    fun openAppSettings(pkg: String) {
        try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            openSystemSettings()
        } catch (e: SecurityException) {
            openSystemSettings()
        }
    }

    // `android:appCategory` existe desde Android 8 (API 26); en Fire OS 6 (Android 7.1) se deduce del paquete.
    private fun categoria(ai: ApplicationInfo): Categoria {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (ai.category) {
                ApplicationInfo.CATEGORY_GAME -> return Categoria.JUEGOS
                ApplicationInfo.CATEGORY_AUDIO -> return Categoria.MUSICA
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return Categoria.STREAMING
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return Categoria.UTILIDADES
            }
        }
        return LayoutState.categoriaPorPaquete(ai.packageName)
    }

    private fun render(d: Drawable, w: Int, h: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == w && d.bitmap.height == h) return d.bitmap
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Mantiene la proporción del drawable: llena el lienzo recortando al centro.
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
        val scale = maxOf(w.toFloat() / iw, h.toFloat() / ih)
        val dw = (iw * scale).toInt()
        val dh = (ih * scale).toInt()
        val left = (w - dw) / 2
        val top = (h - dh) / 2
        d.setBounds(left, top, left + dw, top + dh)
        d.draw(canvas)
        return bmp
    }
}
