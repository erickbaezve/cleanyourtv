package com.miusuario.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.miusuario.tvlauncher.CalidadVidrio
import com.miusuario.tvlauncher.Config
import com.miusuario.tvlauncher.ModoTema
import com.miusuario.tvlauncher.data.Ajustes
import com.miusuario.tvlauncher.ui.glass.glass
import com.miusuario.tvlauncher.ui.theme.LocalGlass

/** Velo del color de fondo bajo los paneles con mucho texto: el cristal se nota en los bordes y el texto se lee. */
private const val VELO_PANEL = 0.8f

/** Botón de cristal enfocable: borde blanco fino y leve escala al enfocar, punto cian si está seleccionado. */
@Composable
fun Chip(
    texto: String,
    modifier: Modifier = Modifier,
    seleccionado: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalGlass.current
    var foco by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(if (foco) 1.06f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "chip")
    val forma = RoundedCornerShape(50)
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { foco = it.isFocused }
            .focusProperties { canFocus = true } // clickable solo es enfocable fuera del modo táctil
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .background(if (foco) tokens.texto.copy(alpha = 0.92f) else tokens.tinte, forma)
            .border(if (foco) 2.dp else 1.dp, if (foco) tokens.bordeFoco else tokens.bordeTenue, forma)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (seleccionado) {
                Box(Modifier.size(8.dp).background(Config.CIAN_FIRE_TV, CircleShape))
            }
            Text(
                texto,
                style = MaterialTheme.typography.titleMedium,
                color = if (foco) tokens.fondo else tokens.texto,
            )
        }
    }
}

/** Opciones del modo edición (mover, ocultar, crear carpeta, cancelar...), en un panel de cristal centrado. */
@Composable
fun MenuEdicion(titulo: String, opciones: List<Pair<String, () -> Unit>>, conFondo: Boolean, alCerrar: () -> Unit) {
    val tokens = LocalGlass.current
    val primero = remember { FocusRequester() }
    BackHandler(onBack = alCerrar)
    LaunchedEffect(Unit) { primero.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(340.dp)
                .glass(RoundedCornerShape(28.dp), conFondo = conFondo, elevacion = 16.dp)
                .background(tokens.fondo.copy(alpha = VELO_PANEL), RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = tokens.texto)
            opciones.forEachIndexed { i, (texto, accion) ->
                Chip(texto, Modifier.fillMaxWidth(), focusRequester = if (i == 0) primero else null, onClick = accion)
            }
        }
    }
}

