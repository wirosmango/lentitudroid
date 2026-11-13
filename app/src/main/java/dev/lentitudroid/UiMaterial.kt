package dev.lentitudroid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Тема MD3 Expressive: крупные скругления, тональные поверхности, пузыри сообщений,
// отдельное поле ввода-«пилюля» и круглая кнопка отправки. Шрифт — по-прежнему Cascadia Code.

private val CardShape = RoundedCornerShape(28.dp)
private val FieldShape = RoundedCornerShape(16.dp)
private val BubbleOther = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 6.dp)
private val BubbleMe = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp)

@Composable
private fun MdButton(
    text: String,
    container: Color,
    content: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
            .background(container.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = content, fontSize = 17.sp) }
}

@Composable
private fun MdField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    onAction: () -> Unit = {},
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = p.dim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = p.fg, fontFamily = LocalMono.current, fontSize = 16.sp),
            cursorBrush = SolidColor(p.primary),
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }, onGo = { onAction() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(p.bg, FieldShape)
                        .border(2.dp, if (focused) p.primary else Color.Transparent, FieldShape)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    if (value.isEmpty()) Text(hint, color = p.dim.copy(alpha = 0.7f))
                    inner()
                }
            },
        )
    }
}

@Composable
fun MaterialStartScreen(vm: ChatViewModel) {
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
        Text("Lentitudroid", fontSize = 34.sp, color = p.primary)
        Spacer(Modifier.height(8.dp))
        Text("Клиент lentitud для Android", color = p.dim, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth().background(p.surfaceHigh, CardShape).padding(20.dp)) {
            MdField(addr, { addr = it }, "Адрес сервера", "host:port", ImeAction.Next, KeyboardType.Uri)
            Spacer(Modifier.height(16.dp))
            MdField(nick, { nick = it }, "Ник", "nick", ImeAction.Go, onAction = { connect() })
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    busy -> "Подключение…"
                    err != null -> err!!
                    else -> " "
                },
                color = if (busy) p.dim else p.err,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            MdButton("Подключиться", p.primary, p.onPrimary, enabled = !busy) { connect() }
        }

        Spacer(Modifier.weight(1f))
        Text("Либо, поднимите локальный сервер", color = p.dim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        MdButton("Up local server", p.primaryContainer, p.onPrimaryContainer, enabled = !busy) {
            vm.upLocalServer(nick)
        }
    }
}

@Composable
fun MaterialChatScreen(vm: ChatViewModel) {
    val p = LocalPalette.current
    val mono = LocalMono.current
    val lines by vm.lines.collectAsStateWithLifecycle()
    val me by vm.nick.collectAsStateWithLifecycle()
    val fs by vm.fontSize.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val send = { vm.submit(input); input = "" }

    BackHandler { vm.disconnect() }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items(lines) { line ->
                when (line) {
                    is Line.Chat -> {
                        val mine = line.nick == me
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                        ) {
                            Text(
                                line.nick,
                                color = nickColor(p, line.nick), fontSize = 12.sp,
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 2.dp),
                            )
                            Box(
                                Modifier.widthIn(max = 300.dp)
                                    .background(if (mine) p.primaryContainer else p.surfaceHigh, if (mine) BubbleMe else BubbleOther)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    line.text, fontSize = fs.sp,
                                    color = if (mine) p.onPrimaryContainer else p.fg,
                                )
                            }
                        }
                    }
                    is Line.System -> Text(
                        line.text, color = p.dim, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    is Line.Error -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            line.text, color = p.err, fontSize = 13.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.background(p.errContainer, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // поле ввода — отдельный элемент: «пилюля» + круглая кнопка отправки
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).heightIn(min = 56.dp)
                    .background(p.surfaceHigh, RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) Text("Сообщение или /команда", color = p.dim.copy(alpha = 0.7f), fontSize = fs.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.replace("\n", "") },
                    singleLine = true,
                    textStyle = TextStyle(color = p.fg, fontFamily = mono, fontSize = fs.sp),
                    cursorBrush = SolidColor(p.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(p.primary).clickable { send() },
                contentAlignment = Alignment.Center,
            ) { Text("↑", color = p.onPrimary, fontSize = 26.sp) }
        }
    }
}

@Composable
fun MaterialSettingsScreen(vm: ChatViewModel) {
    val p = LocalPalette.current
    val theme by vm.theme.collectAsStateWithLifecycle()
    val fs by vm.fontSize.collectAsStateWithLifecycle()

    BackHandler { vm.closeSettings() }

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().padding(24.dp)) {
        Text("Настройки", fontSize = 30.sp, color = p.primary)
        Spacer(Modifier.height(24.dp))

        Column(Modifier.fillMaxWidth().background(p.surfaceHigh, CardShape).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Тема", color = p.dim, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(CircleShape).background(p.primaryContainer)
                        .clickable { vm.cycleTheme() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text(theme.id, color = p.onPrimaryContainer, fontSize = 15.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Размер текста", color = p.dim, modifier = Modifier.weight(1f))
                RoundIcon("−") { vm.setFontSize(fs - 1) }
                Text("$fs", modifier = Modifier.padding(horizontal = 14.dp))
                RoundIcon("+") { vm.setFontSize(fs + 1) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Предпросмотр", color = p.dim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.align(Alignment.Start).background(p.surfaceHigh, BubbleOther).padding(16.dp, 10.dp)) {
                Text("как дела?", fontSize = fs.sp)
            }
            Box(Modifier.align(Alignment.End).background(p.primaryContainer, BubbleMe).padding(16.dp, 10.dp)) {
                Text("привет!", color = p.onPrimaryContainer, fontSize = fs.sp)
            }
        }

        Spacer(Modifier.weight(1f))
        Text("/theme — быстро сменить тему, /leave — на главный экран", color = p.dim, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        MdButton("Назад", p.primary, p.onPrimary) { vm.closeSettings() }
    }
}

@Composable
private fun RoundIcon(symbol: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(p.primaryContainer).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = p.onPrimaryContainer, fontSize = 20.sp) }
}
