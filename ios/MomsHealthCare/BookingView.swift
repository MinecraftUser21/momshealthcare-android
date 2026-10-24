import SwiftUI
import UIKit

/// The booking form: same fields and rules as the website, plus saved patients.
struct BookingView: View {
    let prefill: Router.Booking

    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var name = ""
    @State private var phone = ""
    @State private var serviceId: String?
    @State private var date: String?
    @State private var slotId: String?
    @State private var notes = ""
    @State private var remember = true
    @State private var showErrors = false
    @State private var sending = false
    @State private var failed = false
    @State private var sent: Store.Request?
    @State private var calendarFor: Store.Request?
    @State private var pickingDate = false
    @FocusState private var focus: Field?

    enum Field { case name, phone, notes }

    private let quickDays = 14

    var body: some View {
        NavigationStack {
            ScrollView {
                Group {
                    if let sent {
                        sentView(sent)
                            .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity), removal: .opacity))
                    } else {
                        form
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
                .readable()
            }
            .scrollDismissesKeyboard(.interactively)
            .screen()
            .navigationTitle(sent == nil ? "Book a visit" : "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(sent == nil ? "Cancel" : "Done") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { openURL(Clinic.phoneURL) } label: { Image(systemName: "phone") }
                        .accessibilityLabel("Call the clinic")
                }
            }
            .alert("Couldn’t send your request", isPresented: $failed) {
                Button("Try again") { submit() }
                Button("Use email app") { openURL(emailURL()) }
                Button("Call") { openURL(Clinic.phoneURL) }
                Button("Close", role: .cancel) {}
            } message: {
                Text("Check your internet connection and try again. You can also send it from your email app, or call the clinic.")
            }
            .sheet(isPresented: $pickingDate) { otherDateSheet }
            .sheet(item: $calendarFor) { r in CalendarSheet(request: r) { calendarFor = nil } }
        }
        .tint(Palette.text)
        .onAppear(perform: applyPrefill)
    }

    // MARK: Form

    private var form: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Send a request and the clinic will call you to confirm a time.")
                .font(.system(size: 17))
                .foregroundStyle(Palette.text2)
                .padding(.top, 8)

            VStack(alignment: .leading, spacing: 22) {
                if !store.profiles.isEmpty {
                    group("Who is this for?") {
                        FlowLayout {
                            ForEach(store.profiles) { p in
                                ChoicePill(title: p.name, selected: p.name == name && Clinic.normalisePhone(phone) == p.phone) {
                                    name = p.name
                                    phone = Clinic.prettyPhone(p.phone)
                                }
                            }
                        }
                    }
                }

                group("Patient name", error: nameError) {
                    TextField("", text: $name)
                        .textContentType(.name)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.next)
                        .focused($focus, equals: .name)
                        .onSubmit { focus = .phone }
                        .fieldStyle(focused: focus == .name, invalid: nameError != nil)
                }

                group("Mobile number", error: phoneError) {
                    HStack(spacing: 12) {
                        Text("+91").foregroundStyle(Palette.text2)
                        Rectangle().fill(Palette.line).frame(width: 1, height: 22)
                        TextField("", text: $phone)
                            .keyboardType(.phonePad)
                            .textContentType(.telephoneNumber)
                            .focused($focus, equals: .phone)
                    }
                    .fieldStyle(focused: focus == .phone, invalid: phoneError != nil)
                    Toggle("Save these details for next time", isOn: $remember)
                        .font(.subheadline)
                        .foregroundStyle(Palette.text2)
                        .tint(Palette.open)
                        .padding(.top, 6)
                }

                group("Service", error: showErrors && serviceId == nil ? "Please choose a service." : nil) {
                    FlowLayout {
                        ForEach(Clinic.services) { svc in
                            ChoicePill(title: svc.label, selected: serviceId == svc.id) { serviceId = svc.id }
                        }
                    }
                }

                group("Preferred date", error: dateError) {
                    dateStrip
                    if let date {
                        Text(Clinic.longDate(date)).font(.subheadline).foregroundStyle(Palette.text2).padding(.top, 4)
                    }
                }

                group("Preferred time", error: showErrors && slotId == nil ? "Please choose a time." : nil) {
                    HStack(spacing: 8) {
                        ForEach(Clinic.slots) { slot in slotButton(slot) }
                    }
                }

                group("Notes (optional)") {
                    ZStack(alignment: .topLeading) {
                        if notes.isEmpty {
                            Text("e.g. weeks of pregnancy, referral, previous reports")
                                .foregroundStyle(Palette.text3)
                                .padding(.top, 8)
                                .padding(.leading, 5)
                        }
                        TextEditor(text: $notes)
                            .focused($focus, equals: .notes)
                            .scrollContentBackground(.hidden)
                            .frame(minHeight: 88)
                    }
                    .fieldStyle(focused: focus == .notes, invalid: false)
                }

                Button(action: submit) {
                    Text(sending ? "Sending…" : "Request appointment")
                }
                .buttonStyle(PillButtonStyle(fullWidth: true))
                .disabled(sending)

                Button { openURL(Clinic.emergencyURL) } label: {
                    Text("For a medical emergency, call **112**.")
                        .font(.footnote)
                        .foregroundStyle(Palette.text3)
                        .frame(maxWidth: .infinity)
                }
            }
            .card()
            .padding(.top, 20)
        }
    }

    private func group<Content: View>(_ label: String, error: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(.system(size: 14, weight: .medium)).foregroundStyle(Palette.text2)
            content()
            if let error {
                Text(error).font(.footnote).foregroundStyle(Palette.closed).transition(.opacity)
            }
        }
    }

    private var dateStrip: some View {
        let today = Clinic.todayISO()
        let lateTonight = Clinic.hourNow() >= Clinic.closesHour
        let days = (0..<quickDays).map { Clinic.addDays(today, $0) }
        let far = date.map { !days.contains($0) } ?? false
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(days.enumerated()), id: \.element) { i, iso in
                    dayTile(top: i == 0 ? "Today" : Clinic.weekday(iso), middle: Clinic.dayNumber(iso), bottom: Clinic.month(iso), selected: date == iso) {
                        date = iso
                        dropPassedSlot()
                    }
                    .disabled(i == 0 && lateTonight)
                    .opacity(i == 0 && lateTonight ? 0.35 : 1)
                    .accessibilityLabel(Clinic.longDate(iso))
                }
                if far, let date {
                    dayTile(top: Clinic.weekday(date), middle: Clinic.dayNumber(date), bottom: Clinic.month(date), selected: true) { pickingDate = true }
                } else {
                    dayTile(top: "Other", symbol: "calendar", bottom: "date", selected: false) { pickingDate = true }
                        .accessibilityLabel("Choose a date")
                }
            }
            .padding(.horizontal, 20)
        }
        .padding(.horizontal, -20)
    }

    private func dayTile(top: String, middle: String = "", symbol: String? = nil, bottom: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 1) {
                Text(top).font(.system(size: 12, weight: .medium)).opacity(0.75)
                if let symbol {
                    Image(systemName: symbol).font(.system(size: 19)).frame(height: 27)
                } else {
                    Text(middle).font(.system(size: 22, weight: .semibold))
                }
                Text(bottom).font(.system(size: 12)).opacity(0.75)
            }
            .foregroundStyle(selected ? Palette.onAccent : Palette.text)
            .frame(width: 64, height: 80)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(selected ? Palette.accent : Palette.field))
            .animation(.easeOut(duration: 0.16), value: selected)
        }
        .buttonStyle(PressStyle())
    }

    private func slotButton(_ slot: Clinic.Slot) -> some View {
        let open = slotOpen(slot)
        let selected = slotId == slot.id
        return Button { slotId = slot.id } label: {
            VStack(spacing: 2) {
                Text(slot.title).font(.system(size: 15, weight: .medium)).lineLimit(1)
                Text(slot.range).font(.system(size: 12)).opacity(0.75).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(selected ? Palette.onAccent : Palette.text)
            .frame(maxWidth: .infinity, minHeight: 68)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(selected ? Palette.accent : Palette.field))
            .animation(.easeOut(duration: 0.16), value: selected)
        }
        .buttonStyle(PressStyle())
        .disabled(!open)
        .opacity(open ? 1 : 0.35)
    }

    private var otherDateSheet: some View {
        let today = Clinic.date(Clinic.todayISO()) ?? Date()
        let last = Clinic.calendar.date(byAdding: .day, value: Clinic.maxDaysAhead, to: today) ?? today
        return NavigationStack {
            DatePicker(
                "Preferred date",
                selection: Binding(
                    get: { date.flatMap(Clinic.date) ?? today },
                    set: { date = Clinic.iso($0); dropPassedSlot() }
                ),
                in: today...last,
                displayedComponents: .date
            )
            .datePickerStyle(.graphical)
            .environment(\.timeZone, Clinic.timeZone)
            .padding()
            .navigationTitle("Choose a date")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { pickingDate = false } }
            }
            Spacer()
        }
        .tint(Palette.text)
        .presentationDetents([.large])
    }

    // MARK: Rules (same as the website)

    private var nameError: String? {
        showErrors && name.trimmingCharacters(in: .whitespaces).count < 2 ? "Please enter the patient’s name." : nil
    }

    private var phoneError: String? {
        showErrors && !Clinic.isValidPhone(phone) ? "Enter a valid 10-digit mobile number." : nil
    }

    private var dateError: String? {
        guard showErrors else { return nil }
        guard let date else { return "Please choose a date." }
        return date < Clinic.todayISO() ? "Please choose today or a later date." : nil
    }

    /// Today's slots that have already ended can't be picked.
    private func slotOpen(_ slot: Clinic.Slot) -> Bool {
        !(date == Clinic.todayISO() && Clinic.hourNow() >= slot.endHour)
    }

    private func dropPassedSlot() {
        if let s = Clinic.slot(slotId), !slotOpen(s) { slotId = nil }
    }

    private func applyPrefill() {
        guard name.isEmpty && serviceId == nil else { return }
        serviceId = Clinic.service(prefill.service)?.id
        if let n = prefill.notes { notes = n }
        if let d = prefill.date, d >= Clinic.todayISO() { date = d }
    }

    // MARK: Sending

    private func submit() {
        guard !sending else { return }
        withAnimation(.easeOut(duration: 0.2)) { showErrors = true }
        dropPassedSlot()
        guard nameError == nil, phoneError == nil, dateError == nil,
              let service = Clinic.service(serviceId), let slot = Clinic.slot(slotId), let date else {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            return
        }
        focus = nil
        let digits = Clinic.normalisePhone(phone)
        let cleanName = name.trimmingCharacters(in: .whitespaces)
        let request = Store.Request(
            id: Int64(Date().timeIntervalSince1970 * 1000),
            name: cleanName, phone: digits, service: service.label, date: date, slot: slot.id
        )
        let trimmedNotes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        sending = true
        Task {
            let result = await Sender.send(Sender.bookingFields(request, notes: trimmedNotes))
            sending = false
            if result == .sent {
                store.add(request)
                if remember { store.remember(.init(name: cleanName, phone: digits)) }
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                withAnimation(.spring(response: 0.5, dampingFraction: 0.85)) { sent = request }
            } else {
                failed = true
            }
        }
    }

    /// Fallback: the same request, addressed to the doctor, in the email app.
    private func emailURL() -> URL {
        let service = Clinic.service(serviceId)
        let slot = Clinic.slot(slotId)
        let body = """
        New appointment request · Mom’s Health Care

        Patient Name: \(name.trimmingCharacters(in: .whitespaces))
        Phone: \(Clinic.normalisePhone(phone))
        Service: \(service?.emailValue ?? "")
        Preferred Date: \(date.map(Clinic.emailDate) ?? "")
        Preferred Time: \(slot?.emailValue ?? "")
        \(notes.isEmpty ? "" : "Notes: \(notes)\n")
        Sent from the Mom’s Health Care iOS app
        """
        var c = URLComponents()
        c.scheme = "mailto"
        c.path = Clinic.email
        c.queryItems = [
            URLQueryItem(name: "subject", value: "New appointment request · Mom’s Health Care"),
            URLQueryItem(name: "body", value: body),
        ]
        return c.url ?? URL(string: "mailto:\(Clinic.email)")!
    }

    // MARK: Sent

    private func sentView(_ r: Store.Request) -> some View {
        let slot = Clinic.slot(r.slot)
        return VStack(spacing: 0) {
            SentCheck().padding(.top, 36)
            Heading.title("Request sent.").padding(.top, 24)
            Text("The clinic will call you on \(Clinic.prettyPhone(r.phone)) to confirm a time.")
                .font(.system(size: 17))
                .foregroundStyle(Palette.text2)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            VStack(spacing: 0) {
                summaryRow("Patient", r.name)
                Divider().overlay(Palette.line)
                summaryRow("Service", r.service)
                Divider().overlay(Palette.line)
                summaryRow("Date", Clinic.longDate(r.date))
                Divider().overlay(Palette.line)
                summaryRow("Time", slot.map { "\($0.title), \($0.range)" } ?? "")
            }
            .card(padding: 18)
            .padding(.top, 28)
            Button("Done") { dismiss() }
                .buttonStyle(PillButtonStyle(fullWidth: true))
                .padding(.top, 24)
            Button("Add to calendar") { Task { if await CalendarAccess.ready() { calendarFor = r } } }
                .buttonStyle(PillButtonStyle(primary: false, fullWidth: true))
                .padding(.top, 10)
        }
    }

    private func summaryRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label).foregroundStyle(Palette.text2)
            Spacer(minLength: 16)
            Text(value).font(.system(size: 15, weight: .medium)).foregroundStyle(Palette.text).multilineTextAlignment(.trailing)
        }
        .font(.system(size: 15))
        .padding(.vertical, 12)
    }
}

