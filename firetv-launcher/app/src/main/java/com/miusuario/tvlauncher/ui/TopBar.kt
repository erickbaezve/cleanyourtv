package com.miusuario.tvlauncher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.miusuario.tvlauncher.ui.glass.glass
import com.miusuario.tvlauncher.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Barra superior estilo Fire TV: píldora de búsqueda a la izquierda; a la derecha reloj, engranaje de ajustes rápidos
 * y círculo de perfil. Todo en superficies de cristal.
 */
@Composable
fun TopBar(
    margen: Dp,
    buscando: Boolean,
    consulta: String,
    alConsultar: (String) -> Unit,
    alActivarBusqueda: () -> Unit,
    buscarFR: FocusRequester,
    ajustesFR: FocusRequester,
    rejillaFR: FocusRequester,
    alAjustes: () -> Unit,
    alPerfil: () -> Unit,
    pildoraConFondo: Boolean,
) {
    val tokens = LocalGlass.current
    val campoFR = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = margen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Píldora de búsqueda. Filtra las apps instaladas (también las que están dentro de carpetas).
        var focoPildora by remember { mutableStateOf(false) }
        Box(
            Modifier
                .width(300.dp)
                .height(40.dp)
                .focusRequester(buscarFR)
                .focusProperties {
                    down = rejillaFR
                    canFocus = true
                }
                .onFocusChanged { focoPildora = it.hasFocus }
                .then(if (buscando) Modifier else Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = alActivarBusqueda))
                .glass(RoundedCornerShape(50), conFondo = pildoraConFondo)
                .border(if (focoPildora) 2.dp else 0.dp, if (focoPildora) tokens.bordeFoco else Color.Transparent, RoundedCornerShape(50))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Lupa(tokens.texto)
                if (buscando) {
                    LaunchedEffect(Unit) { campoFR.requestFocus() }
                    BasicTextField(
                        value = consulta,
                        onValueChange = alConsultar,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = tokens.texto),
                        cursorBrush = SolidColor(tokens.acento),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { rejillaFR.requestFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(campoFR)
                            .focusProperties { down = rejillaFR },
                    )
                } else {
                    Text(
                        consulta.ifEmpty { "Buscar apps" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (consulta.isEmpty()) tokens.textoSecundario else tokens.texto,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .height(44.dp)
                .glass(RoundedCornerShape(50), conFondo = false)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Reloj(Modifier.padding(horizontal = 12.dp))
            BotonRedondo(rejillaFR, alAjustes, Modifier.focusRequester(ajustesFR)) { color -> Engranaje(color) }
            BotonRedondo(rejillaFR, alPerfil) { color -> Perfil(color) }
        }
    }
}

@Composable
private fun Reloj(modifier: Modifier) {
    val tokens = LocalGlass.current
    val hora by produceState(ahora()) {
        while (true) {
            value = ahora()
            delay(15_000)
        }
    }
    Text(hora, style = MaterialTheme.typography.titleMedium, color = tokens.texto, modifier = modifier)
}

private fun ahora(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

@Composable
private fun BotonRedondo(
    rejillaFR: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: @Composable (Color) -> Unit,
) {
    val tokens = LocalGlass.current
    var foco by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(if (foco) 1.15f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "boton")
    Box(
        modifier
            .size(34.dp)
            .focusProperties {
                down = rejillaFR
                canFocus = true // también si el sistema cree que hay pantalla táctil
            }
            .onFocusChanged { foco = it.isFocused }
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .background(if (foco) tokens.texto else Color.Transparent, CircleShape)
            .border(if (foco) 2.dp else 0.dp, if (foco) tokens.acento else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        icono(if (foco) tokens.fondo else tokens.texto)
    }
}

@Composable
private fun Lupa(color: Color) = Canvas(Modifier.size(18.dp)) {
    val r = size.minDimension * 0.34f
    val c = Offset(size.width * 0.42f, size.height * 0.42f)
    drawCircle(color, r, c, style = Stroke(width = size.minDimension * 0.12f))
    drawLine(color, Offset(c.x + r * 0.7f, c.y + r * 0.7f), Offset(size.width * 0.92f, size.height * 0.92f),
        strokeWidth = size.minDimension * 0.14f, cap = StrokeCap.Round)
}

@Composable
private fun Engranaje(color: Color) = Canvas(Modifier.size(20.dp)) {
    val c = center
    val r = size.minDimension * 0.30f
    for (i in 0 until 8) {
        rotate(i * 45f, c) {
            drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y - r * 1.55f), strokeWidth = size.minDimension * 0.16f, cap = StrokeCap.Round)
        }
    }
    drawCircle(color, r, c, style = Stroke(width = size.minDimension * 0.14f))
}

@Composable
private fun Perfil(color: Color) = Canvas(Modifier.size(22.dp)) {
    drawCircle(color, size.minDimension * 0.47f, center, style = Stroke(width = size.minDimension * 0.08f))
    drawCircle(color, size.minDimension * 0.16f, Offset(center.x, size.height * 0.38f))
    drawArc(
        color, 200f, 140f, useCenter = false,
        topLeft = Offset(size.width * 0.22f, size.height * 0.58f),
        size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.5f),
        style = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round),
    )
}
