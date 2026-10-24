package com.miusuario.tvlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.miusuario.tvlauncher.data.AppInfo
import com.miusuario.tvlauncher.model.AppEntry
import com.miusuario.tvlauncher.model.Categoria
import com.miusuario.tvlauncher.model.FolderEntry
import com.miusuario.tvlauncher.model.GridEntry
import com.miusuario.tvlauncher.ui.theme.LocalGlass

/**
 * La tarjeta principal ocupa todo el fondo: el arte de la app enfocada a pantalla completa, con velos del color del
 * tema a la izquierda y abajo para que el texto y el Dock se lean. Cambia con un fundido de resorte al mover el foco.
 * [atenuacion] (0..1) apaga el arte cuando el foco baja a la rejilla. Nada de recomendaciones ni anuncios.
 */
@Composable
fun FondoHero(
    entry: GridEntry?,
    apps: Map<String, AppInfo>,
    atenuacion: Float,
    cargarBanner: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlass.current
    Box(modifier.fillMaxSize().background(tokens.fondo)) {
        AnimatedContent(
            targetState = entry,
            contentKey = { it?.id },
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                    scaleIn(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow), initialScale = 1.04f))
                    .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessLow)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "fondoHero",
        ) { e -> if (e != null) ArteFondo(e, apps, cargarBanner) }

        // Velos: izquierda (texto), abajo (Dock y rejilla) y arriba (barra). Del color del tema, no negro puro.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to tokens.fondo.copy(alpha = 0.92f),
                    0.42f to tokens.fondo.copy(alpha = 0.55f),
                    0.75f to tokens.fondo.copy(alpha = 0f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to tokens.fondo.copy(alpha = 0.55f),
                    0.18f to tokens.fondo.copy(alpha = 0f),
                    0.52f to tokens.fondo.copy(alpha = 0f),
                    0.82f to tokens.fondo.copy(alpha = 0.85f),
                    1f to tokens.fondo,
                ),
            ),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = atenuacion }.background(tokens.fondo.copy(alpha = 0.86f)))
    }
}

@Composable
private fun ArteFondo(entry: GridEntry, apps: Map<String, AppInfo>, cargarBanner: suspend (String) -> ImageBitmap?) {
    val color = entry.color(apps)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(color, Color(color.red * 0.4f, color.green * 0.4f, color.blue * 0.4f)))),
    ) {
        when (entry) {
            is AppEntry -> {
                val app = apps[entry.pkg]
                val grande by produceState<ImageBitmap?>(null, entry.pkg) { value = cargarBanner(entry.pkg) }
                val img = grande ?: app?.banner
                if (img != null) {
                    Image(
                        img, null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (app != null) {
                    // Sin banner: el icono grande a la derecha, sobre su color.
                    Box(Modifier.fillMaxSize().padding(end = 140.dp, bottom = 120.dp), contentAlignment = Alignment.CenterEnd) {
                        Image(app.icon, null, Modifier.fillMaxHeight(0.38f).aspectRatio(1f))
                    }
                }
            }
            is FolderEntry -> Box(
                Modifier.fillMaxSize().padding(start = 480.dp, end = 72.dp, top = 90.dp, bottom = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                MiniCuadricula(entry, apps, Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
            }
        }
    }
}

/**
 * Información compacta de la tarjeta principal, dentro del fondo: tipo, título y los botones Abrir (▶) e Información.
 * Abajo va al Dock ([abajo]); arriba, a la barra superior ([arriba]).
 */
@Composable
fun InfoHero(
    entry: GridEntry?,
    apps: Map<String, AppInfo>,
    abrirFR: FocusRequester,
    arriba: FocusRequester,
    abajo: FocusRequester,
    alAbrir: () -> Unit,
    alInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlass.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedContent(
            targetState = entry,
            contentKey = { it?.id },
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { -it / 24 })
                    .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMedium)))
            },
            label = "infoHero",
        ) { e ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e != null) {
                    Text(
                        subtitulo(e, apps).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                        color = tokens.acento,
                    )
                    Text(
                        e.titulo(apps),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                        color = tokens.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (e is FolderEntry) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            e.apps.take(8).forEach { pkg ->
                                apps[pkg]?.let { Image(it.icon, it.label, Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonHero(
                texto = if (entry is FolderEntry) "Abrir carpeta" else "Abrir",
                principal = true,
                arriba = arriba,
                abajo = abajo,
                icono = { c -> IconoPlay(c) },
                modifier = Modifier.focusRequester(abrirFR),
                onClick = alAbrir,
            )
            BotonHero(
                texto = "Información",
                principal = false,
                arriba = arriba,
                abajo = abajo,
                icono = { c -> IconoInfo(c) },
                onClick = alInfo,
            )
        }
    }
}

/** Botón de la tarjeta principal: cápsula; el principal casi blanco (como en Apple TV) y el otro de cristal. */
@Composable
private fun BotonHero(
    texto: String,
    principal: Boolean,
    arriba: FocusRequester,
    abajo: FocusRequester,
    icono: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalGlass.current
    var foco by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(if (foco) 1.1f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "botonHero")
    val forma = RoundedCornerShape(50)
    val oscuro = Color(0xFF1C1C1E)
    val fondo = when {
        foco -> Color.White
        principal -> tokens.texto.copy(alpha = 0.9f)
        else -> tokens.tinte.copy(alpha = (tokens.tinte.alpha * 2.5f).coerceAtMost(1f))
    }
    val tinta = when {
        foco -> oscuro
        principal -> tokens.fondo
        else -> tokens.texto
    }
    Row(
        modifier
            .focusProperties {
                up = arriba
                down = abajo
                canFocus = true
            }
            .onFocusChanged { foco = it.isFocused }
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .background(fondo, forma)
            .border(if (foco) 2.dp else 1.dp, if (foco) tokens.acento else tokens.bordeTenue, forma)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icono(tinta)
        Text(texto, style = MaterialTheme.typography.titleMedium, color = tinta)
    }
}

@Composable
private fun IconoPlay(color: Color) = Canvas(Modifier.size(13.dp)) {
    val p = Path().apply {
        moveTo(size.width * 0.12f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(size.width * 0.12f, size.height)
        close()
    }
    drawPath(p, color)
}

@Composable
private fun IconoInfo(color: Color) = Canvas(Modifier.size(16.dp)) {
    val s = size.minDimension
    drawCircle(color, s * 0.45f, center, style = Stroke(width = s * 0.1f))
    drawCircle(color, s * 0.07f, Offset(center.x, s * 0.3f))
    drawLine(color, Offset(center.x, s * 0.45f), Offset(center.x, s * 0.74f), strokeWidth = s * 0.12f)
}

fun subtitulo(entry: GridEntry, apps: Map<String, AppInfo>): String = when (entry) {
    is FolderEntry -> "Carpeta · ${entry.apps.size} apps"
    is AppEntry -> nombreCategoria(apps[entry.pkg]?.categoria)
}

fun nombreCategoria(c: Categoria?): String = when (c) {
    Categoria.STREAMING -> "Vídeo"
    Categoria.JUEGOS -> "Juego"
    Categoria.MUSICA -> "Música"
    Categoria.UTILIDADES -> "Utilidad"
    Categoria.DESCONOCIDA, null -> "App"
}
