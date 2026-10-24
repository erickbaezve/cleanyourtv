package com.miusuario.tvlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.miusuario.tvlauncher.CalidadVidrio
import com.miusuario.tvlauncher.data.AppInfo
import com.miusuario.tvlauncher.model.AppEntry
import com.miusuario.tvlauncher.model.Categoria
import com.miusuario.tvlauncher.model.FolderEntry
import com.miusuario.tvlauncher.model.GridEntry
import com.miusuario.tvlauncher.ui.glass.LocalCalidad
import com.miusuario.tvlauncher.ui.glass.glass
import com.miusuario.tvlauncher.ui.theme.LocalGlass

/**
 * La única tarjeta principal: muestra la entrada enfocada (banner grande, nombre y color) y cambia con una animación
 * de resorte cada vez que el foco pasa a otra app. Nada de recomendaciones ni anuncios.
 */
@Composable
fun Hero(
    entry: GridEntry?,
    apps: Map<String, AppInfo>,
    alto: Dp,
    conFondo: Boolean,
    cargarBanner: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = entry,
        contentKey = { it?.id },
        transitionSpec = {
            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f) +
                slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 40 })
                .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMedium)))
        },
        modifier = modifier.fillMaxWidth().height(alto),
        label = "hero",
    ) { e ->
        if (e != null) HeroContenido(e, apps, alto, conFondo, cargarBanner)
    }
}

@Composable
private fun HeroContenido(
    entry: GridEntry,
    apps: Map<String, AppInfo>,
    alto: Dp,
    conFondo: Boolean,
    cargarBanner: suspend (String) -> ImageBitmap?,
) {
    val tokens = LocalGlass.current
    val calidad = LocalCalidad.current
    val color = entry.color(apps)
    val forma = RoundedCornerShape(24.dp)
    val sombra = if (calidad == CalidadVidrio.ALTA) color else tokens.sombra
    Row(
        Modifier.fillMaxSize().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .shadow(24.dp, forma, ambientColor = sombra, spotColor = sombra)
                .clip(forma)
                .background(color),
        ) {
            when (entry) {
                is AppEntry -> {
                    val app = apps[entry.pkg]
                    val grande by produceState<ImageBitmap?>(null, entry.pkg) { value = cargarBanner(entry.pkg) }
                    val img = grande ?: app?.banner
                    if (img != null) {
                        Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        ArteApp(app, Modifier.fillMaxSize())
                    }
                }
                is FolderEntry -> MiniCuadricula(entry, apps, Modifier.fillMaxSize())
            }
        }
        Column(
            Modifier
                .weight(1f)
                .glass(RoundedCornerShape(28.dp), conFondo = conFondo, elevacion = 8.dp)
                .background(tokens.veloTexto)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                entry.titulo(apps),
                style = MaterialTheme.typography.displaySmall,
                color = tokens.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitulo(entry, apps), style = MaterialTheme.typography.titleMedium, color = tokens.textoSecundario)
            if (entry is FolderEntry) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.apps.take(8).forEach { pkg ->
                        apps[pkg]?.let { Image(it.icon, it.label, Modifier.size(alto / 9).clip(RoundedCornerShape(8.dp))) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "OK para abrir · Mantén OK para editar",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textoSecundario,
            )
        }
    }
}

private fun subtitulo(entry: GridEntry, apps: Map<String, AppInfo>): String = when (entry) {
    is FolderEntry -> "Carpeta · ${entry.apps.size} apps"
    is AppEntry -> apps[entry.pkg]?.categoria?.let {
        when (it) {
            Categoria.STREAMING -> "Vídeo"
            Categoria.JUEGOS -> "Juego"
            Categoria.MUSICA -> "Música"
            Categoria.UTILIDADES -> "Utilidad"
            Categoria.DESCONOCIDA -> "App"
        }
    } ?: "App"
}
