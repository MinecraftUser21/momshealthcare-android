import SwiftUI
import EventKit
import EventKitUI

/// App-wide navigation: which tab is showing, and which sheet is open.
@MainActor
final class Router: ObservableObject {

    enum Tab: String, CaseIterable, Identifiable, Hashable {
        case home, appointments, pregnancy, clinic
        var id: String { rawValue }
        var title: String {
            switch self {
            case .home: return "Home"
            case .appointments: return "Appointments"
            case .pregnancy: return "Pregnancy"
            case .clinic: return "Clinic"
            }
        }
        var symbol: String {
            switch self {
            case .home: return "house"
            case .appointments: return "calendar"
            case .pregnancy: return "figure.and.child.holdinghands"
            case .clinic: return "cross.case"
            }
        }
    }

    struct Booking: Identifiable {
        let id = UUID()
        var service: String?
        var notes: String?
        var date: String?
    }

    @Published var tab: Tab = .home
    @Published var booking: Booking?
    @Published var service: Clinic.Service?
    @Published var toast: String?

    func book(_ service: String? = nil, notes: String? = nil, date: String? = nil) {
        booking = Booking(service: service, notes: notes, date: date)
    }

    func show(toast text: String) {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { toast = text }
        Task {
            try? await Task.sleep(nanoseconds: 2_600_000_000)
            if toast == text { withAnimation(.easeInOut(duration: 0.25)) { toast = nil } }
        }
    }
}

// MARK: - Heartbeat line (the website's hero ECG)

struct EcgShape: Shape {
    func path(in r: CGRect) -> Path {
        let u = min(0.9, r.width * 0.72 / 272)
        let b = r.height * 0.6
        let sx = (r.width - 272 * u) / 2
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: b))
        var cur = CGPoint(x: sx, y: b)
        p.addLine(to: cur)
        func rel(_ dx: CGFloat, _ dy: CGFloat) {
            cur = CGPoint(x: cur.x + dx * u, y: cur.y + dy * u)
            p.addLine(to: cur)
        }
        rel(18, -22); rel(18, 22)
        cur = CGPoint(x: sx + 90 * u, y: b); p.addLine(to: cur)
        rel(14, -54); rel(18, 92); rel(16, -58); rel(12, 20)
        cur = CGPoint(x: sx + 240 * u, y: b); p.addLine(to: cur)
        rel(16, -14); rel(16, 14)
        p.addLine(to: CGPoint(x: r.maxX, y: b))
        return p
    }
}

struct EcgLine: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private let style = StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round)

    var body: some View {
        if reduceMotion || ProcessInfo.processInfo.isLowPowerModeEnabled {
            EcgShape().stroke(Palette.line, style: style)
        } else {
            TimelineView(.animation(minimumInterval: 1.0 / 30)) { context in
                let t = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3.6) / 3.6
                let head = CGFloat(t / 0.8) // the last fifth is a rest beat
                let tail: CGFloat = 0.34
                ZStack {
                    EcgShape().stroke(Palette.line, style: style)
                    if head < 1 + tail {
                        EcgShape()
                            .trim(from: max(0, head - tail), to: min(1, head))
                            .stroke(
                                LinearGradient(
                                    colors: [Palette.text.opacity(0), Palette.text.opacity(0.5), Palette.text],
                                    startPoint: UnitPoint(x: head - tail, y: 0.5),
                                    endPoint: UnitPoint(x: head, y: 0.5)
                                ),
                                style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round)
                            )
                    }
                }
            }
        }
    }
}

// MARK: - Small pieces

struct StatusDot: View {
    let open: Bool
    var body: some View {
        Circle()
            .fill(open ? Palette.open : Palette.closed)
            .frame(width: 7, height: 7)
            .padding(3)
            .background(Circle().fill(open ? Palette.open.opacity(0.2) : .clear))
    }
}

struct IconCircle: View {
    let symbol: String
    var size: CGFloat = 44
    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.42, weight: .regular))
            .foregroundStyle(Palette.text)
            .frame(width: size, height: size)
            .background(Circle().fill(Palette.field))
    }
}

/// Lays children out left to right, wrapping onto new lines.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, line: CGFloat = 0, widest: CGFloat = 0
        for view in subviews {
            let s = view.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > maxWidth {
                x = 0
                y += line + spacing
                line = 0
            }
            x += s.width + spacing
            widest = max(widest, x - spacing)
            line = max(line, s.height)
        }
        return CGSize(width: proposal.width ?? widest, height: y + line)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = 0, y: CGFloat = 0, line: CGFloat = 0
        for view in subviews {
            let s = view.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > bounds.width {
                x = 0
                y += line + spacing
                line = 0
            }
            view.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY + y), proposal: .unspecified)
            x += s.width + spacing
            line = max(line, s.height)
        }
    }
}

/// Selectable pill (service, patient).
struct ChoicePill: View {
    let title: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15, weight: .medium))
                .padding(.horizontal, 16)
                .frame(minHeight: 40)
                .foregroundStyle(selected ? Palette.onAccent : Palette.text)
                .background(Capsule().fill(selected ? Palette.accent : Palette.field))
                .animation(.easeOut(duration: 0.16), value: selected)
        }
        .buttonStyle(PressStyle())
    }
}

/// The website's pill toast.
struct ToastView: View {
    let text: String
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle").foregroundStyle(Palette.open)
            Text(text).font(.system(size: 15, weight: .medium)).foregroundStyle(Palette.text)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(Capsule().fill(Palette.surface2))
        .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
        .accessibilityAddTraits(.isStaticText)
    }
}

// MARK: - Add to calendar

enum CalendarAccess {
    static let store = EKEventStore()

    /// iOS 17 and later add events without asking; iOS 16 needs permission first.
    static func ready() async -> Bool {
        if #available(iOS 17.0, *) { return true }
        return (try? await store.requestAccess(to: .event)) ?? false
    }
}

struct CalendarSheet: UIViewControllerRepresentable {
    let request: Store.Request
    let onDone: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onDone: onDone) }

    func makeUIViewController(context: Context) -> EKEventEditViewController {
        let event = EKEvent(eventStore: CalendarAccess.store)
        let slot = Clinic.slot(request.slot)
        event.title = "\(request.service) · Mom’s Health Care"
        event.startDate = Clinic.moment(request.date, hour: slot?.startHour ?? Clinic.opensHour)
        event.endDate = Clinic.moment(request.date, hour: slot?.endHour ?? Clinic.closesHour)
        event.timeZone = Clinic.timeZone
        event.location = "Mom’s Health Care, \(Clinic.address)"
        event.notes = "Appointment request for \(request.name). The clinic will call to confirm the exact time. Phone: \(Clinic.phoneDisplay)."
        let controller = EKEventEditViewController()
        controller.eventStore = CalendarAccess.store
        controller.event = event
        controller.editViewDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: EKEventEditViewController, context: Context) {}

    final class Coordinator: NSObject, EKEventEditViewDelegate {
        let onDone: () -> Void
        init(onDone: @escaping () -> Void) { self.onDone = onDone }
        func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
            onDone()
        }
    }
}
