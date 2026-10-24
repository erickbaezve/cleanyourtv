package com.miusuario.tvlauncher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.miusuario.tvlauncher.CalidadVidrio
import com.miusuario.tvlauncher.Config
import com.miusuario.tvlauncher.data.AppInfo
import com.miusuario.tvlauncher.model.AppEntry
import com.miusuario.tvlauncher.model.FolderEntry
import com.miusuario.tvlauncher.model.GridEntry
import com.miusuario.tvlauncher.ui.glass.LocalCalidad
import com.miusuario.tvlauncher.ui.glass.LocalEspecular
import com.miusuario.tvlauncher.ui.glass.glass
import com.miusuario.tvlauncher.ui.theme.LocalGlass

/** Estado visual de un icono de la rejilla. */
enum class EstadoTile { NORMAL, ENFOCADO, LEVANTADO, DESTINO }

/** Nombre que se muestra para una entrada. */
fun GridEntry.titulo(apps: Map<String, AppInfo>): String = when (this) {
    is AppEntry -> apps[pkg]?.label ?: pkg
    is FolderEntry -> name
}

/** Color dominante de una entrada (para carpetas, el de su primera app). */
fun GridEntry.color(apps: Map<String, AppInfo>): Color = when (this) {
    is AppEntry -> apps[pkg]?.color
    is FolderEntry -> this.apps.firstNotNullOfOrNull { apps[it]?.color }
} ?: Color(0xFF2A3A4A)

/**
 * Icono 5:3 con esquinas redondeadas. Enfocado: escala [Config.ESCALA_FOCO], inclinación parallax ([inclinacion],
 * en grados X/Y, leída solo al dibujar), capa de cristal con brillo en el borde, sombra del color dominante y el
 * nombre debajo en una etiqueta de cristal. En modo edición todos los iconos hacen un leve vaivén.
 */
@Composable
fun Tile(
    entry: GridEntry,
    apps: Map<String, AppInfo>,
    ancho: Dp,
    estado: EstadoTile,
    vaiven: Boolean,
    fase: Int,
    inclinacion: State<Offset>?,
    etiquetaConFondo: Boolean,
    modifier: Modifier = Modifier,
    alPosicionar: ((Rect) -> Unit)? = null,
) {
    val tokens = LocalGlass.current
    val calidad = LocalCalidad.current
    val especular = LocalEspecular.current
    val alto = ancho / Config.RELACION
    val forma = RoundedCornerShape(Config.RADIO_ESQUINA)
    val enfocado = estado != EstadoTile.NORMAL
    val escala by animateFloatAsState(
        targetValue = when (estado) {
            EstadoTile.NORMAL -> 1f
            EstadoTile.ENFOCADO, EstadoTile.DESTINO -> Config.ESCALA_FOCO
            EstadoTile.LEVANTADO -> Config.ESCALA_FOCO + 0.08f
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "escala",
    )
    val giro = if (vaiven) {
        val t = rememberInfiniteTransition(label = "vaiven")
        val a by t.animateFloat(
            initialValue = -1.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 130 + (fase % 3) * 15), RepeatMode.Reverse),
            label = "giro",
        )
        a
    } else {
        0f
    }
    val color = entry.color(apps)
    val sombra = if (calidad == CalidadVidrio.ALTA && enfocado) color.copy(alpha = 0.9f) else tokens.sombra

    Box(modifier.width(ancho)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(Config.RELACION)
                .then(if (alPosicionar != null) Modifier.onGloballyPositioned { alPosicionar(it.boundsInRoot()) } else Modifier)
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    rotationZ = giro
                    val inc = if (enfocado && calidad != CalidadVidrio.BAJA) inclinacion?.value ?: Offset.Zero else Offset.Zero
                    rotationX = inc.x
                    rotationY = inc.y
                    cameraDistance = 14f * density
                }
                .shadow(if (enfocado) 18.dp else 3.dp, forma, ambientColor = sombra, spotColor = sombra)
                .clip(forma)
                .background(color),
        ) {
            when (entry) {
                is AppEntry -> ArteApp(apps[entry.pkg], Modifier.fillMaxSize())
                is FolderEntry -> MiniCuadricula(entry, apps, Modifier.fillMaxSize())
            }
            if (enfocado) {
                // Capa de cristal sobre el icono: brillo especular que sigue la inclinación y borde brillante.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            if (calidad != CalidadVidrio.BAJA) {
                                val x = especular.value
                                drawRect(
                                    Brush.linearGradient(
                                        listOf(tokens.especular, Color.Transparent, Color.Transparent),
                                        start = Offset(size.width * (0.2f + 0.3f * x), 0f),
                                        end = Offset(size.width * (0.6f + 0.3f * x), size.height),
                                    ),
                                )
                            }
                        }
                        .border(
                            width = if (estado == EstadoTile.ENFOCADO) 2.dp else 3.dp,
                            brush = when (estado) {
                                EstadoTile.DESTINO, EstadoTile.LEVANTADO -> Brush.linearGradient(listOf(Config.CIAN_FIRE_TV, Config.CIAN_FIRE_TV))
                                else -> Brush.linearGradient(
                                    listOf(tokens.bordeFoco, tokens.bordeFoco.copy(alpha = 0.45f)),
                                    start = Offset.Zero,
                                    end = Offset.Infinite,
                                )
                            },
                            shape = forma,
                        ),
                )
            }
        }
        if (enfocado) {
            // El icono escalado crece un 10% por abajo: la etiqueta queda justo debajo.
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = alto * (1f + (Config.ESCALA_FOCO - 1f) / 2f) + 6.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = entry.titulo(apps),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .glass(RoundedCornerShape(50), conFondo = etiquetaConFondo)
                        .background(tokens.veloTexto)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Banner recortado al centro a 5:3, o el icono centrado sobre su color dominante si la app no tiene banner. */
@Composable
fun ArteApp(app: AppInfo?, modifier: Modifier) {
    if (app == null) return
    if (app.banner != null) {
        Image(app.banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(
            modifier.background(Brush.verticalGradient(listOf(app.color, app.color.copy(alpha = 0.75f).compositeOver(Color.Black)))),
            contentAlignment = Alignment.Center,
        ) {
            Image(app.icon, contentDescription = null, modifier = Modifier.fillMaxHeight(0.62f).aspectRatio(1f))
        }
    }
}

/** Icono de carpeta: hasta 9 apps en una mini-cuadrícula 3x3, sobre cristal. */
@Composable
fun MiniCuadricula(folder: FolderEntry, apps: Map<String, AppInfo>, modifier: Modifier) {
    val tokens = LocalGlass.current
    val celdas = folder.apps.take(Config.MINI_CUADRICULA)
    Box(
        modifier.background(
            Brush.linearGradient(listOf(tokens.tinte.compositeOver(tokens.fondo), tokens.fondo.copy(alpha = 0.92f))),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (fila in 0 until 3) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (col in 0 until 3) {
                        val pkg = celdas.getOrNull(fila * 3 + col)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp)),
                        ) {
                            if (pkg != null) ArteApp(apps[pkg], Modifier.fillMaxSize()) else Spacer(Modifier.size(0.dp))
                        }
                    }
                }
            }
        }
    }
}
