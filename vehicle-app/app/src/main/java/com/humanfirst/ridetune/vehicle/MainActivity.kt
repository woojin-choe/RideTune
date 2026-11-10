package com.humanfirst.ridetune.vehicle

import android.os.Bundle
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.encodeToString

private val Ink = Color(0xFF302D29)
private val Canvas = Color(0xFFF5F3F0)
private val Panel = Color(0xFFE8E2DA)
private val Accent = Color(0xFF827A70)
private val Muted = Color(0xFF625B51)
private val Line = Color(0xFFCDC5BA)
private val scheme = lightColorScheme(primary=Accent,onPrimary=Color.White,background=Canvas,onBackground=Ink,surface=Canvas,onSurface=Ink,surfaceVariant=Panel,onSurfaceVariant=Ink,error=Color(0xFF9A4C3C))
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme=scheme) { RideApp() } }
    }
}
@Composable private fun RideApp(vm: RideViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val canEdit = s.parked == true && !s.busy && !s.storageError
    var showJson by remember {mutableStateOf(false)}
    var jsonText by remember {mutableStateOf("")}
    var showReceiver by remember { mutableStateOf(false) }
    val debugBuild = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var selectedProfileId by remember(s.profiles.map { it.profileId }) {
        mutableStateOf(s.profiles.firstOrNull { it.profileId == s.selected?.profileId }?.profileId
            ?: s.profiles.firstOrNull()?.profileId)
    }
    Surface(Modifier.fillMaxSize(),color=Canvas) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("RideTune",fontSize=27.sp,fontWeight=FontWeight.Bold)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(s.connected) "● Pleos 연결됨" else "○ 차량 연결 확인 중",color=if(s.connected) Accent else Muted)
                    Surface(color=Panel,shape=RoundedCornerShape(24.dp)) {Text(when(s.parked) {true->"P · 설정 가능"; false->"주행 중 · 설정 잠금"; null->"기어 확인 안 됨"},Modifier.padding(14.dp))}
                    if(s.screen != "home") OutlinedButton(onClick=vm::home,enabled=!s.busy) {Text("홈")}
                    TextButton(onClick=vm::diagnostics,enabled=!s.busy) {Text("진단")}
                }
            }
            Spacer(Modifier.height(24.dp))
            if(s.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }
            s.message?.let {message -> Surface(color=Panel,shape=RoundedCornerShape(16.dp)) {Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {Text(message,Modifier.weight(1f)); TextButton(onClick=vm::dismiss) {Text("닫기")}}}; Spacer(Modifier.height(12.dp))}
            when(s.screen) {
                "home" -> {
                    val selected = s.profiles.firstOrNull { it.profileId == selectedProfileId } ?: s.profiles.firstOrNull()
                    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(0.75f).verticalScroll(rememberScrollState())) {
                            Text("탑승자",fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                            Text("저장된 프로필 ${s.profiles.size}",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=5.dp,bottom=16.dp))
                            s.profiles.forEach {p ->
                                val chosen = p.profileId == selected?.profileId
                                Surface(color=if(chosen) Accent else Panel,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().padding(bottom=10.dp).clickable(enabled=canEdit && s.session?.closed != false) { selectedProfileId=p.profileId }) {
                                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Surface(color=Canvas,shape=RoundedCornerShape(30.dp)) { Text(p.displayName.take(1),Modifier.padding(12.dp),fontWeight=FontWeight.Bold,color=Ink) }
                                        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(p.displayName,fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=if(chosen) Color.White else Ink); Text(profileSummary(p),fontSize=12.sp,color=if(chosen) Color.White else Muted,modifier=Modifier.padding(top=4.dp)) }
                                        if(chosen) Text("✓",color=Color.White,fontSize=18.sp)
                                    }
                                }
                            }
                            OutlinedButton(onClick={
                                if (debugBuild) showReceiver = true else { jsonText = ""; showJson = true }
                            },enabled=canEdit && s.session?.closed != false,modifier=Modifier.fillMaxWidth().height(52.dp)) {Text("프로필 가져오기")}
                            Spacer(Modifier.height(26.dp)); HorizontalDivider(color=Line)
                            Text("보호자 메모",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=20.dp,bottom=8.dp))
                            Text("새로운 차량에 적응할 시간을 주세요.",fontSize=15.sp,lineHeight=23.sp)
                        }
                        Column(Modifier.weight(1.65f).verticalScroll(rememberScrollState())) {
                            Text("탑승 준비",color=Muted,fontSize=13.sp)
                            Text("${selected?.displayName ?: "탑승자"}님의 이동 환경",fontSize=34.sp,lineHeight=43.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=6.dp))
                            Text("저장된 설정을 확인해 주세요.",color=Muted,fontSize=16.sp,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
                            selected?.let {p ->
                                SettingGroup("공조", "앞좌석 공통") {
                                    SettingLine("온도", p.preferences.temperatureCelsius?.let {"${it}°C"} ?: "변경 안 함")
                                    SettingLine("바람 세기", p.preferences.fanPreference?.let { when(it) {"LOW" -> "약하게"; "MEDIUM" -> "중간"; "HIGH" -> "강하게"; else -> "끄기"} } ?: "변경 안 함")
                                }
                                Spacer(Modifier.height(22.dp))
                                SettingGroup("실내조명", "차량 전체") { SettingLine("밝기", p.preferences.ambientBrightnessPercent?.let {"${it}%"} ?: "변경 안 함") }
                                Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                    TextButton(onClick={vm.edit(p)},enabled=canEdit && s.session?.closed != false) {Text("이번 이동 설정 조정")}
                                    Button(onClick={vm.preview(p)},enabled=canEdit && s.session?.closed != false,modifier=Modifier.height(58.dp)) {Text("변경 내용 확인",fontSize=18.sp)}
                                }
                            } ?: Text("보호자의 휴대폰에서 프로필을 가져와 주세요.",color=Muted)
                            s.session?.let {session -> Spacer(Modifier.height(22.dp)); InfoCard(if(session.closed) "최근 이용 종료" else "이용 기록이 남아 있어요", "${session.profileName} · ${if(session.closed) "복원 결과를 확인할 수 있어요" else "이전 설정으로 복원할 수 있어요"}") {Button(onClick=vm::session,enabled=!s.busy) {Text(if(session.closed) "결과 보기" else "이용 이어보기")}} }
                        }
                    }
                }
                "edit" -> s.selected?.let {p -> Editor(p,canEdit,{vm.save(it)}, {jsonText=profileJson.encodeToString(it);showJson=true})}
                "preview" -> Column(Modifier.fillMaxSize()) {
                    FlowHeader("적용 전 확인","이렇게 바뀌어요","${s.selected?.displayName}님의 설정으로 변경할 항목을 확인해 주세요.")
                    Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text("설정",color=Muted,fontSize=13.sp); Row(Modifier.width(290.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text("현재",color=Muted,fontSize=13.sp);Text("적용 후",color=Muted,fontSize=13.sp)} }
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)) {items(s.plan) {item -> CompareRow(item)}}
                    Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("미지원 항목은 변경하지 않습니다",color=Muted,fontSize=13.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick={s.selected?.let(vm::preview)},enabled=canEdit,modifier=Modifier.height(54.dp)) {Text("현재값 다시 확인")}; Button(onClick=vm::apply,enabled=canEdit && s.plan.any {it.actionable},modifier=Modifier.height(58.dp)) {Text("이 설정 적용",fontSize=18.sp)} }
                    }
                }
                "session" -> s.session?.let {session -> Column(Modifier.fillMaxSize()) {
                    val succeeded = session.entries.count {it.attempted && it.status.contains("성공")}
                    FlowHeader(if(session.closed) "이동 종료" else "적용 완료",if(session.closed) "이전 설정을 확인해 주세요" else "이동 환경이 준비됐어요", "${session.profileName}님의 설정 ${succeeded}개를 차량에서 확인했어요.")
                    LazyColumn(Modifier.weight(1f).padding(top=14.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {items(session.entries) {e -> SessionRow(e)}}
                    Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(if(session.closed) "복원 결과를 확인했어요." else "이동이 끝나면 이전 차량 설정으로 복원할 수 있어요.",color=Muted,fontSize=13.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick=vm::session,enabled=!s.busy,modifier=Modifier.height(54.dp)) {Text("실제 상태 새로고침")}
                            if(!session.closed) Button(onClick=vm::restorePreview,enabled=canEdit,modifier=Modifier.height(58.dp)) {Text("이전 설정으로 복원",fontSize=18.sp)}
                        }
                    }
                }}
                "restore" -> s.session?.let {session -> var keep by remember {mutableStateOf(false)}
                    Column(Modifier.fillMaxSize()) {
                        FlowHeader("이동 마무리","이전 설정으로 되돌릴까요?","이번 이용에서 변경한 항목만 복원해요.")
                        Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text("설정",color=Muted,fontSize=13.sp); Row(Modifier.width(290.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text("현재",color=Muted,fontSize=13.sp);Text("복원 후",color=Muted,fontSize=13.sp)} }
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)) {items(session.entries.filter {it.attempted}) {e ->
                            val now=s.restorePreview["${e.item.feature}:${e.item.area}"]
                            val conflict= now == "확인 불가" || !sameValue(e.item.feature,now,e.item.target) && !sameValue(e.item.feature,now,e.item.before)
                            RestoreRow(e,now,conflict)
                        }}
                        Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(keep,{keep=it},enabled=canEdit); Text("직접 변경한 값은 유지",fontSize=14.sp)}; Button(onClick={vm.restore(keep)},enabled=canEdit,modifier=Modifier.height(58.dp)) {Text("복원하기",fontSize=18.sp)} }
                    }
                }
                "diagnostics" -> Column {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("차량 연결 진단",fontSize=30.sp); Button(onClick=vm::refresh,enabled=!s.busy) {Text("다시 조회")}}
                    Text("Pleos Vehicle 2.3.2 · 실제 SDK · 모의 모드 자동 전환 없음",color=Accent,modifier=Modifier.padding(vertical=12.dp))
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {items(s.capabilities) {c -> InfoCard("${c.feature.label} · ${c.access}","${areaLabel(c.area)}\n범위 ${c.min ?: "—"} ~ ${c.max ?: "—"} · 간격 ${c.step ?: "—"} · 지원값 ${c.options.joinToString()}\n현재: ${s.current["${c.feature.name}:${c.area}"] ?: "—"}${(c.error ?: s.readErrors["${c.feature.name}:${c.area}"])?.let {"\n$it"} ?: ""}")}}
                }
            }
        }
    }
    if (showReceiver && canEdit && s.session?.closed != false) {
        ProfileReceiverDialog(
            onClose = { showReceiver = false },
            onJson = { showReceiver = false; jsonText = ""; showJson = true },
            onReview = { profile ->
                showReceiver = false
                selectedProfileId = profile.profileId
                vm.edit(profile)
            }
        )
    } else if (showReceiver) {
        LaunchedEffect(Unit) { showReceiver = false }
    }
    if(showJson) AlertDialog(onDismissRequest={showJson=false},title={Text("개발용 프로필 JSON")},text={Column {Text("JSON을 가져오면 편집 화면에서 확인 후 저장합니다."); OutlinedTextField(value=jsonText,onValueChange={if(it.toByteArray().size<=16384)jsonText=it},modifier=Modifier.fillMaxWidth().height(270.dp))}},confirmButton={TextButton(onClick={showJson=false; vm.importProfile(jsonText)},enabled=canEdit) {Text("가져와서 확인")}},dismissButton={TextButton(onClick={showJson=false}) {Text("닫기")}})
}

