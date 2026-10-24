import SwiftUI
import UIKit

/// Where the clinic is, when it's open, every service, and the app's settings.
struct ClinicView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @Environment(\.openURL) private var openURL
    @State private var hour = Clinic.hourNow()

    private let ticker = Timer.publish(every: 60, on: .main, in: .common).autoconnect()
    private let days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                info
                Heading.section("Opening hours")
                hours
                Heading.section("Services")
                services
                emergency
                Heading.section("Settings")
                settings
                VStack(spacing: 6) {
                    Text("Your details stay on this device. When you book, your request is sent to the clinic by email.")
                    Text("Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")")
                }
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
        .navigationTitle("Clinic")
        .onReceive(ticker) { _ in hour = Clinic.hourNow() }
    }

    private var info: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                BrandMark(size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mom’s Health Care").font(.system(size: 17, weight: .semibold)).foregroundStyle(Palette.text)
                    HStack(spacing: 6) {
                        StatusDot(open: Clinic.isOpen(hour: hour))
                        Text(Clinic.statusText(hour: hour)).font(.subheadline).foregroundStyle(Palette.text2)
                    }
                }
            }
            Text(Clinic.address)
                .foregroundStyle(Palette.text)
                .textSelection(.enabled)
                .padding(.top, 20)
            Text(Clinic.plusCode).font(.footnote).foregroundStyle(Palette.text3).padding(.top, 6)
            HStack(spacing: 10) {
                Button("Directions") { openURL(Clinic.directionsURL) }
                    .buttonStyle(PillButtonStyle(fullWidth: true))
                Button("Call") { openURL(Clinic.phoneURL) }
                    .buttonStyle(PillButtonStyle(primary: false, fullWidth: true))
            }
            .padding(.top, 20)
            Button {
                UIPasteboard.general.string = "Mom’s Health Care, \(Clinic.address)"
                router.show(toast: "Address copied")
            } label: {
                Label("Copy address", systemImage: "doc.on.doc")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Palette.text)
                    .padding(.top, 18)
            }
        }
        .card()
    }

    /// Monday to Sunday, with today picked out.
    private var hours: some View {
        // Calendar weekday: Sunday = 1 … Saturday = 7. Our list starts on Monday.
        let today = (Clinic.calendar.component(.weekday, from: Date()) + 5) % 7
        return VStack(spacing: 0) {
            ForEach(Array(days.enumerated()), id: \.offset) { i, day in
                HStack {
                    Text(i == today ? "\(day) (today)" : day)
                        .font(.system(size: 15, weight: i == today ? .semibold : .regular))
                        .foregroundStyle(i == today ? Palette.text : Palette.text2)
                    Spacer()
                    Text("8:00 AM – 9:00 PM")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(i == today ? Palette.text : Palette.text2)
                }
                .padding(.vertical, 10)
            }
        }
        .card(padding: 16)
    }

    private var services: some View {
        VStack(spacing: 0) {
            ForEach(Clinic.services) { svc in
                Button { router.service = svc } label: {
                    row(svc.symbol, svc.name, value: svc.tag)
                }
                .buttonStyle(PressStyle())
            }
        }
        .card(padding: 16)
    }

    private var emergency: some View {
        HStack(spacing: 16) {
            Image(systemName: "cross.case").font(.system(size: 24)).foregroundStyle(Palette.closed)
            VStack(alignment: .leading, spacing: 2) {
                Text("Medical emergency?").font(.system(size: 17, weight: .semibold)).foregroundStyle(Palette.text)
                Text("Call 112 for an ambulance straight away.").font(.system(size: 14)).foregroundStyle(Palette.text2)
            }
            Spacer(minLength: 8)
            Button("Call 112") { openURL(Clinic.emergencyURL) }
                .buttonStyle(PillButtonStyle(small: true))
        }
        .card()
        .padding(.top, 12)
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("Appearance", systemImage: "moon").font(.system(size: 16, weight: .medium)).foregroundStyle(Palette.text)
            Picker("Appearance", selection: $store.appearance) {
                ForEach(Store.Appearance.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.top, 8)
            .padding(.bottom, 12)

            NavigationLink { PatientsView() } label: {
                row("person.2", "Saved patients", value: store.profiles.isEmpty ? "None yet" : "\(store.profiles.count) saved", chevron: true)
            }
            Link(destination: Clinic.repoURL) {
                row("chevron.left.forwardslash.chevron.right", "Open source", value: "GitHub", chevron: true)
            }
            NavigationLink { LicencesView() } label: {
                row("info.circle", "Licences", chevron: true)
            }
        }
        .card()
    }

    private func row(_ symbol: String, _ title: String, value: String? = nil, chevron: Bool = true) -> some View {
        HStack(spacing: 16) {
            Image(systemName: symbol).font(.system(size: 18)).foregroundStyle(Palette.text).frame(width: 24)
            Text(title).font(.system(size: 16, weight: .medium)).foregroundStyle(Palette.text)
            Spacer()
            if let value { Text(value).font(.system(size: 15)).foregroundStyle(Palette.text2) }
            if chevron { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Palette.text3) }
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/// The website's logo: a rounded square with a heartbeat line.
struct BrandMark: View {
    var size: CGFloat = 40
    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
            .fill(Palette.accent)
            .frame(width: size, height: size)
            .overlay(
                MarkLine()
                    .stroke(Palette.onAccent, style: StrokeStyle(lineWidth: size * 0.07, lineCap: .round, lineJoin: .round))
            )
            .accessibilityHidden(true)
    }

    private struct MarkLine: Shape {
        func path(in r: CGRect) -> Path {
            // M5 23h5l4-11 6 16 6-16 4 11h5 on a 40-unit square
            let s = r.width / 40
            let pts: [(CGFloat, CGFloat)] = [(5, 23), (10, 23), (14, 12), (20, 28), (26, 12), (30, 23), (35, 23)]
            var p = Path()
            p.addLines(pts.map { CGPoint(x: r.minX + $0.0 * s, y: r.minY + $0.1 * s) })
            return p
        }
    }
}

struct PatientsView: View {
    @EnvironmentObject private var store: Store

    var body: some View {
        List {
            if store.profiles.isEmpty {
                Text("People you book for are saved here, so next time you can fill the form with one tap.")
                    .foregroundStyle(Palette.text2)
            }
            ForEach(store.profiles) { p in
                VStack(alignment: .leading, spacing: 2) {
                    Text(p.name).foregroundStyle(Palette.text)
                    Text(Clinic.prettyPhone(p.phone)).font(.subheadline).foregroundStyle(Palette.text2)
                }
            }
            .onDelete { idx in idx.map { store.profiles[$0] }.forEach(store.forget) }
        }
        .navigationTitle("Saved patients")
        .toolbar { if !store.profiles.isEmpty { EditButton() } }
    }
}

struct LicencesView: View {
    var body: some View {
        ScrollView {
            Text("""
            This app is free and open source under the MIT License.

            Icons are Apple’s SF Symbols, used as provided by iOS.

            The clinic’s name, logo and text belong to Mom’s Health Care.
            """)
            .foregroundStyle(Palette.text)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
        }
        .screen()
        .navigationTitle("Licences")
    }
}
