import SwiftUI
import Network

@main
struct RideTuneApp: App {
    var body: some Scene {
        WindowGroup { RideTuneView() }
    }
}

private struct Passenger: Codable, Identifiable {
    var id = UUID()
    var name: String
    var temperature: Double = 22
    var fan: Fan = .low
    var brightness: Double = 20
    var colorHex: String = "#5588CC"
    var interestTopic: String = "바다와 고래"
    var comfortPhrase: String = "지금은 안전하게 가는 중이고, 곧 도착해요"
    var weatherScene: String = "비 오는 길"

    var initial: String { name.first.map(String.init) ?? "" }
    var degrees: String { temperature.formatted(.number.precision(.fractionLength(0...1))) + "°C" }
    var light: String { "\(Int(brightness))%" }

    enum Fan: String, CaseIterable, Codable {
        case low = "약하게", medium = "중간", high = "강하게"
    }

    static let topics = ["바다와 고래", "공룡과 나무", "기차와 역 이름", "우주와 별자리"]
    static let weathers = ["비 오는 길", "깜깜한 밤·터널", "막히는 도로", "맑은 날"]
    static let colors: [(name: String, hex: String)] = [
        ("파란 바다", "#5588CC"),
        ("초록 숲", "#70B89B"),
        ("따뜻한 노을", "#D2A66B"),
        ("별빛 보라", "#C28BB3")
    ]
}

private enum Palette {
    static let paper = Color(red: 245/255, green: 243/255, blue: 240/255)
    static let panel = Color(red: 232/255, green: 226/255, blue: 218/255)
    static let ink = Color(red: 48/255, green: 45/255, blue: 41/255)
    static let muted = Color(red: 98/255, green: 91/255, blue: 81/255)
    static let line = Color(red: 214/255, green: 206/255, blue: 196/255)
    static let accent = Color(red: 117/255, green: 109/255, blue: 99/255)
    static let white = Color(red: 255/255, green: 253/255, blue: 249/255)
}

private struct RideTuneView: View {
    private enum Page { case home, editor, connect, review, sent }
    @AppStorage("passengers.v2") private var savedProfiles = Data()
    @AppStorage("selectedPassenger.v2") private var savedSelection = ""
    @AppStorage("vehicleHost.v1") private var vehicleHost = "192.168.200.165"
    @State private var profiles = [
        Passenger(
            name: "바다",
            temperature: 22,
            fan: .low,
            brightness: 20,
            colorHex: "#5588CC",
            interestTopic: "바다와 고래",
            comfortPhrase: "지금은 안전하게 가는 중이고, 곧 도착해요",
            weatherScene: "비 오는 길"
        ),
        Passenger(
            name: "숲",
            temperature: 23,
            fan: .low,
            brightness: 15,
            colorHex: "#70B89B",
            interestTopic: "공룡과 나무",
            comfortPhrase: "엄마 아빠랑 함께 안전하게 이동하고 있어요",
            weatherScene: "맑은 날"
        )
    ]
    @State private var selectedID: UUID?
    @State private var draft = Passenger(name: "")
    @State private var page: Page = .home
    @State private var code = ""
    @State private var connectionError: String?
    @State private var connecting = false
    @FocusState private var codeFocused: Bool

