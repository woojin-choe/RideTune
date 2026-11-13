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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
private val CalmBlue = Color(0xFF4A6B82)
private val CalmGreen = Color(0xFF4F7A65)
private val scheme = lightColorScheme(primary=Accent,onPrimary=Color.White,background=Canvas,onBackground=Ink,surface=Canvas,onSurface=Ink,surfaceVariant=Panel,onSurfaceVariant=Ink,error=Color(0xFF9A4C3C))

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val debugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openReceiver = debugBuild && intent.getBooleanExtra("open_profile_receiver", false)
        setContent { MaterialTheme(colorScheme=scheme) { RideApp(openReceiver=openReceiver) } }
    }
}

@Composable private fun RideApp(openReceiver: Boolean, vm: RideViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val canEdit = s.parked == true && !s.busy && !s.storageError
    var showJson by remember {mutableStateOf(false)}
    var jsonText by remember {mutableStateOf("")}
    var showReceiver by remember { mutableStateOf(false) }
    var pendingReceiver by remember { mutableStateOf(openReceiver) }
    LaunchedEffect(pendingReceiver, canEdit) {
        if (pendingReceiver && canEdit) {
            showReceiver = true
            pendingReceiver = false
        }
    }
    val debugBuild = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var selectedProfileId by remember(s.profiles.map { it.profileId }) {
        mutableStateOf(s.profiles.firstOrNull { it.profileId == s.selected?.profileId }?.profileId
            ?: s.profiles.firstOrNull()?.profileId)
    }
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity) {
        Density(density = baseDensity.density * 1.42f, fontScale = baseDensity.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
    Surface(Modifier.fillMaxSize(),color=Canvas) {
        Column(Modifier.fillMaxSize().padding(horizontal=44.dp, vertical=28.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("RideTune",fontSize=30.sp,fontWeight=FontWeight.Bold)
                    Surface(color=Panel,shape=RoundedCornerShape(14.dp)) {
                        Text("AI 안심 대화 단짝 & 감각 동조",Modifier.padding(horizontal=12.dp,vertical=6.dp),fontSize=13.sp,color=Muted,fontWeight=FontWeight.Medium)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(s.connected) "● Pleos 연결됨" else "○ 차량 연결 확인 중",fontSize=15.sp,color=if(s.connected) Accent else Muted)
                    Surface(color=Panel,shape=RoundedCornerShape(24.dp)) {Text(when(s.parked) {true->"P · 설정 가능"; false->"주행 중 · AI 대화 가능"; null->"기어 확인 안 됨"},Modifier.padding(horizontal=16.dp,vertical=10.dp),fontSize=15.sp,fontWeight=FontWeight.Medium)}
                    if(s.screen != "companion") OutlinedButton(onClick={
                        val chosen = s.profiles.firstOrNull { it.profileId == selectedProfileId } ?: s.selected ?: s.profiles.firstOrNull()
                        vm.openCompanion(chosen)
                    },enabled=!s.busy) {Text("AI 대화 단짝",fontSize=15.sp)}
                    if(s.screen != "home") OutlinedButton(onClick=vm::home,enabled=!s.busy) {Text("홈",fontSize=15.sp)}
                    TextButton(onClick=vm::diagnostics,enabled=!s.busy) {Text("진단",fontSize=15.sp)}
                }
            }
            HorizontalDivider(Modifier.padding(top=14.dp,bottom=14.dp),color=Line)
            if(s.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }
            s.message?.let {message -> Surface(color=Panel,shape=RoundedCornerShape(16.dp)) {Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {Text(message,Modifier.weight(1f),fontSize=15.sp); TextButton(onClick=vm::dismiss) {Text("닫기",fontSize=15.sp)}}}; Spacer(Modifier.height(12.dp))}
            Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
            when(s.screen) {
                "home" -> {
                    val selected = s.profiles.firstOrNull { it.profileId == selectedProfileId } ?: s.profiles.firstOrNull()
                    Row(Modifier.fillMaxWidth(0.96f),horizontalArrangement=Arrangement.spacedBy(40.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(0.82f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center) {
                            Text("탑승자",fontSize=26.sp,fontWeight=FontWeight.Bold)
                            Text("저장된 프로필 ${s.profiles.size}",color=Muted,fontSize=15.sp,modifier=Modifier.padding(top=4.dp,bottom=16.dp))
                            s.profiles.forEach {p ->
                                val chosen = p.profileId == selected?.profileId
                                Surface(color=if(chosen) Accent else Panel,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().padding(bottom=10.dp).clickable(enabled=!s.busy) { selectedProfileId=p.profileId }) {
                                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Surface(color=Canvas,shape=RoundedCornerShape(30.dp)) { Text(p.displayName.take(1),Modifier.padding(horizontal=14.dp,vertical=10.dp),fontSize=18.sp,fontWeight=FontWeight.Bold,color=Ink) }
                                        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                                            Text(p.displayName,fontSize=21.sp,fontWeight=FontWeight.SemiBold,color=if(chosen) Color.White else Ink)
                                            Text(profileSummary(p),fontSize=13.sp,color=if(chosen) Color.White else Muted,modifier=Modifier.padding(top=3.dp))
                                            Text("관심사: ${p.interestTopic} · 창밖: ${p.weatherScene}",fontSize=12.sp,color=if(chosen) Color(0xFFEAE4DC) else Muted,modifier=Modifier.padding(top=2.dp))
                                        }
                                        if(chosen) Text("✓",color=Color.White,fontSize=20.sp)
                                    }
                                }
                            }
                            OutlinedButton(onClick={
                                if (debugBuild) showReceiver = true else { jsonText = ""; showJson = true }
                            },enabled=canEdit,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp)) {Text("iPhone에서 프로필 가져오기",fontSize=16.sp,fontWeight=FontWeight.SemiBold)}
                            Spacer(Modifier.height(20.dp)); HorizontalDivider(color=Line)
                            Text("보호자 메모 · AI 대화 가이드",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=14.dp,bottom=4.dp))
                            Text(selected?.comfortPhrase ?: "새로운 차량에 적응할 시간을 주세요.",fontSize=15.sp,lineHeight=22.sp)
                        }
                        Column(Modifier.weight(1.55f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center) {
                            Text("탑승 준비 & AI 단짝",color=Muted,fontSize=15.sp,fontWeight=FontWeight.Medium)
                            Text("${selected?.displayName ?: "탑승자"}님의 이동 환경",fontSize=36.sp,lineHeight=44.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp))
                            Text("감각 환경과 AI 대화 단짝 설정을 확인해 주세요.",color=Muted,fontSize=16.sp,modifier=Modifier.padding(top=6.dp,bottom=22.dp))
                            selected?.let {p ->
                                SettingGroup("공조 · 실내조명", "앞좌석 공통 · 차량 전체") {
                                    SettingLine("온도 · 바람", "${p.preferences.temperatureCelsius?.let {"${it}°C"} ?: "유지"} · ${p.preferences.fanPreference?.let { when(it) {"LOW" -> "약하게(1단)"; "MEDIUM" -> "중간"; "HIGH" -> "강하게"; else -> "끄기"} } ?: "유지"}")
                                    SettingLine("조명 밝기", p.preferences.ambientBrightnessPercent?.let {"${it}%"} ?: "변경 안 함")
                                }
                                Spacer(Modifier.height(20.dp))
                                SettingGroup("AI 안심 대화 단짝", "반복 질문 무제한 응대 · 창밖 날씨 번역") {
                                    SettingLine("좋아하는 주제", p.interestTopic)
                                    SettingLine("창밖 상황", p.weatherScene)
                                }
                                Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                    TextButton(onClick={vm.edit(p)},enabled=canEdit) {Text("이번 이동 설정 조정",fontSize=16.sp,fontWeight=FontWeight.Medium)}
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick={vm.openCompanion(p)},enabled=!s.busy,modifier=Modifier.height(58.dp),shape=RoundedCornerShape(18.dp)) {
                                            Text("AI 안심 대화 단짝 열기",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                                        }
                                        Button(onClick={vm.preview(p)},enabled=canEdit && s.session?.closed != false,modifier=Modifier.height(58.dp).widthIn(min=175.dp),shape=RoundedCornerShape(18.dp)) {
                                            Text("변경 내용 확인",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                                        }
                                    }
                                }
                            } ?: Text("보호자의 휴대폰에서 프로필을 가져와 주세요.",color=Muted,fontSize=17.sp)
                            s.session?.let {session -> Spacer(Modifier.height(18.dp)); InfoCard(if(session.closed) "최근 이용 종료" else "이용 기록이 남아 있어요", "${session.profileName} · ${if(session.closed) "복원 결과를 확인할 수 있어요" else "이전 설정으로 복원할 수 있어요"}") {Button(onClick=vm::session,enabled=!s.busy) {Text(if(session.closed) "결과 보기" else "이용 이어보기")}} }
                        }
                    }
                }
                "companion" -> CompanionScreen(s, vm)
                "received" -> s.selected?.let { profile ->
                    Column(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                        FlowHeader("iPhone에서 전송됨", "${profile.displayName}님의 프로필을 받았어요",
                            "이번 이동에 사용할 감각 환경과 AI 대화 설정이에요.")
                        InfoCard("공조 · 실내조명", profileSummary(profile))
                        InfoCard("AI 안심 대화 단짝", "관심 주제: ${profile.interestTopic} · 창밖: ${profile.weatherScene}")
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick={vm.edit(profile)},enabled=canEdit) { Text("편집 및 저장") }
                            Button(onClick={vm.preview(profile)},enabled=canEdit && s.session?.closed != false) {
                                Text("차량 변경 내용 확인")
                            }
                        }
                    }
                }
                "edit" -> Box(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.94f),contentAlignment=Alignment.Center) { s.selected?.let {p -> Editor(p,canEdit,{vm.save(it)}, {jsonText=profileJson.encodeToString(it);showJson=true})} }
                "preview" -> Column(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f),verticalArrangement=Arrangement.Center) {
                    FlowHeader("적용 전 확인","이렇게 바뀌어요","${s.selected?.displayName}님의 설정으로 변경할 항목을 확인해 주세요.")
                    Row(Modifier.fillMaxWidth().padding(top=22.dp,bottom=10.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text("설정",color=Muted,fontSize=15.sp); Row(Modifier.width(310.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text("현재",color=Muted,fontSize=15.sp);Text("적용 후",color=Muted,fontSize=15.sp)} }
                    HorizontalDivider(color=Line)
                    LazyColumn(Modifier.weight(1f,fill=false),verticalArrangement=Arrangement.spacedBy(0.dp)) {items(s.plan) {item -> CompareRow(item)}}
                    Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("미지원 항목은 변경하지 않습니다",color=Muted,fontSize=15.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) { OutlinedButton(onClick={s.selected?.let(vm::preview)},enabled=canEdit,modifier=Modifier.height(58.dp),shape=RoundedCornerShape(16.dp)) {Text("현재값 다시 확인",fontSize=16.sp)}; Button(onClick=vm::apply,enabled=canEdit && s.plan.any {it.actionable},modifier=Modifier.height(62.dp).widthIn(min=180.dp),shape=RoundedCornerShape(18.dp)) {Text("이 설정 적용",fontSize=19.sp,fontWeight=FontWeight.SemiBold)} }
                    }
                }
                "session" -> s.session?.let {session -> Column(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f),verticalArrangement=Arrangement.Center) {
                    val succeeded = session.entries.count {it.attempted && it.status.contains("확인")}
                    FlowHeader(if(session.closed) "이동 종료" else "적용 완료",if(session.closed) "이전 설정을 확인해 주세요" else "이동 환경이 준비됐어요", "${session.profileName}님의 설정 ${succeeded}개를 차량에서 확인했어요.")
                    Spacer(Modifier.height(16.dp)); HorizontalDivider(color=Line)
                    LazyColumn(Modifier.weight(1f,fill=false),verticalArrangement=Arrangement.spacedBy(0.dp)) {items(session.entries) {e -> SessionRow(e)}}
                    Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(if(session.closed) "복원 결과를 확인했어요." else "이동 중에는 AI 안심 대화 단짝과 함께하고, 도착 후 이전 설정으로 복원할 수 있어요.",color=Muted,fontSize=15.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            if(!session.closed) OutlinedButton(onClick={vm.openCompanion(s.selected)},enabled=!s.busy,modifier=Modifier.height(58.dp),shape=RoundedCornerShape(16.dp)) {Text("AI 안심 대화 단짝 시작",fontSize=16.sp,fontWeight=FontWeight.SemiBold)}
                            OutlinedButton(onClick=vm::session,enabled=!s.busy,modifier=Modifier.height(58.dp),shape=RoundedCornerShape(16.dp)) {Text("상태 새로고침",fontSize=16.sp)}
                            if(!session.closed) Button(onClick=vm::restorePreview,enabled=canEdit,modifier=Modifier.height(62.dp).widthIn(min=185.dp),shape=RoundedCornerShape(18.dp)) {Text("이전 설정으로 복원",fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
                        }
                    }
                }}
                "restore" -> s.session?.let {session -> var keep by remember {mutableStateOf(false)}
                    Column(Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f),verticalArrangement=Arrangement.Center) {
                        FlowHeader("이동 마무리","이전 설정으로 되돌릴까요?","이번 이용에서 변경한 항목만 복원해요.")
                        Row(Modifier.fillMaxWidth().padding(top=22.dp,bottom=10.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text("설정",color=Muted,fontSize=15.sp); Row(Modifier.width(310.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text("현재",color=Muted,fontSize=15.sp);Text("복원 후",color=Muted,fontSize=15.sp)} }
                        HorizontalDivider(color=Line)
                        LazyColumn(Modifier.weight(1f,fill=false),verticalArrangement=Arrangement.spacedBy(0.dp)) {items(session.entries.filter {it.attempted}) {e ->
                            val now=s.restorePreview["${e.item.feature}:${e.item.area}"]
                            val conflict= now == "확인 불가" || !sameValue(e.item.feature,now,e.item.target) && !sameValue(e.item.feature,now,e.item.before)
                            RestoreRow(e,now,conflict)
                        }}
                        Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(keep,{keep=it},enabled=canEdit); Text("직접 변경한 값은 유지",fontSize=16.sp)}; Button(onClick={vm.restore(keep)},enabled=canEdit,modifier=Modifier.height(62.dp).widthIn(min=180.dp),shape=RoundedCornerShape(18.dp)) {Text("복원하기",fontSize=19.sp,fontWeight=FontWeight.SemiBold)} }
                    }
                }
                "diagnostics" -> Column(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.94f)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("차량 연결 진단",fontSize=32.sp,fontWeight=FontWeight.Bold); Button(onClick=vm::refresh,enabled=!s.busy) {Text("다시 조회",fontSize=16.sp)}}
                    Text("Pleos Vehicle 2.3.2 · 실제 SDK · 모의 모드 자동 전환 없음",color=Accent,fontSize=15.sp,modifier=Modifier.padding(vertical=12.dp))
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {items(s.capabilities) {c -> InfoCard("${c.feature.label} · ${c.access}","${areaLabel(c.area)}\n범위 ${c.min ?: "—"} ~ ${c.max ?: "—"} · 간격 ${c.step ?: "—"} · 지원값 ${c.options.joinToString()}\n현재: ${s.current["${c.feature.name}:${c.area}"] ?: "—"}${(c.error ?: s.readErrors["${c.feature.name}:${c.area}"])?.let {"\n$it"} ?: ""}")}}
                }
            }
            }
        }
    }
    if (showReceiver && canEdit) {
        ProfileReceiverDialog(
            onClose = { showReceiver = false },
            onJson = { showReceiver = false; jsonText = ""; showJson = true },
            onReceived = { profile ->
                showReceiver = false
                vm.showReceivedProfile(profile)
            }
        )
    } else if (showReceiver) {
        LaunchedEffect(Unit) { showReceiver = false }
    }
    if(showJson) AlertDialog(onDismissRequest={showJson=false},title={Text("개발용 프로필 JSON")},text={Column {Text("JSON을 가져오면 편집 화면에서 확인 후 저장합니다."); OutlinedTextField(value=jsonText,onValueChange={if(it.toByteArray().size<=16384)jsonText=it},modifier=Modifier.fillMaxWidth().height(270.dp))}},confirmButton={TextButton(onClick={showJson=false; vm.importProfile(jsonText)},enabled=canEdit) {Text("가져와서 확인")}},dismissButton={TextButton(onClick={showJson=false}) {Text("닫기")}})
    }
}

