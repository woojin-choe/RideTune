package com.humanfirst.ridetune.vehicle

data class CompanionTurn(
    val speakerLabel: String,
    val childUtterance: String,
    val aiReply: String,
    val firstStep: String,
    val thenStep: String,
    val vehicleEffect: String? = null
)

object AiCompanionEngine {
    val interestPresets = listOf("바다와 고래", "공룡과 나무", "기차와 역 이름", "우주와 별자리")
    val weatherPresets = listOf("비 오는 길", "깜깜한 밤·터널", "막히는 도로", "맑은 날")

    private fun address(name: String): String {
        val trimmed = name.trim()
        val last = trimmed.lastOrNull() ?: return ""
        if (last !in '가'..'힣') return trimmed
        return trimmed + if ((last - '가') % 28 == 0) "야" else "아"
    }

    fun openingTurn(profile: PassengerProfile, parked: Boolean?, current: Map<String, String>): CompanionTurn {
        val name = address(profile.displayName)
        val topic = profile.interestTopic.ifBlank { "바다와 고래" }
        val weather = profile.weatherScene.ifBlank { "맑은 날" }
        val temp = profile.preferences.temperatureCelsius?.let { "${it}°C" }
            ?: current["TEMPERATURE:${profile.preferredSeat}"]?.let { "${it}°C" }
            ?: "편안한 온도"
        val light = profile.preferences.ambientBrightnessPercent?.let { "${it}%" } ?: "은은한 밝기"
        val gearState = if (parked == false) "천천히 달리는 중" else "출발 준비 중(P단)"
        val storyIntro = when {
            topic.contains("고래") || topic.contains("바다") ->
                "$name, 안녕! 나는 오늘 함께 타고 갈 바닷속 고래 친구야. 차 안을 좋아하는 파란 바닷속($temp · 조명 $light)처럼 편안하게 맞춰 두었어."
            topic.contains("공룡") || topic.contains("숲") || topic.contains("나무") ->
                "$name, 반가워! 나는 오늘 함께 이동할 초록 숲 아기 공룡 친구야. 차 안을 시원한 나무 그늘($temp · 조명 $light)처럼 맞춰 두었어."
            topic.contains("기차") || topic.contains("지하철") || topic.contains("역") ->
                "$name, 탑승 완료! 오늘 우리 특별 열차는 정해진 선로를 따라 안전하게 이동할 거야. 객실 온도는 $temp, 조명은 $light 로 정확히 맞췄어."
            else ->
                "$name, 안녕! 오늘 이동하는 동안 내가 옆에서 ${topic} 이야기를 계속 들려줄게."
        }
        return CompanionTurn(
            speakerLabel = "AI 단짝 오프닝 · $weather ($gearState)",
            childUtterance = "차에 탔어요. 오늘도 나랑 계속 이야기해 줄 거죠?",
            aiReply = "$storyIntro ${profile.comfortPhrase}. 궁금한 게 생기면 나한테 이야기해 줘.",
            firstStep = "지금(First): 편안한 바람($temp) 속에서 AI 단짝과 ${topic} 이야기 나누기",
            thenStep = "다음(Then): ${profile.comfortPhrase}",
            vehicleEffect = "프로필 환경 준비 ($temp · 조명 $light · 창밖: $weather)"
        )
    }