/// A ring and tick that draw themselves in.
private struct SentCheck: View {
    @State private var ring: CGFloat = 0
    @State private var tick: CGFloat = 0

    var body: some View {
        ZStack {
            Circle().trim(from: 0, to: ring)
                .stroke(Palette.text, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Tick().trim(from: 0, to: tick)
                .stroke(Palette.text, style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
        }
        .frame(width: 76, height: 76)
        .onAppear {
            withAnimation(.easeOut(duration: 0.55)) { ring = 1 }
            withAnimation(.easeOut(duration: 0.35).delay(0.4)) { tick = 1 }
        }
        .accessibilityHidden(true)
    }

    private struct Tick: Shape {
        func path(in r: CGRect) -> Path {
            var p = Path()
            p.move(to: CGPoint(x: r.width * 0.32, y: r.height * 0.52))
            p.addLine(to: CGPoint(x: r.width * 0.45, y: r.height * 0.64))
            p.addLine(to: CGPoint(x: r.width * 0.69, y: r.height * 0.38))
            return p
        }
    }
}

private extension View {
    /// The website's filled, rounded text field.
    func fieldStyle(focused: Bool, invalid: Bool) -> some View {
        self.padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.field))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(invalid ? Palette.closed : focused ? Palette.text2 : .clear, lineWidth: 1.5)
            )
            .animation(.easeOut(duration: 0.15), value: focused)
            .foregroundStyle(Palette.text)
    }
}
