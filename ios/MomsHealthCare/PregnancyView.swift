import SwiftUI

/// Pregnancy dating from the first day of the last period, using the standard 280-day term.
enum Pregnancy {
    static let termDays = 280

    struct Scan: Identifiable {
        let name: String
        let startWeek: Int
        let endWeek: Int
        var id: String { name }
    }

    /// The scans most pregnancies are offered, and the weeks they're usually done in.
    static let scans = [
        Scan(name: "Dating & viability scan", startWeek: 6, endWeek: 10),
        Scan(name: "NT scan", startWeek: 11, endWeek: 14),
        Scan(name: "Anomaly scan (TIFFA)", startWeek: 18, endWeek: 22),
        Scan(name: "Growth scan", startWeek: 28, endWeek: 32),
        Scan(name: "Growth & Doppler scan", startWeek: 34, endWeek: 37),
    ]

    enum State { case done, now, later }

    struct Progress {
        let lmp: String
        var days: Int { Clinic.daysBetween(lmp, Clinic.todayISO()) }
        var weeks: Int { max(0, days / 7) }
        var dayOfWeek: Int { max(0, days % 7) }
        var due: String { Clinic.addDays(lmp, Pregnancy.termDays) }
        var daysToGo: Int { Pregnancy.termDays - days }
        var overdue: Bool { days > Pregnancy.termDays + 14 }
        var trimester: String { weeks < 14 ? "First" : weeks < 28 ? "Second" : "Third" }
        var fraction: Double { min(1, max(0, Double(days) / Double(Pregnancy.termDays))) }

        func start(_ s: Scan) -> String { Clinic.addDays(lmp, s.startWeek * 7) }
        func end(_ s: Scan) -> String { Clinic.addDays(lmp, s.endWeek * 7 - 1) }
        func state(_ s: Scan) -> State { weeks >= s.endWeek ? .done : weeks >= s.startWeek ? .now : .later }
    }
}

