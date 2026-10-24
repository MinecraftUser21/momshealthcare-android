import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @Environment(\.openURL) private var openURL
    @State private var hour = Clinic.hourNow()
    @State private var calendarFor: Store.Request?

    private let ticker = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                statusCard
                if let next = store.next { upcomingCard(next) }
                quickActions
                Heading.section("Services")
                services
                Heading.section("Before you come")
                tips
                Text("East Barisha, Thakurpukur · 5.0 on Google")
                    .font(.footnote)
                    .foregroundStyle(Palette.text3)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 16)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
            .readable()
        }
        .screen()
        .navigationTitle(greeting)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { openURL(Clinic.phoneURL) } label: { Image(systemName: "phone") }
                    .accessibilityLabel("Call the clinic")
            }
        }
        .safeAreaInset(edge: .bottom) { bookButton }
        .onReceive(ticker) { _ in hour = Clinic.hourNow() }
        .sheet(item: $calendarFor) { r in CalendarSheet(request: r) { calendarFor = nil } }
    }

    private var greeting: String {
        let base = Clinic.greeting(hour: hour)
        guard let name = store.profiles.first?.name.split(separator: " ").first else { return base }
        return "\(base), \(name)"
    }

    private var bookButton: some View {
        Button { router.book() } label: {
            Label("Book a visit", systemImage: "plus")
        }
        .buttonStyle(PillButtonStyle())
        .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
        .frame(maxWidth: .infinity, alignment: .trailing)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
    }

    private var statusCard: some View {
        let open = Clinic.isOpen(hour: hour)
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                StatusDot(open: open)
                Heading.headline(open ? "Open now" : "Closed")
            }
            Text(open ? "Until 9 PM · Open every day, 8 AM – 9 PM" : hour < Clinic.opensHour ? "Opens at 8 AM today" : "Opens tomorrow at 8 AM")
                .font(.subheadline)
                .foregroundStyle(Palette.text2)
            EcgLine()
                .frame(height: 76)
                .padding(.horizontal, -20)
                .accessibilityHidden(true)
        }
        .padding([.horizontal, .top], 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Palette.surface))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func upcomingCard(_ r: Store.Request) -> some View {
        let slot = Clinic.slot(r.slot)
        return VStack(alignment: .leading, spacing: 4) {
                Label("Upcoming", systemImage: "calendar")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Palette.text2)
                Heading.headline(r.service).padding(.top, 8)
                Text([Clinic.shortDate(r.date), slot.map { "\($0.title), \($0.range)" }].compactMap { $0 }.joined(separator: " · "))
                    .foregroundStyle(Palette.text)
                Text("For \(r.name)").font(.subheadline).foregroundStyle(Palette.text2)
                Button("Add to calendar") {
                    Task { if await CalendarAccess.ready() { calendarFor = r } }
                }
                .buttonStyle(PillButtonStyle(primary: false, small: true))
                .padding(.top, 12)
        }
        .card()
        .contentShape(Rectangle())
        .onTapGesture { router.tab = .appointments }
    }

    private var quickActions: some View {
        HStack(spacing: 10) {
            quick("Book", "calendar.badge.plus") { router.book() }
            quick("Call", "phone") { openURL(Clinic.phoneURL) }
            quick("Directions", "location") { openURL(Clinic.directionsURL) }
            quick("Pregnancy", "figure.and.child.holdinghands") { router.tab = .pregnancy }
        }
    }

    private func quick(_ title: String, _ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: symbol).font(.system(size: 21)).frame(height: 24)
                Text(title).font(.system(size: 13, weight: .medium)).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(Palette.text)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Palette.surface))
        }
        .buttonStyle(PressStyle())
    }

    private var services: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Clinic.services) { svc in
                    Button { router.service = svc } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            IconCircle(symbol: svc.symbol)
                            Text(svc.name)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(Palette.text)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                                .padding(.top, 12)
                            Text(svc.short)
                                .font(.system(size: 14))
                                .foregroundStyle(Palette.text2)
                                .lineLimit(3)
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 0)
                        }
                        .padding(18)
                        .frame(width: 164, height: 184, alignment: .topLeading)
                        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Palette.surface))
                    }
                    .buttonStyle(PressStyle())
                }
            }
            .padding(.horizontal, 20)
        }
        .padding(.horizontal, -20)
    }

    private var tips: some View {
        VStack(alignment: .leading, spacing: 18) {
            tip("checkmark.shield", "Painless and radiation-free", "USG and ECHO use sound waves only. No needles, no radiation.")
            tip("doc.text", "Come prepared", "Some scans need fasting or a full bladder. Bring previous reports and prescriptions.")
            tip("waveform.path.ecg", "Results, then a doctor", "Gynecology, cardiology and OPD doctors are in the same building to review your report.")
        }
        .card()
    }

    private func tip(_ symbol: String, _ title: String, _ body: String) -> some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: symbol).font(.system(size: 19)).foregroundStyle(Palette.text).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 16, weight: .medium)).foregroundStyle(Palette.text)
                Text(body).font(.system(size: 14)).foregroundStyle(Palette.text2)
            }
        }
    }
}
