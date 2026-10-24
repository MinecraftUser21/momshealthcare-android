import SwiftUI
import UIKit

/// The website's black-and-white palette (dark), and its inverse (light).
enum Palette {
    static let bg = dynamic(light: 0xFFFFFF, dark: 0x000000)
    static let surface = dynamic(light: 0xF5F5F7, dark: 0x161617)
    static let surface2 = dynamic(light: 0xEDEDF0, dark: 0x1D1D1F)
    static let field = dynamic(light: 0xEBEBEE, dark: 0x232325)
    static let text = dynamic(light: 0x1D1D1F, dark: 0xF5F5F7)
    static let text2 = dynamic(light: 0x6E6E73, dark: 0x86868B)
    static let text3 = dynamic(light: 0x86868B, dark: 0x6E6E73)
    static let line = dynamic(light: 0x000000, dark: 0xFFFFFF, alpha: 0.12)
    static let accent = dynamic(light: 0x000000, dark: 0xFFFFFF)
    static let onAccent = dynamic(light: 0xFFFFFF, dark: 0x000000)
    static let secondaryFill = dynamic(light: 0x000000, dark: 0xFFFFFF, alpha: 0.08)
    static let open = dynamic(light: 0x28A745, dark: 0x30D158)
    static let closed = dynamic(light: 0xE5352B, dark: 0xFF453A)

    private static func dynamic(light: UInt32, dark: UInt32, alpha: CGFloat = 1) -> Color {
        Color(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light, alpha: alpha)
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

/// System type with the website's tight tracking (SF Pro is what the site shows on Apple devices).
enum Heading {
    static func title(_ s: String) -> some View {
        Text(s).font(.system(size: 30, weight: .semibold)).tracking(-0.8).foregroundStyle(Palette.text)
    }
    static func headline(_ s: String) -> some View {
        Text(s).font(.system(size: 20, weight: .semibold)).tracking(-0.4).foregroundStyle(Palette.text)
    }
    static func section(_ s: String) -> some View {
        Text(s).font(.system(size: 19, weight: .semibold)).tracking(-0.3).foregroundStyle(Palette.text)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 28)
            .padding(.bottom, 2)
    }
}

/// Pill buttons, like the website's.
struct PillButtonStyle: ButtonStyle {
    var primary = true
    var small = false
    var fullWidth = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: small ? 15 : 17, weight: .medium))
            .lineLimit(1)
            .padding(.horizontal, small ? 16 : 24)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: small ? 36 : 50)
            .foregroundStyle(primary ? Palette.onAccent : Palette.text)
            .background(Capsule().fill(primary ? Palette.accent : Palette.secondaryFill))
            .contentShape(Capsule())
            .opacity(configuration.isPressed ? 0.82 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.18), value: configuration.isPressed)
    }
}

/// Gentle press feedback for cards and tiles.
struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.easeOut(duration: 0.18), value: configuration.isPressed)
    }
}

extension View {
    /// The website's rounded grey card.
    func card(padding: CGFloat = 20) -> some View {
        self.padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Palette.surface))
    }

    /// Keeps reading width comfortable on iPad and Mac.
    func readable() -> some View {
        frame(maxWidth: 680).frame(maxWidth: .infinity)
    }

    func screen() -> some View {
        background(Palette.bg.ignoresSafeArea())
    }
}
