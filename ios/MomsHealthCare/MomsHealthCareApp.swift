import SwiftUI

@main
struct MomsHealthCareApp: App {
    @StateObject private var store = Store()
    @StateObject private var router = Router()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(router)
                .preferredColorScheme(store.appearance.scheme)
        }
    }
}

/// Bottom tabs on iPhone; a sidebar on iPad and Mac, where there's room for one.
struct RootView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        Group {
            if sizeClass == .regular {
                sidebarLayout
            } else {
                tabLayout
            }
        }
        .tint(Palette.text)
        #if DEBUG
        .task {
            // Screenshot mode: "-book YES" opens the booking form once the window is up.
            if UserDefaults.standard.bool(forKey: "book") {
                try? await Task.sleep(nanoseconds: 800_000_000)
                router.book()
            }
        }
        #endif
        .sheet(item: $router.booking) { booking in
            BookingView(prefill: booking)
                .environmentObject(store)
                .environmentObject(router)
                .preferredColorScheme(store.appearance.scheme)
        }
        .sheet(item: $router.service) { service in
            ServiceSheet(service: service)
                .environmentObject(router)
                .presentationDetents([.medium, .large])
                .preferredColorScheme(store.appearance.scheme)
        }
        .overlay(alignment: .bottom) {
            if let text = router.toast {
                ToastView(text: text)
                    .padding(.bottom, 96)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private var tabLayout: some View {
        TabView(selection: $router.tab) {
            ForEach(Router.Tab.allCases) { tab in
                NavigationStack { screen(for: tab) }
                    .tabItem { Label(tab.title, systemImage: tab.symbol) }
                    .tag(tab)
            }
        }
    }

    private var sidebarLayout: some View {
        NavigationSplitView {
            List(Router.Tab.allCases, selection: Binding<Router.Tab?>(
                get: { router.tab },
                set: { if let t = $0 { router.tab = t } }
            )) { tab in
                Label(tab.title, systemImage: tab.symbol).tag(tab)
            }
            .navigationTitle("Mom’s Health Care")
        } detail: {
            NavigationStack { screen(for: router.tab) }
        }
    }

    @ViewBuilder
    private func screen(for tab: Router.Tab) -> some View {
        switch tab {
        case .home: HomeView()
        case .appointments: AppointmentsView()
        case .pregnancy: PregnancyView()
        case .clinic: ClinicView()
        }
    }
}
