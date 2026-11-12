package dev.lentitudroid

import android.content.Context
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val Bg = Color(0xFF2B2F3A)
val Fg = Color.White
val Dim = Color(0xFF8A90A0)
val Err = Color(0xFFFF6B6B)
private val NickPalette = listOf(
    Color(0xFF7DCFFF), Color(0xFF9ECE6A), Color(0xFFE0AF68),
    Color(0xFFBB9AF7), Color(0xFFF7768E), Color(0xFF73DACA),
)

/** Cascadia Code из assets/fonts/CascadiaCode.ttf; если файла нет — системный monospace. */
fun loadMono(ctx: Context): FontFamily = try {
    FontFamily(Typeface.createFromAsset(ctx.assets, "fonts/CascadiaCode.ttf"))
} catch (e: Exception) {
    FontFamily.Monospace
}

/** Cascadia Code для всего приложения: все Text берут шрифт отсюда. */
val LocalMono = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

private fun nickColor(nick: String) = NickPalette[(nick.hashCode() and 0x7fffffff) % NickPalette.size]

@Composable
private fun TermField(
    value: String,
    onChange: (String) -> Unit,
    mono: FontFamily,
    hint: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    onAction: () -> Unit = {},
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Fg, fontFamily = mono, fontSize = 16.sp),
        cursorBrush = SolidColor(Fg),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }, onGo = { onAction() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().border(1.dp, Dim).padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                if (value.isEmpty()) Text(hint, color = Dim, fontFamily = mono, fontSize = 16.sp)
                inner()
            }
        },
    )
}

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Fg,
    fontFamily: FontFamily = LocalMono.current,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    textAlign: TextAlign? = null,
) = androidx.compose.foundation.text.BasicText(
    text = text,
    modifier = modifier,
    style = TextStyle(color = color, fontFamily = fontFamily, fontSize = fontSize, textAlign = textAlign ?: TextAlign.Unspecified),
)

@Composable
fun StartScreen(vm: ChatViewModel, mono: FontFamily) {
    var addr by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    val err by vm.startError.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val busy = phase == Phase.Connecting
    val connect = { if (!busy) vm.connect(addr, nick) }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("LENTITUDROID", color = Fg, fontFamily = mono, fontSize = 32.sp)
        Spacer(Modifier.height(8.dp))
        Text("Клиент lentitud для Android", textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth().border(1.dp, Dim).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Введите адрес сервера для подключения:", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            TermField(addr, { addr = it }, mono, "host:port", ImeAction.Next, KeyboardType.Uri)
            Spacer(Modifier.height(16.dp))
            Text("И ник")
            Spacer(Modifier.height(16.dp))
            TermField(nick, { nick = it }, mono, "nick", ImeAction.Go, onAction = { connect() })
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    busy -> "подключение…"
                    err != null -> err!!
                    else -> " "
                },
                color = if (busy) Dim else Err,
                fontFamily = mono, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))
        Text("Либо, поднимите локальный сервер")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().background(Color.White)
                .clickable(enabled = !busy) { vm.upLocalServer(nick) }
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(0.3f))
            Text("Up local server", color = Bg, fontFamily = mono, fontSize = 20.sp)
            Spacer(Modifier.weight(0.7f))
            Text("↵", color = Bg, fontFamily = mono, fontSize = 24.sp)
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel, mono: FontFamily) {
    val lines by vm.lines.collectAsStateWithLifecycle()
    val me by vm.nick.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val style = TextStyle(color = Fg, fontFamily = mono, fontSize = 15.sp)

    BackHandler { vm.disconnect() }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("LENTITUDROID", fontFamily = mono, fontSize = 32.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))

        // Alignment.Bottom: пока сообщений мало, они прижаты к полю ввода, новые появляются снизу
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
        ) {
            items(lines) { line ->
                val text: AnnotatedString = when (line) {
                    is Line.Chat -> buildAnnotatedString {
                        withStyle(SpanStyle(color = nickColor(line.nick))) { append("${line.nick}@lentitud") }
                        append(if (line.nick == me) " -> " else " ")
                        append(line.text)
                    }
                    is Line.System -> buildAnnotatedString {
                        withStyle(SpanStyle(color = Dim)) { append("* ${line.text}") }
                    }
                    is Line.Error -> buildAnnotatedString {
                        withStyle(SpanStyle(color = Err)) { append("! ${line.text}") }
                    }
                }
                androidx.compose.foundation.text.BasicText(text, style = style)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$me@lentitud ->", color = nickColor(me), fontFamily = mono, fontSize = 15.sp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it.replace("\n", "") },
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Fg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
    }
}