struct PregnancyView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @State private var picking: PickMode?
    @State private var confirmRemove = false
    @State private var shownFraction = 0.0

    enum PickMode: String, Identifiable {
        case lmp, due
        var id: String { rawValue }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Track your weeks and see when each scan is due.")
                    .font(.subheadline)
                    .foregroundStyle(Palette.text2)
                if let lmp = store.lmp {
                    tracker(Pregnancy.Progress(lmp: lmp))
                } else {
                    setup
                }
                Text("For general information only. Your doctor will confirm your due date and which scans you need.")
                    .font(.footnote)
                    .foregroundStyle(Palette.text3)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
            .readable()
        }
        .screen()
        .navigationTitle("Pregnancy")
        .sheet(item: $picking) { mode in
            DateSheet(mode: mode, current: store.lmp) { iso in
                store.lmp = mode == .lmp ? iso : Clinic.addDays(iso, -Pregnancy.termDays)
            }
            .presentationDetents([.large])
        }
        .confirmationDialog("Remove pregnancy dates?", isPresented: $confirmRemove, titleVisibility: .visible) {
            Button("Remove", role: .destructive) { store.lmp = nil }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("Your dates will be deleted from this device.")
        }
    }

    private var setup: some View {
        VStack(alignment: .leading, spacing: 6) {
            IconCircle(symbol: "figure.and.child.holdinghands", size: 52)
            Heading.headline("When did your last period start?").padding(.top, 14)
            Text("We’ll work out your due date, how far along you are and when your scans are due. This stays on your device.")
                .font(.subheadline)
                .foregroundStyle(Palette.text2)
            Button("Choose the date") { picking = .lmp }
                .buttonStyle(PillButtonStyle(fullWidth: true))
                .padding(.top, 18)
            Button("I know my due date") { picking = .due }
                .buttonStyle(PillButtonStyle(primary: false, fullWidth: true))
        }
        .card()
    }

    private func tracker(_ p: Pregnancy.Progress) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(spacing: 20) {
                ZStack {
                    Circle().stroke(Palette.field, lineWidth: 10)
                    Circle()
                        .trim(from: 0, to: shownFraction)
                        .stroke(Palette.text, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    VStack(spacing: 2) {
                        Text("\(p.weeks)")
                            .font(.system(size: 52, weight: .semibold))
                            .tracking(-1.5)
                            .foregroundStyle(Palette.text)
                        Text("weeks, \(p.dayOfWeek) \(p.dayOfWeek == 1 ? "day" : "days")")
                            .font(.subheadline)
                            .foregroundStyle(Palette.text2)
                    }
                }
                .frame(width: 180, height: 180)
                .onAppear { animate(to: p.fraction) }
                .onChange(of: p.fraction) { animate(to: $0) }

                HStack {
                    stat(p.trimester, "Trimester")
                    stat(Clinic.dayMonth(p.due), "Due date")
                    stat(p.daysToGo > 0 ? "\(p.daysToGo) days" : "—", "To go")
                }
                if p.overdue {
                    Text("Your due date has passed. Congratulations if your baby has arrived! You can update or remove your dates.")
                        .font(.subheadline)
                        .foregroundStyle(Palette.text2)
                        .multilineTextAlignment(.center)
                }
                HStack(spacing: 8) {
                    Button("Change dates") { picking = .lmp }
                    Button("Remove") { confirmRemove = true }
                }
                .buttonStyle(PillButtonStyle(primary: false, small: true))
            }
            .frame(maxWidth: .infinity)
            .card()

            Heading.section("Scans to plan")
            VStack(spacing: 0) {
                ForEach(Pregnancy.scans) { scan in scanRow(scan, p) }
            }
            .card(padding: 16)

            VStack(alignment: .leading, spacing: 4) {
                Text("Regular check-ups").font(.system(size: 17, weight: .semibold)).foregroundStyle(Palette.text)
                Text("The gynecologist sees you through every trimester, and after your baby arrives.")
                    .font(.subheadline)
                    .foregroundStyle(Palette.text2)
                Button("Book a check-up") { router.book("gyn") }
                    .buttonStyle(PillButtonStyle(fullWidth: true))
                    .padding(.top, 12)
            }
            .card()
        }
    }

    private func animate(to value: Double) {
        withAnimation(.spring(response: 0.9, dampingFraction: 0.9)) { shownFraction = value }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 17, weight: .semibold)).foregroundStyle(Palette.text).lineLimit(1)
            Text(label).font(.footnote).foregroundStyle(Palette.text3)
        }
        .frame(maxWidth: .infinity)
    }

    private func scanRow(_ scan: Pregnancy.Scan, _ p: Pregnancy.Progress) -> some View {
        let state = p.state(scan)
        let today = Clinic.todayISO()
        let start = p.start(scan)
        return HStack(spacing: 14) {
            Image(systemName: state == .done ? "checkmark.circle.fill" : state == .now ? "smallcircle.filled.circle" : "circle")
                .font(.system(size: 20))
                .foregroundStyle(state == .later ? Palette.text3 : Palette.text)
            VStack(alignment: .leading, spacing: 2) {
                Text(scan.name)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(state == .done ? Palette.text2 : Palette.text)
                Text(state == .done
                     ? "\(scan.startWeek)–\(scan.endWeek) weeks"
                     : "\(scan.startWeek)–\(scan.endWeek) weeks · \(Clinic.dayMonth(start)) – \(Clinic.dayMonth(p.end(scan)))")
                    .font(.system(size: 14))
                    .foregroundStyle(Palette.text2)
            }
            Spacer(minLength: 8)
            if state != .done && !p.overdue {
                Button("Book") {
                    let notes = "\(scan.name) (\(scan.startWeek)–\(scan.endWeek) weeks). Currently \(p.weeks) weeks \(p.dayOfWeek) days."
                    // Suggest the start of the window when it's still ahead and bookable.
                    let date = start > today && Clinic.daysBetween(today, start) <= Clinic.maxDaysAhead ? start : nil
                    router.book("usg", notes: notes, date: date)
                }
                .buttonStyle(PillButtonStyle(primary: state == .now, small: true))
            }
        }
        .padding(.vertical, 10)
    }
}

/// Graphical date picker limited to sensible dates for the last period or due date.
private struct DateSheet: View {
    let mode: PregnancyView.PickMode
    let current: String?
    let onSave: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()

    private var range: ClosedRange<Date> {
        let today = Clinic.date(Clinic.todayISO()) ?? Date()
        let cal = Clinic.calendar
        return mode == .lmp
            ? cal.date(byAdding: .day, value: -300, to: today)!...today
            : today...cal.date(byAdding: .day, value: 300, to: today)!
    }

    var body: some View {
        NavigationStack {
            DatePicker(
                mode == .lmp ? "First day of your last period" : "Your due date",
                selection: $date,
                in: range,
                displayedComponents: .date
            )
            .datePickerStyle(.graphical)
            .environment(\.timeZone, Clinic.timeZone)
            .padding()
            .navigationTitle(mode == .lmp ? "Last period" : "Due date")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(Clinic.iso(date))
                        dismiss()
                    }
                }
            }
            .onAppear {
                if let current, let d = Clinic.date(mode == .lmp ? current : Clinic.addDays(current, Pregnancy.termDays)) {
                    date = min(max(d, range.lowerBound), range.upperBound)
                } else {
                    date = mode == .lmp ? range.upperBound : range.lowerBound
                }
            }
            Spacer()
        }
        .tint(Palette.text)
    }
}