/** Pide un texto (nombre de carpeta) con el teclado en pantalla. */
@Composable
fun DialogoTexto(titulo: String, inicial: String, conFondo: Boolean, alAceptar: (String) -> Unit, alCancelar: () -> Unit) {
    val tokens = LocalGlass.current
    var texto by remember { mutableStateOf(inicial) }
    val campo = remember { FocusRequester() }
    BackHandler(onBack = alCancelar)
    LaunchedEffect(Unit) { campo.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(420.dp)
                .glass(RoundedCornerShape(28.dp), conFondo = conFondo, elevacion = 16.dp)
                .background(tokens.fondo.copy(alpha = VELO_PANEL), RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = tokens.texto)
            BasicTextField(
                value = texto,
                onValueChange = { texto = it.take(30) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = tokens.texto),
                cursorBrush = SolidColor(tokens.acento),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { alAceptar(texto) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(campo)
                    .background(tokens.tinte, RoundedCornerShape(14.dp))
                    .border(2.dp, tokens.acento, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Guardar") { alAceptar(texto) }
                Chip("Cancelar", onClick = alCancelar)
            }
        }
    }
}

/**
 * Ajustes del launcher (y acceso rápido desde el engranaje): tema, horario del modo automático, calidad del vidrio,
 * apps ocultas y los ajustes de Fire TV.
 */
@Composable
fun PanelAjustes(
    ajustes: Ajustes,
    calidadEfectiva: CalidadVidrio,
    ocultas: List<Pair<String, String>>,
    alCambiar: (Ajustes) -> Unit,
    alMostrar: (String) -> Unit,
    alAbrirSistema: () -> Unit,
    alCerrar: () -> Unit,
) {
    val tokens = LocalGlass.current
    val primero = remember { FocusRequester() }
    BackHandler(onBack = alCerrar)
    LaunchedEffect(Unit) { primero.requestFocus() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(460.dp)
                .padding(18.dp)
                .glass(RoundedCornerShape(30.dp), elevacion = 20.dp)
                .background(tokens.fondo.copy(alpha = VELO_PANEL), RoundedCornerShape(30.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, color = tokens.texto)

            Seccion("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModoTema.entries.forEachIndexed { i, modo ->
                    Chip(modo.etiqueta, seleccionado = ajustes.modoTema == modo, focusRequester = if (i == 0) primero else null) {
                        alCambiar(ajustes.copy(modoTema = modo))
                    }
                }
            }
            if (ajustes.modoTema == ModoTema.AUTOMATICO) {
                Hora("Nocturno desde", ajustes.nocheInicio) { alCambiar(ajustes.copy(nocheInicio = it)) }
                Hora("Claro desde", ajustes.nocheFin) { alCambiar(ajustes.copy(nocheFin = it)) }
            }

            Seccion("Calidad del vidrio")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalidadVidrio.entries.forEach { c ->
                    Chip(c.etiqueta, seleccionado = ajustes.calidad == c) { alCambiar(ajustes.copy(calidad = c)) }
                }
            }
            if (calidadEfectiva != ajustes.calidad) {
                Text(
                    "Bajada a ${calidadEfectiva.etiqueta} por rendimiento (menos de ${Config.FPS_MINIMOS} fps).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textoSecundario,
                )
            }

            Seccion("Apps ocultas")
            if (ocultas.isEmpty()) {
                Text("Ninguna", style = MaterialTheme.typography.bodyMedium, color = tokens.textoSecundario)
            }
            ocultas.forEach { (pkg, nombre) ->
                Chip("Mostrar $nombre", Modifier.fillMaxWidth()) { alMostrar(pkg) }
            }

            Seccion("Sistema")
            Chip("Ajustes de Fire TV", Modifier.fillMaxWidth(), onClick = alAbrirSistema)
            Chip("Cerrar", Modifier.fillMaxWidth(), onClick = alCerrar)
        }
    }
}

@Composable
private fun Seccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textoSecundario)
}

@Composable
private fun Hora(etiqueta: String, hora: Int, alCambiar: (Int) -> Unit) {
    val tokens = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = tokens.texto, modifier = Modifier.width(130.dp))
        Chip("−") { alCambiar((hora + 23) % 24) }
        Text("%02d:00".format(hora), style = MaterialTheme.typography.titleMedium, color = tokens.texto)
        Chip("+") { alCambiar((hora + 1) % 24) }
    }
}

/**
 * Panel del botón Información de la tarjeta principal: datos de la app (o las apps de la carpeta) y accesos a abrirla
 * y a sus ajustes del sistema.
 */
@Composable
fun PanelInfo(
    titulo: String,
    lineas: List<Pair<String, String>>,
    acciones: List<Pair<String, () -> Unit>>,
    alCerrar: () -> Unit,
) {
    val tokens = LocalGlass.current
    val primero = remember { FocusRequester() }
    BackHandler(onBack = alCerrar)
    LaunchedEffect(Unit) { primero.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(480.dp)
                .glass(RoundedCornerShape(30.dp), elevacion = 18.dp)
                .background(tokens.fondo.copy(alpha = VELO_PANEL), RoundedCornerShape(30.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = tokens.texto)
            lineas.forEach { (etiqueta, valor) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = tokens.textoSecundario, modifier = Modifier.width(130.dp))
                    Text(valor, style = MaterialTheme.typography.bodyMedium, color = tokens.texto)
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                acciones.forEachIndexed { i, (texto, accion) ->
                    Chip(texto, focusRequester = if (i == 0) primero else null, onClick = accion)
                }
                Chip("Cerrar", onClick = alCerrar)
            }
        }
    }
}