    fun repeatQuestionTurn(profile: PassengerProfile, count: Int, parked: Boolean?): CompanionTurn {
        val name = address(profile.displayName)
        val topic = profile.interestTopic.ifBlank { "바다와 고래" }
        val facts = when {
            topic.contains("고래") || topic.contains("바다") -> listOf(
                "혹등고래도 먼 바다를 갈 때 노래를 부르면서 천천히 헤엄친대.",
                "푸른바다거북은 파도가 쳐도 서두르지 않고 자기 속도로 헤엄쳐.",
                "돌고래 가족은 이동할 때 서로 나란히 붙어서 안전하게 헤엄친대.",
                "바닷속 잠수함은 바깥에 비가 와도 안쪽은 아주 조용하고 따뜻해."
            )
            topic.contains("공룡") || topic.contains("나무") -> listOf(
                "목을 긴 브라키오사우루스는 키 큰 나무 꼭대기 나뭇잎을 천천히 먹었대.",
                "트리케라톱스는 튼튼한 뿔이 있어서 어떤 길도 안심하고 걸어갔어.",
                "초록 숲속 나무들은 바람이 불어도 뿌리가 단단해서 흔들리지 않아.",
                "아기 공룡들도 먼 길을 갈 때 발자국 숫자를 세면서 걸어갔대."
            )
            topic.contains("기차") || topic.contains("역") -> listOf(
                "기차는 정해진 철길을 따라서만 달리기 때문에 길을 잃지 않아.",
                "신호등이 잠깐 멈추라고 하면 기차도 역에서 숨을 고르고 다시 출발해.",
                "기관사님이 앞을 잘 보고 계셔서 우리는 객실에서 편하게 쉬면 돼.",
                "이번 역을 지나 다음 역으로 가는 것처럼 순서대로 안전하게 가고 있어."
            )
            else -> listOf(
                "밤하늘의 북극성은 언제나 같은 자리에서 길을 알려준대.",
                "우주선은 바깥이 깜깜해도 안쪽은 따뜻하고 안전한 불빛이 켜져 있어.",
                "별자리들은 서로 선으로 이어져 있어서 어디서든 길을 찾을 수 있어.",
                "달님도 우리가 가는 길을 따라 창밖에서 같이 걸어가고 있어."
            )
        }
        val fact = facts[(count - 1).coerceAtLeast(0) % facts.size]
        val moveText = when (parked) {
            true -> "지금은 차가 잠깐 멈춰 있어."
            false -> "지금은 목적지로 가는 중이야."
            null -> "지금은 차가 어디쯤인지 확인하고 있어."
        }
        val reassurance = if (count <= 1) "언제 도착하는지 궁금했구나. 같이 확인해 보자."
            else "응, 다시 알려줄게. 우리 같이 확인해 보자."
        val comfort = profile.comfortPhrase.trim().trimEnd('.', '!', '?')
        return CompanionTurn(
            speakerLabel = "반복 질문 안심 응답 · ${count}번째 질문",
            childUtterance = "“언제 도착해? 지금 어디야? 또 알려줘!” (${count}번째 질문)",
            aiReply = "$name, $reassurance $moveText $comfort. $fact",
            firstStep = "지금(First): ${topic} 이야기 듣기",
            thenStep = "다음(Then): ${profile.comfortPhrase}",
            vehicleEffect = "${count}번째 질문에도 이동 순서와 관심 주제를 함께 안내"
        )
    }