@Composable private fun ProfileReceiverDialog(
    onClose: () -> Unit,
    onJson: () -> Unit,
    onReview: (PassengerProfile) -> Unit
) {
    var code by remember { mutableStateOf(SimulatorProfileReceiver.newCode()) }
    var profile by remember { mutableStateOf<PassengerProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(code) {
        error = null
        try {
            profile = SimulatorProfileReceiver.receive(code)
            if (profile == null) error = "연결 번호를 새로 발급해 주세요."
        } catch (_: java.io.IOException) {
            error = "수신을 시작하지 못했어요. 다시 시도해 주세요."
        }
    }
    val received = profile
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (received == null) "iPhone에서 가져오기" else "프로필을 받았어요") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    received != null -> {
                        Text(received.displayName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(profileSummary(received))
                        Text("설정을 확인하고 차량에 저장할 수 있어요.")
                    }
                    error != null -> Text(error.orEmpty())
                    else -> {
                        Text("iPhone RideTune의 ‘차량에 보내기’에서 이 번호를 입력하세요.")
                        Text(code, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("번호는 2분 동안 사용할 수 있어요.", color = Muted)
                    }
                }
                if (received == null) TextButton(onClick = onJson) { Text("JSON으로 가져오기") }
            }
        },
        confirmButton = {
            if (received != null) Button(onClick = { onReview(received) }) { Text("받은 설정 확인") }
            else if (error != null) Button(onClick = {
                val previous = code
                do { code = SimulatorProfileReceiver.newCode() } while (code == previous)
            }) { Text("새 번호 받기") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("닫기") } }
    )
}
@Composable private fun InfoCard(title: String, body: String, content: @Composable (() -> Unit)? = null) {
    Surface(color=Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold); Text(body,color=Muted,lineHeight=23.sp); content?.invoke()}}
}
@Composable private fun SettingGroup(title: String, area: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold); Text(area,fontSize=12.sp,color=Muted) }
        Spacer(Modifier.height(7.dp)); content()
    }
}
@Composable private fun SettingLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min=62.dp).border(width=0.7.dp,color=Line,shape=RoundedCornerShape(0.dp)).padding(horizontal=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,fontSize=18.sp); Text(value,fontSize=27.sp,fontWeight=FontWeight.Medium)
    }
}
@Composable private fun FlowHeader(eyebrow: String, title: String, description: String) {
    Column {
        Text(eyebrow,color=Muted,fontSize=13.sp)
        Text(title,fontSize=34.sp,lineHeight=43.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=6.dp))
        Text(description,color=Muted,fontSize=16.sp,lineHeight=24.sp,modifier=Modifier.padding(top=8.dp))
    }
}
@Composable private fun CompareRow(p: PlanItem) {
    Row(Modifier.fillMaxWidth().border(0.7.dp,Line).padding(vertical=18.dp,horizontal=2.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(p.feature.label,fontSize=17.sp); Text(areaLabel(p.area),color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp)); if(p.note.isNotEmpty()) Text(p.note,color=Muted,fontSize=11.sp,modifier=Modifier.padding(top=3.dp)) }
        Text(prettyValue(p.feature,p.before),Modifier.width(115.dp),color=Muted,fontSize=17.sp)
        Column(Modifier.width(145.dp),horizontalAlignment=Alignment.End) { Text(prettyValue(p.feature,p.target),fontSize=20.sp,fontWeight=FontWeight.SemiBold); Text(p.reason ?: if(p.actionable) "적용 예정" else "같은 설정",color=if(p.reason == null) Accent else Color(0xFF9A4C3C),fontSize=11.sp,modifier=Modifier.padding(top=4.dp)) }
    }
}
@Composable private fun SessionRow(entry: SessionEntry) {
    Row(Modifier.fillMaxWidth().border(0.7.dp,Line).padding(vertical=18.dp,horizontal=2.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(entry.item.feature.label,fontSize=17.sp); Text(areaLabel(entry.item.area),color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp)) }
        Text(prettyValue(entry.item.feature,entry.lastObserved ?: entry.item.before),Modifier.width(145.dp),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
        Text(entry.restoreStatus ?: entry.status,Modifier.width(115.dp),color=if(entry.status.contains("실패")) Color(0xFF9A4C3C) else Accent,fontSize=13.sp)
    }
}
@Composable private fun RestoreRow(entry: SessionEntry, current: String?, conflict: Boolean) {
    Row(Modifier.fillMaxWidth().border(0.7.dp,Line).padding(vertical=18.dp,horizontal=2.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(entry.item.feature.label,fontSize=17.sp); Text(areaLabel(entry.item.area),color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp)) }
        Text(prettyValue(entry.item.feature,current),Modifier.width(115.dp),color=Muted,fontSize=17.sp)
        Column(Modifier.width(145.dp),horizontalAlignment=Alignment.End) { Text(prettyValue(entry.item.feature,entry.item.before),fontSize=20.sp,fontWeight=FontWeight.SemiBold); if(conflict) Text("수동 변경 유지",color=Accent,fontSize=11.sp,modifier=Modifier.padding(top=4.dp)) }
    }
}
private fun profileSummary(p: PassengerProfile) = listOfNotNull(p.preferences.temperatureCelsius?.let {"$it°C"},p.preferences.fanPreference?.let {"바람 ${when(it){"LOW"->"약하게";"MEDIUM"->"중간";"HIGH"->"강하게";else->"끄기"}}"},p.preferences.ambientBrightnessPercent?.let {"조명 $it%"}).joinToString("  ·  ").ifEmpty {"변경할 항목을 선택해주세요"}
@Composable private fun PlanRow(p: PlanItem) {
    Surface(color=Panel,shape=RoundedCornerShape(14.dp)) {Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text(p.feature.label,fontSize=20.sp,fontWeight=FontWeight.SemiBold); Text(areaLabel(p.area),color=Muted,fontSize=13.sp)}
        Column(Modifier.weight(1.5f)) {Text("${prettyValue(p.feature,p.before)}  →  ${prettyValue(p.feature,p.target)}",fontSize=18.sp); if(p.note.isNotEmpty()) Text(p.note,color=Accent,fontSize=13.sp)}
        Text(p.reason ?: if(p.actionable) "적용 예정" else "이미 같은 상태",Modifier.weight(1f),color=if(p.reason == null) Accent else Color(0xFF9A4C3C),fontSize=14.sp)
    }}
}
@Composable private fun Editor(original: PassengerProfile, enabled: Boolean, onSave:(PassengerProfile)->Unit, onJson:(PassengerProfile)->Unit) {
    var p by remember(original) {mutableStateOf(original)}
    fun prefs(change: Preferences) {p=p.copy(preferences=change,updatedAt=System.currentTimeMillis())}
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("이동 프로필 편집",fontSize=30.sp); Row {TextButton(onClick={onJson(p)},enabled=enabled) {Text("JSON 보기")}; Button(onClick={onSave(p)},enabled=enabled) {Text("기기에 저장")}}}
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(p.displayName,{if(it.length<=30)p=p.copy(displayName=it)},label={Text("이용자 별명")},enabled=enabled,singleLine=true)
            Choice("탑승 좌석",p.preferredSeat,ProfileCodec.seats.map {it to areaLabel(it)},enabled) {p=p.copy(preferredSeat=it!!)}
            NumberSetting("설정 온도 (°C)",p.preferences.temperatureCelsius?.toDouble(),10.0,40.0,0.5,22.0,enabled) {prefs(p.preferences.copy(temperatureCelsius=it?.toFloat()))}
            Choice("바람 세기",p.preferences.fanPreference,listOf(null to "변경 안 함","OFF" to "끄기","LOW" to "약하게","MEDIUM" to "중간","HIGH" to "강하게"),enabled) {prefs(p.preferences.copy(fanPreference=it,auto=if(it!=null)false else p.preferences.auto,power=if(it!=null)true else p.preferences.power))}
            Choice("송풍 방향",p.preferences.airflowPreference,listOf(null to "변경 안 함","FACE" to "얼굴","FLOOR" to "발","FACE_AND_FLOOR" to "얼굴 + 발"),enabled) {prefs(p.preferences.copy(airflowPreference=it,auto=if(it!=null)false else p.preferences.auto,power=if(it!=null)true else p.preferences.power))}
            Text("수동 바람을 선택하면 자동 공조 OFF와 공조 전원 ON도 미리보기에 포함됩니다.",color=Muted)
            BoolSetting("공조 전원",p.preferences.power,enabled) {prefs(p.preferences.copy(power=it))}
            BoolSetting("자동 공조",p.preferences.auto,enabled) {prefs(p.preferences.copy(auto=it))}
            BoolSetting("에어컨",p.preferences.ac,enabled) {prefs(p.preferences.copy(ac=it))}
            BoolSetting("앰비언트 조명",p.preferences.ambientEnabled,enabled) {prefs(p.preferences.copy(ambientEnabled=it))}
            NumberSetting("조명 밝기 (%)",p.preferences.ambientBrightnessPercent?.toDouble(),0.0,100.0,5.0,20.0,enabled) {prefs(p.preferences.copy(ambientBrightnessPercent=it?.toInt()))}
            Choice("조명 색상",p.preferences.ambientColorHex,listOf(null to "변경 안 함","#5588CC" to "파랑","#70B89B" to "초록","#D2A66B" to "황금","#C28BB3" to "보라","#FFFFFF" to "흰색"),enabled) {prefs(p.preferences.copy(ambientColorHex=it,ambientColorAuto=if(it!=null)false else p.preferences.ambientColorAuto))}
            BoolSetting("주행 모드 연동 색상",p.preferences.ambientColorAuto,enabled) {prefs(p.preferences.copy(ambientColorAuto=it))}
            Text("차량의 실제 지원 범위는 적용 미리보기에서 확인합니다. 설정은 개인의 선호이며 감각적 효과를 보장하지 않습니다.",color=Muted)
        }
    }
}
@Composable private fun Choice(label: String, value:String?, choices:List<Pair<String?,String>>, enabled:Boolean, changed:(String?)->Unit) {
    Column {Text(label,fontSize=17.sp); Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {choices.forEach {(key,text)->FilterChip(selected=value==key,onClick={changed(key)},label={Text(text,Modifier.padding(vertical=7.dp))},enabled=enabled)}}}
}
@Composable private fun BoolSetting(label:String,v:Boolean?,enabled:Boolean,change:(Boolean?)->Unit) = Choice(label,v?.toString(),listOf(null to "변경 안 함","true" to "켜기","false" to "끄기"),enabled) {change(it?.toBooleanStrict())}
@Composable private fun NumberSetting(label:String,v:Double?,min:Double,max:Double,step:Double,initial:Double,enabled:Boolean,change:(Double?)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(label,Modifier.width(190.dp),fontSize=17.sp)
        FilterChip(selected=v==null,onClick={change(if(v==null)initial else null)},label={Text(if(v==null) "변경 안 함" else "설정 사용")},enabled=enabled)
        OutlinedButton(onClick={change(((v?:initial)-step).coerceAtLeast(min))},enabled=enabled && v!=null) {Text("−")}
        Text(v?.toString() ?: "—",fontSize=22.sp)
        OutlinedButton(onClick={change(((v?:initial)+step).coerceAtMost(max))},enabled=enabled && v!=null) {Text("+")}
    }
}
