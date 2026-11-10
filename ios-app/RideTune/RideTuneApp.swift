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

    var initial: String { name.first.map(String.init) ?? "" }
    var degrees: String { temperature.formatted(.number.precision(.fractionLength(0...1))) + "°C" }
    var light: String { "\(Int(brightness))%" }

    enum Fan: String, CaseIterable, Codable {
        case low = "약하게", medium = "중간", high = "강하게"
    }
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
    @AppStorage("passengers.v1") private var savedProfiles = Data()
    @AppStorage("selectedPassenger.v1") private var savedSelection = ""
    @State private var profiles = [
        Passenger(name: "바다"),
        Passenger(name: "숲", temperature: 23, brightness: 15)
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
            heading("탑승자", "탑승 전에, 각자 편한 환경을 준비해요.")
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 11) {
                    Text(selected.initial).font(.headline)
                        .frame(width: 44, height: 44).background(Palette.white, in: Circle())
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("이동 프로필").font(.caption).foregroundStyle(Palette.muted)
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
            }
            .padding(17)
            .background(Palette.panel, in: RoundedRectangle(cornerRadius: 22))
            action("차량에 보내기", icon: "car.fill") { beginConnection() }
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
                            Text(profile.name).font(.subheadline.weight(.semibold))
                            if profile.id == selected.id {
                                Text("선택됨").font(.caption).foregroundStyle(Palette.muted)
                                    .accessibilityHidden(true)
                            }
                            Spacer(minLength: 8)
                            Text("\(profile.degrees) · 조명 \(profile.light)")
                                .font(.caption).foregroundStyle(Palette.muted)
                        }
                        .frame(minHeight: 50)
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
                    "탑승자가 편하게 느끼는 값을 저장해요.")
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
                Label("\(selected.name)의 이동 프로필", systemImage: "person")
                    .font(.subheadline.weight(.semibold))
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
            Text("같은 Mac에서 실행 중인 Pleos 시뮬레이터에 연결해요.")
                .font(.footnote).foregroundStyle(Palette.muted)
            transferError
            action(connecting ? "연결 확인 중…" : "연결 확인", enabled: code.count == 6 && !connecting) {
                transfer(includeProfile: false)
            }
        }
    }

    private var review: some View {
        Group {
            heading("보낼 설정 확인", "차량으로 보낼 프로필을 확인해 주세요.")
            panel {
                Text("\(selected.name)의 이동 프로필").font(.headline)
                detail("온도", selected.degrees)
                detail("바람", selected.fan.rawValue)
                detail("조명", selected.light)
            }
            Text("차량 화면에서 받은 프로필을 확인하고 적용해요.")
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
            heading("프로필을 보냈어요", "Pleos에서 \(selected.name)의 프로필을 받았어요.")
            panel {
                Text("차량에서 할 일").font(.caption).foregroundStyle(Palette.muted)
                Text("차량 화면에서 받은 프로필을 열고 변경 내용을 확인해 주세요.")
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
        codeFocused = false
        connecting = true
        connectionError = nil
        Task {
            defer { connecting = false }
            do {
                try await SimulatorTransfer.request(code: submittedCode, passenger: profile)
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
        let preferences: Preferences

        struct Preferences: Encodable {
            let temperatureCelsius: Double
            let fanPreference: String
            let ambientBrightnessPercent: Int
            let power = true
            let auto = false
            let ambientEnabled = true
        }

        init(_ passenger: Passenger) {
            profileId = passenger.id.uuidString
            displayName = passenger.name
            let fan: String
            switch passenger.fan {
            case .low: fan = "LOW"
            case .medium: fan = "MEDIUM"
            case .high: fan = "HIGH"
            }
            preferences = Preferences(
                temperatureCelsius: passenger.temperature,
                fanPreference: fan,
                ambientBrightnessPercent: Int(passenger.brightness)
            )
        }
    }

    static func request(code: String, passenger: Passenger?) async throws {
        #if targetEnvironment(simulator)
        var data = try JSONEncoder().encode(Request(code: code, profile: passenger.map(VehicleProfile.init)))
        data.append(10)
        let payload = data
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let queue = DispatchQueue(label: "ridetune.simulator-transfer")
            let connection = NWConnection(host: "127.0.0.1", port: 48732, using: .tcp)
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
            queue.asyncAfter(deadline: .now() + 4) { finish(.failure(Failure.unavailable)) }
            connection.start(queue: queue)
        }
        #else
        throw Failure.unsupported
        #endif
    }
}