    fun windowWeatherTurn(profile: PassengerProfile, weather: String, parked: Boolean?): Pair<CompanionTurn, Preferences> {
        val name = address(profile.displayName)
        val gearTag = if (parked == false) "주행 중" else "정차(P단)"
        return when {
            weather.contains("비") -> CompanionTurn(
                speakerLabel = "창밖 날씨 안심 중계 ($gearTag) · 🌧️ 비 오는 길",
                childUtterance = "“창밖에 비가 오고 소리가 나요. 왜 이래요?”",
                aiReply = "$name, 창밖 소리가 궁금했구나. 빗방울이 창문에 톡톡 닿는 소리야. 우리 차 안에서 따뜻한 불빛을 보면서 같이 가자.",
                firstStep = "지금(First): 창밖 빗방울을 구경하며 따뜻한 오두막 조명(25%) 켜기",
                thenStep = "다음(Then): 빗소리를 자장가 삼아 편안하게 이동하기",
                vehicleEffect = "Pleos 연동: 창밖 우천 대응 → 온도 23.0°C · 바람 약하게(1단) · 따뜻한 황금 조명(#D2A66B, 25%)"
            ) to profile.preferences.copy(
                power = true, auto = false, temperatureCelsius = 23.0f, fanPreference = "LOW",
                ambientEnabled = true, ambientBrightnessPercent = 25, ambientColorHex = "#D2A66B", ambientColorAuto = false
            )

            weather.contains("밤") || weather.contains("터널") -> CompanionTurn(
                speakerLabel = "창밖 환경 안심 중계 · 🌙 깜깜한 밤·터널",
                childUtterance = "“창밖이 갑자기 깜깜해졌어요!”",
                aiReply = "$name, 창밖이 어두워져서 놀랐구나. 긴 동굴(터널)이나 밤길을 지날 때는 원래 바깥이 잠깐 밤하늘처럼 어두워져. 놀라지 않게 차 안 불빛을 은은한 파란 별빛(#5588CC, 30%)으로 밝혀 두었어. 금방 다시 밝은 길이 나올 거야!",
                firstStep = "지금(First): 차 안의 파란 별빛 조명(30%)을 보며 안심하기",
                thenStep = "다음(Then): 어두운 구간을 지나 목적지로 안전하게 이동하기",
                vehicleEffect = "Pleos 연동: 야간·터널 어둠 완충 → 온도 22.0°C · 바람 1단 · 안심 파란 조명(#5588CC, 30%)"
            ) to profile.preferences.copy(
                power = true, auto = false, temperatureCelsius = 22.0f, fanPreference = "LOW",
                ambientEnabled = true, ambientBrightnessPercent = 30, ambientColorHex = "#5588CC", ambientColorAuto = false
            )

            weather.contains("막히") || weather.contains("도로") -> CompanionTurn(
                speakerLabel = "창밖 환경 안심 중계 · 🚗 정체 구간",
                childUtterance = "“차가 왜 자꾸 멈춰요? 왜 안 가요?”",
                aiReply = "$name, 지금 창밖에 자동차 친구들이 줄을 서서 차례차례 가고 있어서 잠깐씩 멈추는 거야. 고장 난 게 아니니까 안심해도 돼! 답답하지 않게 차 안 공기를 시원한 숲속 바람(21.5°C)과 초록빛(#70B89B)으로 바꿔줄게.",
                firstStep = "지금(First): 앞차와 차례대로 줄 서서 천천히 이동하기",
                thenStep = "다음(Then): 길이 뚫리면 다시 쌩쌩 달려서 도착하기",
                vehicleEffect = "Pleos 연동: 정체 구간 답답함 해소 → 온도 21.5°C · 바람 1단 · 숲속 초록 조명(#70B89B, 20%)"
            ) to profile.preferences.copy(
                power = true, auto = false, temperatureCelsius = 21.5f, fanPreference = "LOW",
                ambientEnabled = true, ambientBrightnessPercent = 20, ambientColorHex = "#70B89B", ambientColorAuto = false
            )

            else -> CompanionTurn(
                speakerLabel = "창밖 풍경 안심 중계 · ☀️ 맑은 날",
                childUtterance = "“창밖에 구름이랑 나무가 지나가요!”",
                aiReply = "$name, 오늘 창밖 날씨가 참 맑지? 창밖 멀리 보이는 구름이 우리 차랑 같이 달리기 시합을 하고 있대! 눈이 부시지 않게 실내 조명은 은은하게(15%) 낮추고 기분 좋은 산들바람(22.0°C)을 틀어줄게.",
                firstStep = "지금(First): 창밖 먼 구름을 보며 시원한 바람 느끼기",
                thenStep = "다음(Then): ${profile.comfortPhrase}",
                vehicleEffect = "Pleos 연동: 맑은 날 눈부심 완화 → 온도 22.0°C · 바람 1단 · 은은한 조명(15%)"
            ) to profile.preferences.copy(
                power = true, auto = false, temperatureCelsius = 22.0f, fanPreference = "LOW",
                ambientEnabled = true, ambientBrightnessPercent = 15, ambientColorAuto = false
            )
        }
    }

