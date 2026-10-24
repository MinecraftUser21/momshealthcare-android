import SwiftUI

/// Every request sent from this device: upcoming first, then earlier and cancelled ones.
struct AppointmentsView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @Environment(\.openURL) private var openURL
    @State private var calendarFor: Store.Request?
    @State private var cancelling: Store.Request?
    @State private var sending = false
    @State private var cancelFailed = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if store.requests.isEmpty {
                    empty
                } else {
                    Text("Requests you’ve sent. The clinic calls to confirm each one.")
                        .font(.subheadline)
                        .foregroundStyle(Palette.text2)
                    ForEach(store.upcoming) { card($0, upcoming: true) }
                    if !store.earlier.isEmpty {
                        Heading.section("Earlier")
                        ForEach(store.earlier) { card($0, upcoming: false) }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
            .readable()
        }
        .screen()
        .navigationTitle("Appointments")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { router.book() } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Book a visit")
            }
        }
        .sheet(item: $calendarFor) { r in CalendarSheet(request: r) { calendarFor = nil } }
        .confirmationDialog(
            "Cancel this request?",
            isPresented: Binding(get: { cancelling != nil }, set: { if !$0 { cancelling = nil } }),
            titleVisibility: .visible,
            presenting: cancelling
        ) { r in
            Button("Cancel request", role: .destructive) { cancel(r) }
            Button("Keep", role: .cancel) {}
        } message: { _ in
            Text("We’ll let the clinic know you no longer need this appointment.")
        }
        .alert("Couldn’t reach the clinic", isPresented: $cancelFailed) {
            Button("Call") { openURL(Clinic.phoneURL) }
            Button("Close", role: .cancel) {}
        } message: {
            Text("Please call to cancel.")
        }
    }

    private var empty: some View {
        VStack(spacing: 8) {
            IconCircle(symbol: "calendar", size: 72)
            Heading.headline("No appointments yet").padding(.top, 12)
            Text("Book a visit and the clinic will call you to confirm a time.")
                .font(.subheadline)
                .foregroundStyle(Palette.text2)
                .multilineTextAlignment(.center)
            Button("Book a visit") { router.book() }
                .buttonStyle(PillButtonStyle())
                .padding(.top, 16)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 72)
    }

    private func card(_ r: Store.Request, upcoming: Bool) -> some View {
        let slot = Clinic.slot(r.slot)
        let cancelled = r.status == Store.Request.cancelled
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(cancelled ? "Cancelled" : upcoming ? "Waiting for call" : "Past")
                    .font(.system(size: 12, weight: .medium))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(Palette.field))
                    .foregroundStyle(Palette.text)
                Spacer()
                Text(Clinic.shortDate(r.date)).font(.footnote).foregroundStyle(Palette.text3)
            }
            Heading.headline(r.service).padding(.top, 10)
            Text([Clinic.longDate(r.date), slot.map { "\($0.title), \($0.range)" }].compactMap { $0 }.joined(separator: " · "))
                .foregroundStyle(Palette.text)
            Text("For \(r.name) · \(Clinic.prettyPhone(r.phone))").font(.subheadline).foregroundStyle(Palette.text2)
            HStack(spacing: 8) {
                if upcoming {
                    Button("Add to calendar") { Task { if await CalendarAccess.ready() { calendarFor = r } } }
                    Button("Cancel") { cancelling = r }.disabled(sending)
                } else {
                    Button("Book again") { router.book(Clinic.service(label: r.service)?.id) }
                }
            }
            .buttonStyle(PillButtonStyle(primary: false, small: true))
            .padding(.top, 12)
        }
        .card()
        .opacity(cancelled ? 0.6 : 1)
    }

    private func cancel(_ r: Store.Request) {
        sending = true
        Task {
            let result = await Sender.send(Sender.cancelFields(r))
            sending = false
            if result == .sent {
                store.cancel(r.id)
                router.show(toast: "The clinic has been told")
            } else {
                cancelFailed = true
            }
        }
    }
}