    private var selected: Passenger { profiles.first { $0.id == selectedID } ?? profiles[0] }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    switch page {
                    case .home: home
                    case .editor: editor
                    case .connect: connect
                    case .review: review
                    case .sent: sent
                    }
                }
                .frame(maxWidth: 440)
                .padding(.horizontal, 22)
                .padding(.bottom, 28)
                .frame(maxWidth: .infinity)
            }
            .defaultScrollAnchor(.top)
            .scrollDismissesKeyboard(.interactively)
            .id(page)
        }
        .background(Palette.paper.ignoresSafeArea())
        .foregroundStyle(Palette.ink)
        .tint(Palette.accent)
        .preferredColorScheme(.light)
        .onAppear(perform: load)
    }

    private var header: some View {
        HStack(spacing: 9) {
            Image("RideTuneMark").resizable().frame(width: 31, height: 31).accessibilityHidden(true)
            Text("RideTune").font(.headline)
            Spacer()
            if page != .home {
                Button("닫기") { navigate(.home) }
                    .disabled(connecting)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.muted)
                    .frame(minWidth: 44, minHeight: 44)
            }
        }
        .frame(maxWidth: 440)
        .padding(.horizontal, 22)
        .frame(maxWidth: .infinity)
        .frame(height: 66)
    }

    private var home: some View {
        Group {
            heading("탑승자 & AI 단짝", "감각 환경과 반복 질문을 받아줄 AI 대화 단짝을 준비해요.")
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 11) {
                    Text(selected.initial).font(.headline)
                        .frame(width: 44, height: 44).background(Palette.white, in: Circle())
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("이동 감각 · AI 대화 프로필").font(.caption).foregroundStyle(Palette.muted)
                        Text(selected.name).font(.title2.weight(.semibold))
                    }
                    Spacer()
                    Button("편집") { draft = selected; navigate(.editor) }
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Palette.muted)
                        .frame(minWidth: 44, minHeight: 44)
                }
                Divider().overlay(Palette.line)
                HStack(alignment: .top, spacing: 8) {
                    metric("온도", selected.degrees)
                    Divider().overlay(Palette.line)
                    metric("바람", selected.fan.rawValue)
                    Divider().overlay(Palette.line)
                    metric("조명", selected.light)
                }
                .fixedSize(horizontal: false, vertical: true)
                Divider().overlay(Palette.line)
                VStack(alignment: .leading, spacing: 6) {
                    Text("AI 안심 대화 단짝").font(.caption).foregroundStyle(Palette.muted)
                    Text("관심사: \(selected.interestTopic) · 창밖: \(selected.weatherScene)")
                        .font(.subheadline.weight(.semibold))
                    Text("“\(selected.comfortPhrase)”")
                        .font(.caption).foregroundStyle(Palette.muted)
                }
            }
            .padding(17)
            .background(Palette.panel, in: RoundedRectangle(cornerRadius: 22))

            panel {
                Text("오늘 창밖 상황 빠른 선택").font(.subheadline.weight(.semibold))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Passenger.weathers, id: \.self) { w in
                            Button {
                                updateWeather(w)
                            } label: {
                                Text(w)
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .foregroundStyle(selected.weatherScene == w ? Palette.white : Palette.ink)
                                    .background(selected.weatherScene == w ? Palette.accent : Palette.white, in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            action("차량에 프로필 & AI 단짝 보내기", icon: "car.fill") { beginConnection() }
            VStack(spacing: 0) {
                HStack {
                    Text("저장된 프로필").font(.headline).accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button("+ 추가") { draft = Passenger(name: ""); navigate(.editor) }
                        .font(.subheadline.weight(.semibold))
                        .frame(minWidth: 44, minHeight: 44)
                        .accessibilityLabel("프로필 추가")
                }
                ForEach(profiles) { profile in
                    Divider().overlay(Palette.line)
                    Button {
                        select(profile.id)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(profile.name).font(.subheadline.weight(.semibold))
                                Text("\(profile.interestTopic) · \(profile.weatherScene)")
                                    .font(.caption2).foregroundStyle(Palette.muted)
                            }
                            if profile.id == selected.id {
                                Text("선택됨").font(.caption).foregroundStyle(Palette.muted)
                                    .accessibilityHidden(true)
                            }
                            Spacer(minLength: 8)
                            Text("\(profile.degrees) · 조명 \(profile.light)")
                                .font(.caption).foregroundStyle(Palette.muted)
                        }
                        .frame(minHeight: 54)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(profile.id == selected.id ? .isSelected : [])
                }
            }
            .padding(.top, 4)
        }
    }

    private var editor: some View {
        Group {
            heading(profiles.contains { $0.id == draft.id } ? "프로필 편집" : "새 프로필",
                    "탑승자가 편한 환경과 AI 대화 단짝 주제를 저장해요.")
            VStack(alignment: .leading, spacing: 7) {
                Text("탑승자 이름").font(.subheadline.weight(.semibold))
                TextField("별명을 입력해 주세요", text: $draft.name)
                    .accessibilityLabel("탑승자 이름")
                    .onChange(of: draft.name) { _, value in
                        if value.count > 30 { draft.name = String(value.prefix(30)) }
                    }
                    .modifier(FieldStyle())
            }
            panel {
                Text("AI 안심 대화 단짝 (반복 질문 응대)").font(.headline).accessibilityAddTraits(.isHeader)
                Text("좋아하는 특별 관심사").font(.caption).foregroundStyle(Palette.muted)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Passenger.topics, id: \.self) { t in
                            Button {
                                draft.interestTopic = t
                            } label: {
                                Text(t)
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 11)
                                    .padding(.vertical, 7)
                                    .foregroundStyle(draft.interestTopic == t ? Palette.white : Palette.ink)
                                    .background(draft.interestTopic == t ? Palette.accent : Palette.white, in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                TextField("관심 주제 직접 입력 (예: 바다와 고래, 2호선 지하철)", text: $draft.interestTopic)
                    .modifier(FieldStyle())
                Text("반복 질문 시 일관되게 들려줄 안심 문구").font(.caption).foregroundStyle(Palette.muted)
                TextField("예: 지금은 안전하게 가는 중이고, 곧 도착해요", text: $draft.comfortPhrase)
                    .modifier(FieldStyle())
                Text("창밖 날씨·상황").font(.caption).foregroundStyle(Palette.muted)
                Picker("창밖 상황", selection: $draft.weatherScene) {
                    ForEach(Passenger.weathers, id: \.self) { w in
                        Text(w).tag(w)
                    }
                }
                .pickerStyle(.segmented)
            }
            panel {
                Text("공조").font(.headline).accessibilityAddTraits(.isHeader)
                slider("온도", display: draft.degrees, value: $draft.temperature, range: 15...30, step: 0.5)
                HStack {
                    Text("바람 세기").font(.subheadline.weight(.semibold))
                    Spacer()
                    Picker("바람 세기", selection: $draft.fan) {
                        ForEach(Passenger.Fan.allCases, id: \.self) { fan in
                            Text(fan.rawValue).tag(fan)
                        }
                    }
                    .pickerStyle(.menu)
                }
            }
            panel {
                Text("실내조명").font(.headline).accessibilityAddTraits(.isHeader)
                slider("밝기", display: draft.light, value: $draft.brightness, range: 0...100, step: 5)
                HStack {
                    Text("안정 조명 색상").font(.subheadline.weight(.semibold))
                    Spacer()
                    Picker("조명 색상", selection: $draft.colorHex) {
                        ForEach(Passenger.colors, id: \.hex) { item in
                            Text(item.name).tag(item.hex)
                        }
                    }
                    .pickerStyle(.menu)
                }
            }
            action("프로필 저장", enabled: !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                saveDraft()
            }
        }
    }

    private var connect: some View {
        Group {
            heading("차량 연결", "차량 화면에서 RideTune을 열고 프로필 가져오기를 눌러 주세요.")
            panel {
                Label("\(selected.name)의 이동 프로필 & AI 단짝", systemImage: "person")
                    .font(.subheadline.weight(.semibold))
                Text("관심사: \(selected.interestTopic) · 창밖: \(selected.weatherScene)")
                    .font(.caption).foregroundStyle(Palette.muted)
            }
            VStack(alignment: .leading, spacing: 7) {
                Text("차량 화면에 표시된 연결 번호").font(.subheadline.weight(.semibold))
                TextField("6자리 번호", text: $code)
                    .keyboardType(.numberPad)
                    .focused($codeFocused)
                    .accessibilityLabel("차량 연결 번호")
                    .onChange(of: code) { _, value in
                        code = String(value.filter { "0123456789".contains($0) }.prefix(6))
                        connectionError = nil
                    }
                    .modifier(FieldStyle())
            }
            VStack(alignment: .leading, spacing: 7) {
                Text("Mac 주소 (실기기 Wi-Fi 연동 시)").font(.caption.weight(.semibold)).foregroundStyle(Palette.muted)
                TextField("예: 192.168.200.165", text: $vehicleHost)
                    .keyboardType(.numbersAndPunctuation)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityLabel("Mac IP 주소")
                    .modifier(FieldStyle())
            }
            Text("iOS 시뮬레이터(127.0.0.1)와 같은 Wi-Fi의 실제 iPhone 모두 연결할 수 있어요.")
                .font(.footnote).foregroundStyle(Palette.muted)
            transferError
            action(connecting ? "연결 확인 중…" : "연결 확인", enabled: code.count == 6 && !connecting) {
                transfer(includeProfile: false)
            }
        }
    }

    private var review: some View {
        Group {
            heading("보낼 설정 확인", "차량으로 보낼 감각 설정과 AI 단짝 주제를 확인해 주세요.")
            panel {
                Text("\(selected.name)의 이동 프로필").font(.headline)
                detail("온도", selected.degrees)
                detail("바람", selected.fan.rawValue)
                detail("조명", selected.light)
                detail("AI 관심 주제", selected.interestTopic)
                detail("창밖 상황", selected.weatherScene)
            }
            Text("보내기를 누르면 차량 화면에 받은 프로필이 바로 표시돼요.")
                .font(.footnote).foregroundStyle(Palette.muted)
            transferError
            action(connecting ? "보내는 중…" : "프로필 보내기", enabled: !connecting) { transfer(includeProfile: true) }
            action("연결 번호 다시 확인", enabled: !connecting, secondary: true) { beginConnection() }
        }
    }

    private var sent: some View {
        Group {
            Image(systemName: "checkmark")
                .font(.title2.weight(.semibold))
                .frame(width: 54, height: 54)
                .background(Palette.panel, in: Circle())
                .accessibilityHidden(true)
            heading("프로필을 보냈어요", "Pleos에서 \(selected.name)의 프로필과 AI 대화 주제를 받았어요.")
            panel {
                Text("차량에서 할 일").font(.caption).foregroundStyle(Palette.muted)
                Text("차량 화면에서 변경 내용을 확인한 뒤 감각 설정을 적용할 수 있어요.")
                    .font(.subheadline)
            }
            action("탑승자로 돌아가기") { navigate(.home) }
        }
    }

    private func heading(_ title: String, _ subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.largeTitle.weight(.bold)).accessibilityAddTraits(.isHeader)
            Text(subtitle).font(.subheadline).foregroundStyle(Palette.muted)
        }
        .padding(.bottom, 8)
    }

    private func metric(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(Palette.muted)
            Text(value).font(.body.weight(.semibold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func detail(_ label: String, _ value: String) -> some View {
        VStack(spacing: 10) {
            Divider().overlay(Palette.line)
            HStack { Text(label).foregroundStyle(Palette.muted); Spacer(); Text(value).fontWeight(.semibold) }
                .font(.subheadline)
        }
        .accessibilityElement(children: .combine)
    }

    private func panel<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(17)
            .background(Palette.panel, in: RoundedRectangle(cornerRadius: 22))
    }

    private func slider(_ label: String, display: String, value: Binding<Double>, range: ClosedRange<Double>, step: Double) -> some View {
        VStack(spacing: 0) {
            HStack { Text(label); Spacer(); Text(display).monospacedDigit() }
                .font(.subheadline.weight(.semibold))
                .accessibilityHidden(true)
            Slider(value: value, in: range, step: step) { Text(label) }
                .accessibilityValue(display)
                .frame(minHeight: 44)
        }
    }

    private func action(_ title: String, icon: String? = nil, enabled: Bool = true,
                        secondary: Bool = false, perform: @escaping () -> Void) -> some View {
        Button(action: perform) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).accessibilityHidden(true) }
                Text(title).fontWeight(.semibold).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 51)
            .padding(.horizontal, 10)
            .foregroundStyle(secondary || !enabled ? Palette.ink : Palette.white)
            .background(secondary || !enabled ? Palette.panel : Palette.accent, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func navigate(_ destination: Page) {
        codeFocused = false
        page = destination
    }

    private func beginConnection() {
        code = ""
        connectionError = nil
        navigate(.connect)
    }

    private func updateWeather(_ weather: String) {
        guard let index = profiles.firstIndex(where: { $0.id == selected.id }) else { return }
        profiles[index].weatherScene = weather
        persist()
    }

    @ViewBuilder private var transferError: some View {
        if let connectionError {
            Text(connectionError).font(.footnote)
                .foregroundStyle(Color(red: 0.51, green: 0.23, blue: 0.19))
        }
    }

    private func transfer(includeProfile: Bool) {
        guard !connecting else { return }
        let profile = includeProfile ? selected : nil
        let submittedCode = code
        let submittedHost = vehicleHost.trimmingCharacters(in: .whitespacesAndNewlines)
        codeFocused = false
        connecting = true
        connectionError = nil
        Task {
            defer { connecting = false }
            do {
                try await SimulatorTransfer.request(code: submittedCode, passenger: profile, host: submittedHost)
                navigate(includeProfile ? .sent : .review)
            } catch {
                connectionError = error.localizedDescription
                AccessibilityNotification.Announcement(error.localizedDescription).post()
            }
        }
    }

    private func load() {
        if let stored = try? JSONDecoder().decode([Passenger].self, from: savedProfiles), !stored.isEmpty {
            profiles = stored
        } else {
            persist()
        }
        selectedID = UUID(uuidString: savedSelection)
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(profiles) { savedProfiles = data }
    }

    private func select(_ id: UUID) {
        selectedID = id
        savedSelection = id.uuidString
    }

    private func saveDraft() {
        draft.name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !draft.name.isEmpty else { return }
        if let index = profiles.firstIndex(where: { $0.id == draft.id }) {
            profiles[index] = draft
        } else {
            profiles.append(draft)
        }
        persist()
        select(draft.id)
        navigate(.home)
    }
}

