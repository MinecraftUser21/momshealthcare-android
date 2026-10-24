import SwiftUI

/// What the app remembers, kept on this device only: requests, saved patients,
/// pregnancy dates and the chosen appearance.
@MainActor
final class Store: ObservableObject {

    struct Request: Codable, Identifiable, Hashable {
        static let requested = "requested"
        static let cancelled = "cancelled"

        let id: Int64
        let name: String
        let phone: String
        let service: String
        let date: String
        let slot: String
        var status: String = Request.requested

        var isUpcoming: Bool { status == Request.requested && date >= Clinic.todayISO() }
    }

    struct Profile: Codable, Hashable, Identifiable {
        let name: String
        let phone: String
        var id: String { "\(phone)|\(name.lowercased())" }
    }

    enum Appearance: String, CaseIterable, Identifiable {
        case dark, light, system
        var id: String { rawValue }
        var title: String {
            switch self {
            case .dark: return "Dark"
            case .light: return "Light"
            case .system: return "Match phone"
            }
        }
        var scheme: ColorScheme? {
            switch self {
            case .dark: return .dark
            case .light: return .light
            case .system: return nil
            }
        }
    }

    @Published var requests: [Request] { didSet { save(requests, "requests") } }
    @Published var profiles: [Profile] { didSet { save(profiles, "profiles") } }
    @Published var lmp: String? { didSet { defaults.set(lmp, forKey: "lmp") } }
    /// Dark by default, like the website.
    @Published var appearance: Appearance { didSet { defaults.set(appearance.rawValue, forKey: "appearance") } }

    private let defaults = UserDefaults.standard

    init() {
        requests = Self.load("requests") ?? []
        profiles = Self.load("profiles") ?? []
        lmp = UserDefaults.standard.string(forKey: "lmp")
        appearance = Appearance(rawValue: UserDefaults.standard.string(forKey: "appearance") ?? "") ?? .dark
        #if DEBUG
        // Screenshot mode: launch with "-demo YES" for sample data (kept in memory only).
        if UserDefaults.standard.bool(forKey: "demo") && requests.isEmpty {
            let today = Clinic.todayISO()
            requests = [
                Request(id: 2, name: "Priya Das", phone: "9876543210", service: "Ultrasound (USG)", date: Clinic.addDays(today, 3), slot: "morning"),
                Request(id: 1, name: "Priya Das", phone: "9876543210", service: "General OPD", date: Clinic.addDays(today, -14), slot: "evening"),
            ]
            profiles = [Profile(name: "Priya Das", phone: "9876543210"), Profile(name: "Rina Das", phone: "9123456780")]
            lmp = Clinic.addDays(today, -96)
        }
        #endif
    }

    private static func load<T: Decodable>(_ key: String) -> T? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private func save<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? JSONEncoder().encode(value) { defaults.set(data, forKey: key) }
    }

    // MARK: Requests

    var upcoming: [Request] {
        requests.filter(\.isUpcoming).sorted { ($0.date, $0.id) < ($1.date, $1.id) }
    }

    var earlier: [Request] {
        requests.filter { !$0.isUpcoming }.sorted { ($0.date, $0.id) > ($1.date, $1.id) }
    }

    var next: Request? { upcoming.first }

    func add(_ r: Request) {
        // Newest 50 is plenty for a phone.
        requests = Array(([r] + requests).sorted { $0.id > $1.id }.prefix(50))
    }

    func cancel(_ id: Int64) {
        if let i = requests.firstIndex(where: { $0.id == id }) { requests[i].status = Request.cancelled }
    }

    // MARK: Saved patients

    /// Saves (or moves to the front) a patient, most recent first.
    func remember(_ p: Profile) {
        profiles = Array(([p] + profiles.filter { $0.id != p.id }).prefix(8))
    }

    func forget(_ p: Profile) {
        profiles.removeAll { $0.id == p.id }
    }
}
