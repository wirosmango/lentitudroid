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
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Cascadia Code из assets/fonts/CascadiaCode.ttf; если файла нет — системный monospace. */
fun loadMono(ctx: Context): FontFamily = try {
    FontFamily(Typeface.createFromAsset(ctx.assets, "fonts/CascadiaCode.ttf"))
} catch (e: Exception) {
    FontFamily.Monospace
}

/** Cascadia Code для всего приложения: все Text берут шрифт отсюда. */
val LocalMono = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

internal fun nickColor(p: Palette, nick: String) = p.nicks[(nick.hashCode() and 0x7fffffff) % p.nicks.size]

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.fg,
    fontFamily: FontFamily = LocalMono.current,
    fontSize: TextUnit = 16.sp,
    textAlign: TextAlign? = null,
) = BasicText(
    text = text,
    modifier = modifier,
    style = TextStyle(color = color, fontFamily = fontFamily, fontSize = fontSize, textAlign = textAlign ?: TextAlign.Unspecified),
)

@Composable
private fun TermField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    onAction: () -> Unit = {},
) {
    val p = LocalPalette.current
    val mono = LocalMono.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = p.fg, fontFamily = mono, fontSize = 16.sp),
        cursorBrush = SolidColor(p.fg),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }, onGo = { onAction() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().border(1.dp, p.dim).padding(horizontal = 12.dp, vertical = 14.dp)) {
                if (value.isEmpty()) Text(hint, color = p.dim)
                inner()
            }
        },
    )
}

@Composable
fun StartScreen(vm: ChatViewModel) {
    if (LocalPalette.current.style == UiStyle.Material) MaterialStartScreen(vm) else TerminalStartScreen(vm)
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    if (LocalPalette.current.style == UiStyle.Material) MaterialChatScreen(vm) else TerminalChatScreen(vm)
}

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    if (LocalPalette.current.style == UiStyle.Material) MaterialSettingsScreen(vm) else TerminalSettingsScreen(vm)
}

@Composable
private fun TerminalStartScreen(vm: ChatViewModel) {
    val p = LocalPalette.current
    var addr by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    val err by vm.startError.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val busy = phase == Phase.Connecting
    val connect = { if (!busy) vm.connect(addr, nick) }

    Column(
        Modifier.fillMaxSize().background(p.bg).systemBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("LENTITUDROID", fontSize = 32.sp)
        Spacer(Modifier.height(8.dp))
        Text("Клиент lentitud для Android", textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth().border(1.dp, p.dim).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Введите адрес сервера для подключения:", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            TermField(addr, { addr = it }, "host:port", ImeAction.Next, KeyboardType.Uri)
            Spacer(Modifier.height(16.dp))
            Text("И ник")
            Spacer(Modifier.height(16.dp))
            TermField(nick, { nick = it }, "nick", ImeAction.Go, onAction = { connect() })
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    busy -> "подключение…"
                    err != null -> err!!
                    else -> " "
                },
                color = if (busy) p.dim else p.err,
                fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))
        Text("Либо, поднимите локальный сервер")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().background(p.fg)
                .clickable(enabled = !busy) { vm.upLocalServer(nick) }
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(0.3f))
            Text("Up local server", color = p.bg, fontSize = 20.sp)
            Spacer(Modifier.weight(0.7f))
            Text("↵", color = p.bg, fontSize = 24.sp)
        }
    }
}

@Composable
private fun chatLineText(p: Palette, line: Line, me: String): AnnotatedString = when (line) {
    is Line.Chat -> buildAnnotatedString {
        withStyle(SpanStyle(color = nickColor(p, line.nick))) { append("${line.nick}@lentitud") }
        append(if (line.nick == me) " -> " else " ")
        append(line.text)
    }
    is Line.System -> buildAnnotatedString { withStyle(SpanStyle(color = p.dim)) { append("* ${line.text}") } }
    is Line.Error -> buildAnnotatedString { withStyle(SpanStyle(color = p.err)) { append("! ${line.text}") } }
}

@Composable
private fun TerminalChatScreen(vm: ChatViewModel) {
    val p = LocalPalette.current
    val mono = LocalMono.current
    val lines by vm.lines.collectAsStateWithLifecycle()
    val me by vm.nick.collectAsStateWithLifecycle()
    val fs by vm.fontSize.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val style = TextStyle(color = p.fg, fontFamily = mono, fontSize = fs.sp)

    BackHandler { vm.disconnect() }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))

        // Alignment.Bottom: пока сообщений мало, они прижаты к полю ввода, новые появляются снизу
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
        ) {
            items(lines) { line -> BasicText(chatLineText(p, line, me), style = style) }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$me@lentitud ->", color = nickColor(p, me), fontSize = fs.sp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it.replace("\n", "") },
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(p.fg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSend = { vm.submit(input); input = "" }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LocalPalette.current.dim)
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun TerminalSettingsScreen(vm: ChatViewModel) {
    val p = LocalPalette.current
    val theme by vm.theme.collectAsStateWithLifecycle()
    val fs by vm.fontSize.collectAsStateWithLifecycle()
    val me by vm.nick.collectAsStateWithLifecycle()

    BackHandler { vm.closeSettings() }

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().padding(24.dp)) {
        Text("SETTINGS", fontSize = 32.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(32.dp))

        SettingRow("тема") {
            Text("< ${theme.id} >", modifier = Modifier.clickable { vm.cycleTheme() }.padding(8.dp))
        }
        SettingRow("размер текста") {
            Text("[-]", modifier = Modifier.clickable { vm.setFontSize(fs - 1) }.padding(8.dp))
            Text("$fs")
            Text("[+]", modifier = Modifier.clickable { vm.setFontSize(fs + 1) }.padding(8.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("предпросмотр:", color = p.dim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().border(1.dp, p.dim).padding(12.dp)) {
            val name = me.ifEmpty { "nick" }
            Text(
                "$name@lentitud -> привет!",
                color = nickColor(p, name), fontSize = fs.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text("other@lentitud как дела?", fontSize = fs.sp)
        }

        Spacer(Modifier.weight(1f))
        Text("/theme — быстро сменить тему, /leave — на главный экран", color = p.dim, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().background(p.fg).clickable { vm.closeSettings() }.padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) { Text("← назад", color = p.bg, fontSize = 18.sp) }
    }
}