private struct FieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 11)
            .frame(minHeight: 46)
            .background(Palette.white, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Palette.line).allowsHitTesting(false))
    }
}

private enum SimulatorTransfer {
    enum Failure: LocalizedError {
        case unavailable, invalidCode, invalidProfile, unsupported

        var errorDescription: String? {
            switch self {
            case .unavailable: return "차량 수신 화면을 열고 새 번호로 다시 연결해 주세요."
            case .invalidCode: return "번호가 맞지 않아요. 차량 화면에서 다시 확인해 주세요."
            case .invalidProfile: return "차량에서 프로필을 읽지 못했어요. 설정을 확인해 주세요."
            case .unsupported: return "이 연결은 iPhone과 Pleos 시뮬레이터에서 사용할 수 있어요."
            }
        }
    }

    struct Request: Encodable {
        let code: String
        let profile: VehicleProfile?
    }

    struct VehicleProfile: Encodable {
        let profileId: String
        let displayName: String
        let interestTopic: String
        let comfortPhrase: String
        let weatherScene: String
        let preferences: Preferences

        struct Preferences: Encodable {
            let temperatureCelsius: Double
            let fanPreference: String
            let ambientBrightnessPercent: Int
            let ambientColorHex: String
            let ambientColorAuto = false
            let power = true
            let auto = false
            let ambientEnabled = true
        }