    fun sensorySosTurn(profile: PassengerProfile, kind: String): Pair<CompanionTurn, Preferences> {
        val name = address(profile.displayName)
        return when (kind) {
            "NOISE" -> CompanionTurn(
                speakerLabel = "감각 과부하 즉시 완화 · 🔊 소음/바람 줄이기",
                childUtterance = "“시끄러워요! 바람 소리 싫어요!”",
                aiReply = "앗, $name! 바람 소리가 귀에 거슬렸구나. 바로 말해줘서 고마워! 내가 지금 당장 바람 소리를 가장 작은 1단(미풍)으로 줄여서 차 안을 조용하게 만들었어.",
                firstStep = "지금(First): 바람 소리를 1단(최소)으로 즉시 낮춰 정숙 유지",
                thenStep = "다음(Then): 조용해진 차 안에서 편안히 숨 고르기",
                vehicleEffect = "Pleos 실시간 제어: 공조 풍량 → 1단(LOW) 최소 소음 즉시 적용"
            ) to profile.preferences.copy(power = true, auto = false, fanPreference = "LOW")

            "LIGHT" -> CompanionTurn(
                speakerLabel = "감각 과부하 즉시 완화 · ✨ 눈부심/시각 자극 차단",
                childUtterance = "“눈부셔요! 불빛이 너무 밝아요!”",
                aiReply = "$name, 불빛 때문에 눈이 피곤했구나. 내가 지금 바로 차 안 조명을 눈이 가장 편안한 초록 숲그늘 빛깔(#70B89B) 10% 밝기로 아주 은은하게 낮춰줬어.",
                firstStep = "지금(First): 앰비언트 조명을 10% 저조도 안정 색상(#70B89B)으로 즉시 감광",
                thenStep = "다음(Then): 눈의 피로를 풀며 편안하게 이동하기",
                vehicleEffect = "Pleos 실시간 제어: 앰비언트 조명 → 밝기 10% · 안정 초록(#70B89B) 즉시 적용"
            ) to profile.preferences.copy(ambientEnabled = true, ambientBrightnessPercent = 10, ambientColorHex = "#70B89B", ambientColorAuto = false)

            "STUFFY" -> CompanionTurn(
                speakerLabel = "감각 과부하 즉시 완화 · 🥵 답답함/열감 해소",
                childUtterance = "“답답해요! 차 안이 더워요!”",
                aiReply = "$name, 차 안 공기가 따뜻해서 답답했구나! 내가 지금 바로 온도를 시원한 20.5°C로 낮추고 에어컨 상쾌한 바람을 보내줄게. 금방 시원해질 거야!",
                firstStep = "지금(First): 설정 온도를 20.5°C로 낮추고 에어컨(A/C) 가동",
                thenStep = "다음(Then): 상쾌한 공기를 마시며 기분 전환하기",
                vehicleEffect = "Pleos 실시간 제어: 설정 온도 → 20.5°C · 에어컨 ON · 바람 약하게 즉시 적용"
            ) to profile.preferences.copy(power = true, auto = false, ac = true, temperatureCelsius = 20.5f, fanPreference = "LOW")

            else -> CompanionTurn(
                speakerLabel = "AI 호흡 진정 캡슐 · 🌊 숨 쉬는 자동차 (Breathing Cabin)",
                childUtterance = "“마음이 두근거려요. 진정하고 싶어요.”",
                aiReply = "$name, 괜찮아. 나랑 같이 파도처럼 천천히 숨을 쉬어보자. 지금 차 안의 파란 불빛이 밝아질 때 코로 숨을 들이마시고(하나, 둘, 셋), 불빛이 은은하게 어두워질 때 입으로 후— 내쉬어 봐요.",
                firstStep = "지금(First): 차량 조명 밝기 파동(45% ↔ 15%)에 맞춰 3회 깊게 호흡하기",
                thenStep = "다음(Then): 마음이 편안해지면 안정 조명(20%)과 미풍 유지하기",
                vehicleEffect = "Pleos 실시간 호흡 동기화: 조명 밝기 들숨(45%) → 날숨(15%) → 안정(20%, #5588CC) 파동 제어"
            ) to profile.preferences.copy(
                power = true, auto = false, temperatureCelsius = 21.5f, fanPreference = "LOW",
                ambientEnabled = true, ambientBrightnessPercent = 20, ambientColorHex = "#5588CC", ambientColorAuto = false
            )
        }
    }
}