@Composable private fun CompanionScreen(s: RideState, vm: RideViewModel) {
    val p = s.selected ?: s.profiles.firstOrNull() ?: ProfileCodec.examples().first()
    val latest = s.companionTurns.firstOrNull() ?: AiCompanionEngine.openingTurn(p, s.parked, s.current)
    Row(Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.95f), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(0.95f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.displayName}님의 AI 단짝", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        FilterChip(
                            selected = s.voiceEnabled,
                            onClick = vm::toggleVoice,
                            label = { Text(if (s.voiceEnabled) "🔊 음성 켜짐" else "🔇 글자만") }
                        )
                    }
                    Text("관심 주제: ${p.interestTopic}  ·  창밖: ${p.weatherScene}", fontSize = 14.sp, color = Muted)
                    Text("반복 질문 안심 응답: ${s.repeatCount}회 연속 일정한 톤 유지 중", fontSize = 14.sp, color = CalmBlue, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("1. 반복 질문 & 창밖 상황 안심 대화", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Button(
                onClick = vm::askRepeatQuestion,
                enabled = !s.busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("🔄 “언제 도착해? 또 알려줘!” (${s.repeatCount + 1}번째 질문)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiCompanionEngine.weatherPresets.forEach { w ->
                    OutlinedButton(
                        onClick = { vm.explainWindowWeather(w) },
                        enabled = !s.busy,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("🪟 $w", fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("2. 대화 중 감각 과부하 즉시 완화 (Pleos 실시간 제어)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.triggerSensorySos("NOISE") },
                    enabled = !s.busy,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("🔊 바람 소리 줄이기", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                OutlinedButton(
                    onClick = { vm.triggerSensorySos("LIGHT") },
                    enabled = !s.busy,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("✨ 눈부심 낮추기", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.triggerSensorySos("STUFFY") },
                    enabled = !s.busy,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("🥵 시원한 바람 켜기", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = { vm.triggerSensorySos("BREATHE") },
                    enabled = !s.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = CalmBlue),
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("🌊 숨 쉬는 차 (호흡 진정)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Column(Modifier.weight(1.35f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            s.breathingPhase?.let { phase ->
                Surface(color = CalmBlue, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("🌊 $phase", Modifier.padding(16.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(latest.speakerLabel, fontSize = 14.sp, color = CalmBlue, fontWeight = FontWeight.SemiBold)
                    Text(latest.childUtterance, fontSize = 16.sp, color = Muted)
                    HorizontalDivider(color = Line)
                    Text(latest.aiReply, fontSize = 23.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Surface(color = Canvas, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("예측 가능한 이동 순서 (First – Then)", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                            Text("① ${latest.firstStep}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("② ${latest.thenStep}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CalmGreen)
                        }
                    }
                    latest.vehicleEffect?.let { effect ->
                        Surface(color = Color(0xFFDCE6EC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("🚗 $effect", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            if (s.companionTurns.size > 1) {
                Text("최근 안심 대화 기록", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                s.companionTurns.drop(1).take(3).forEach { turn ->
                    Surface(color = Panel.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(turn.speakerLabel, fontSize = 12.sp, color = Muted)
                            Text(turn.aiReply, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ProfileReceiverDialog(
    onClose: () -> Unit,
    onJson: () -> Unit,
    onReceived: (PassengerProfile) -> Unit
) {
    var code by remember { mutableStateOf(SimulatorProfileReceiver.newCode()) }
    val latestOnReceived by rememberUpdatedState(onReceived)
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(code) {
        error = null
        try {
            val profile = SimulatorProfileReceiver.receive(code)
            if (profile == null) error = "연결 번호를 새로 발급해 주세요."
            else latestOnReceived(profile)
        } catch (_: java.io.IOException) {
            error = "수신을 시작하지 못했어요. 다시 시도해 주세요."
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("iPhone에서 가져오기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    error != null -> Text(error.orEmpty())
                    else -> {
                        Text("iPhone RideTune의 ‘차량에 보내기’에서 이 번호를 입력하세요.")
                        Text(code, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("번호는 2분 동안 사용할 수 있어요.", color = Muted)
                    }
                }
                TextButton(onClick = onJson) { Text("JSON으로 가져오기") }
            }
        },
        confirmButton = {
            if (error != null) Button(onClick = {
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
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold); Text(area,fontSize=13.sp,color=Muted) }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color=Line)
        content()
    }
}
@Composable private fun SettingLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,fontSize=19.sp); Text(value,fontSize=24.sp,fontWeight=FontWeight.SemiBold)
        }
        HorizontalDivider(color=Line)
    }
}
@Composable private fun FlowHeader(eyebrow: String, title: String, description: String) {
    Column {
        Text(eyebrow,color=Muted,fontSize=15.sp,fontWeight=FontWeight.Medium)
        Text(title,fontSize=36.sp,lineHeight=44.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))
        Text(description,color=Muted,fontSize=17.sp,lineHeight=25.sp,modifier=Modifier.padding(top=8.dp))
    }
}
@Composable private fun CompareRow(p: PlanItem) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical=20.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(p.feature.label,fontSize=19.sp,fontWeight=FontWeight.Medium); Text(areaLabel(p.area),color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=4.dp)); if(p.note.isNotEmpty()) Text(p.note,color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=3.dp)) }
            Text(prettyValue(p.feature,p.before),Modifier.width(125.dp),color=Muted,fontSize=19.sp)
            Column(Modifier.width(155.dp),horizontalAlignment=Alignment.End) { Text(prettyValue(p.feature,p.target),fontSize=22.sp,fontWeight=FontWeight.SemiBold); Text(p.reason ?: if(p.actionable) "적용 예정" else "같은 설정",color=if(p.reason == null) Accent else Color(0xFF9A4C3C),fontSize=13.sp,modifier=Modifier.padding(top=4.dp)) }
        }
        HorizontalDivider(color=Line)
    }
}
@Composable private fun SessionRow(entry: SessionEntry) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical=20.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(entry.item.feature.label,fontSize=19.sp,fontWeight=FontWeight.Medium); Text(areaLabel(entry.item.area),color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=4.dp)) }
            Text(prettyValue(entry.item.feature,entry.lastObserved ?: entry.item.before),Modifier.width(155.dp),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Text(entry.restoreStatus ?: entry.status,Modifier.width(125.dp),color=if(entry.status.contains("실패")) Color(0xFF9A4C3C) else Accent,fontSize=15.sp,fontWeight=FontWeight.Medium)
        }
        HorizontalDivider(color=Line)
    }
}
@Composable private fun RestoreRow(entry: SessionEntry, current: String?, conflict: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical=20.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(entry.item.feature.label,fontSize=19.sp,fontWeight=FontWeight.Medium); Text(areaLabel(entry.item.area),color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=4.dp)) }
            Text(prettyValue(entry.item.feature,current),Modifier.width(125.dp),color=Muted,fontSize=19.sp)
            Column(Modifier.width(155.dp),horizontalAlignment=Alignment.End) { Text(prettyValue(entry.item.feature,entry.item.before),fontSize=22.sp,fontWeight=FontWeight.SemiBold); if(conflict) Text("수동 변경 유지",color=Accent,fontSize=13.sp,modifier=Modifier.padding(top=4.dp)) }
        }
        HorizontalDivider(color=Line)
    }
}
private fun profileSummary(p: PassengerProfile) = listOfNotNull(p.preferences.temperatureCelsius?.let {"$it°C"},p.preferences.fanPreference?.let {"바람 ${when(it){"LOW"->"약하게";"MEDIUM"->"중간";"HIGH"->"강하게";else->"끄기"}}"},p.preferences.ambientBrightnessPercent?.let {"조명 $it%"}).joinToString("  ·  ").ifEmpty {"변경할 항목을 선택해주세요"}

@Composable private fun Editor(original: PassengerProfile, enabled: Boolean, onSave:(PassengerProfile)->Unit, onJson:(PassengerProfile)->Unit) {
    var p by remember(original) {mutableStateOf(original)}
    fun prefs(change: Preferences) {p=p.copy(preferences=change,updatedAt=System.currentTimeMillis())}
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("이동 프로필 & AI 단짝 편집",fontSize=30.sp,fontWeight=FontWeight.Bold); Row {TextButton(onClick={onJson(p)},enabled=enabled) {Text("JSON 보기")}; Button(onClick={onSave(p)},enabled=enabled) {Text("기기에 저장")}}}
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(p.displayName,{if(it.length<=30)p=p.copy(displayName=it)},label={Text("이용자 별명")},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth())
            Choice("AI 대화 관심 주제 (특별 관심사)",p.interestTopic,AiCompanionEngine.interestPresets.map {it to it},enabled) {if(it!=null) p=p.copy(interestTopic=it)}
            OutlinedTextField(p.interestTopic,{if(it.length<=60)p=p.copy(interestTopic=it)},label={Text("관심 주제 직접 입력 (예: 바다와 고래, 2호선 지하철)")},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth())
            Choice("창밖 날씨·상황",p.weatherScene,AiCompanionEngine.weatherPresets.map {it to it},enabled) {if(it!=null) p=p.copy(weatherScene=it)}
            OutlinedTextField(p.comfortPhrase,{if(it.length<=100)p=p.copy(comfortPhrase=it)},label={Text("반복 질문 시 안심 약속 문구")},enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth())
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