        init(_ passenger: Passenger) {
            profileId = passenger.id.uuidString
            displayName = passenger.name
            interestTopic = passenger.interestTopic
            comfortPhrase = passenger.comfortPhrase
            weatherScene = passenger.weatherScene
            let fan: String
            switch passenger.fan {
            case .low: fan = "LOW"
            case .medium: fan = "MEDIUM"
            case .high: fan = "HIGH"
            }
            preferences = Preferences(
                temperatureCelsius: passenger.temperature,
                fanPreference: fan,
                ambientBrightnessPercent: Int(passenger.brightness),
                ambientColorHex: passenger.colorHex
            )
        }
    }

    static func request(code: String, passenger: Passenger?, host: String) async throws {
        var data = try JSONEncoder().encode(Request(code: code, profile: passenger.map(VehicleProfile.init)))
        data.append(10)
        let payload = data
        var candidates: [(String, UInt16)] = []
        #if targetEnvironment(simulator)
        candidates.append(("127.0.0.1", 48732))
        candidates.append(("127.0.0.1", 48733))
        #endif
        if !host.isEmpty && host != "127.0.0.1" {
            candidates.append((host, 48733))
            candidates.append((host, 48732))
        }
        for fallbackHost in ["192.168.200.165", "abcui-MacBookPro.local"] where fallbackHost != host {
            candidates.append((fallbackHost, 48733))
        }
        #if !targetEnvironment(simulator)
        candidates.append(("127.0.0.1", 48732))
        #endif
        var lastError: Error = Failure.unavailable
        for (targetHost, targetPort) in candidates {
            do {
                try await sendOnce(payload: payload, host: targetHost, port: targetPort)
                return
            } catch let error as Failure where error == .invalidCode || error == .invalidProfile {
                throw error
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    private static func sendOnce(payload: Data, host: String, port: UInt16) async throws {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else { throw Failure.unavailable }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let queue = DispatchQueue(label: "ridetune.simulator-transfer")
            let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
            var finished = false
            func finish(_ result: Result<Void, Error>) {
                guard !finished else { return }
                finished = true
                connection.stateUpdateHandler = nil
                connection.cancel()
                continuation.resume(with: result)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    connection.send(content: payload, completion: .contentProcessed { error in
                        if let error { finish(.failure(error)); return }
                        connection.receive(minimumIncompleteLength: 1, maximumLength: 1) { reply, _, _, _ in
                            switch reply?.first {
                            case 79: finish(.success(()))
                            case 78: finish(.failure(Failure.invalidCode))
                            case 69: finish(.failure(Failure.invalidProfile))
                            default: finish(.failure(Failure.unavailable))
                            }
                        }
                    })
                case .failed: finish(.failure(Failure.unavailable))
                default: break
                }
            }
            queue.asyncAfter(deadline: .now() + 2.5) { finish(.failure(Failure.unavailable)) }
            connection.start(queue: queue)
        }
    }
}
