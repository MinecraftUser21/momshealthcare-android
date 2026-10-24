import SwiftUI

/// What a service is, how to prepare for it, and a button to book it.
struct ServiceSheet: View {
    let service: Clinic.Service
    @EnvironmentObject private var router: Router
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                IconCircle(symbol: service.symbol, size: 56)
                Heading.title(service.name).padding(.top, 20)
                Text(service.desc)
                    .font(.system(size: 17))
                    .foregroundStyle(Palette.text2)
                    .padding(.top, 10)

                Text("How to prepare")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Palette.text)
                    .padding(.top, 28)
                ForEach(service.prep, id: \.self) { line in
                    HStack(alignment: .top, spacing: 14) {
                        Circle().fill(Palette.text2).frame(width: 6, height: 6).padding(.top, 8)
                        Text(line).font(.system(size: 16)).foregroundStyle(Palette.text)
                    }
                    .padding(.top, 12)
                }
                Text("General guidance. The clinic will confirm exactly what you need when they call.")
                    .font(.footnote)
                    .foregroundStyle(Palette.text3)
                    .padding(.top, 16)

                Button("Book \(service.label)") {
                    dismiss()
                    // Let this sheet close before the booking sheet opens.
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { router.book(service.id) }
                }
                .buttonStyle(PillButtonStyle(fullWidth: true))
                .padding(.top, 24)
            }
            .padding(24)
            .padding(.top, 8)
        }
        .background(Palette.surface2.ignoresSafeArea())
        .presentationDragIndicator(.visible)
    }
}
