package com.miusuario.tvlauncher.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/** Valores del cristal para el tema actual (ya animados durante el cambio de tema). */
@Immutable
data class GlassTokens(
    val dark: Boolean,
    val fondo: Color,
    val texto: Color,
    val textoSecundario: Color,
    val tinte: Color,
    val bordeClaro: Color,
    val bordeTenue: Color,
    val veloTexto: Color,
    val especular: Color,
    val sombra: Color,
    val intensidadDegradado: Float,
    val bordeFoco: Color,
    val acento: Color,
)

val LocalGlass = staticCompositionLocalOf {
    Paleta.NOCTURNO.let { p ->
        GlassTokens(
            true, Color(p.fondo), Color(p.texto), Color(p.textoSecundario), Color(p.tinteVidrio),
            Color(p.bordeClaro), Color(p.bordeTenue), Color(p.veloTexto), Color(p.brilloEspecular),
            Color(p.sombra), p.intensidadDegradado, Color(p.bordeFoco), Color(p.acento),
        )
    }
}

// Roboto es la fuente del sistema en Fire OS (sans-serif); la de Amazon no se puede usar.
private val Roboto = FontFamily.SansSerif

private val tipografia = Typography(
    displaySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
private fun anim(target: Long): Color =
    animateColorAsState(Color(target), spring(stiffness = Spring.StiffnessVeryLow), label = "tema").value

/** Tema del launcher. Al pasar de claro a nocturno todos los tokens se funden con un resorte suave, sin parpadeo. */
@Composable
fun LauncherTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) Paleta.NOCTURNO else Paleta.CLARO
    val tokens = GlassTokens(
        dark = dark,
        fondo = anim(p.fondo),
        texto = anim(p.texto),
        textoSecundario = anim(p.textoSecundario),
        tinte = anim(p.tinteVidrio),
        bordeClaro = anim(p.bordeClaro),
        bordeTenue = anim(p.bordeTenue),
        veloTexto = anim(p.veloTexto),
        especular = anim(p.brilloEspecular),
        sombra = anim(p.sombra),
        intensidadDegradado = animateFloatAsState(
            p.intensidadDegradado, spring(stiffness = Spring.StiffnessVeryLow), label = "degradado",
        ).value,
        bordeFoco = anim(p.bordeFoco),
        acento = anim(p.acento),
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = tokens.acento, background = tokens.fondo, surface = Color(p.superficie),
            onBackground = tokens.texto, onSurface = tokens.texto, border = tokens.bordeFoco,
        )
    } else {
        lightColorScheme(
            primary = tokens.acento, background = tokens.fondo, surface = Color(p.superficie),
            onBackground = tokens.texto, onSurface = tokens.texto, border = tokens.bordeFoco,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = tipografia) {
        CompositionLocalProvider(LocalGlass provides tokens, content = content)
    }
}
