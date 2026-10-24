import Foundation

/// Everything about the clinic that the website also knows: contacts, hours, services.
enum Clinic {
    static let email = "drpubalisarkar@gmail.com"
    static let phoneURL = URL(string: "tel:+918420698958")!
    static let phoneDisplay = "084206 98958"
    static let emergencyURL = URL(string: "tel:112")!

    /// The website's own FormSubmit form, already activated for the inbox above.
    static let formEndpoint = URL(string: "https://formsubmit.co/ajax/drpubalisarkar@gmail.com")!
    static let website = "https://momshealthcare.site.je/"
    static let websiteOrigin = "https://momshealthcare.site.je"
    static let repoURL = URL(string: "https://github.com/MinecraftUser21/momshealthcare-android")!

    static let directionsURL = URL(string: "https://www.google.com/maps/dir/?api=1&destination=F886%2BQC%2C%20Kolkata%2C%20West%20Bengal")!
    static let address = "2nd, Sister Nivedita Rd, East Barisha Govt Colony, Manjhi Para, Thakurpukur, Kolkata, West Bengal 700063"
    static let plusCode = "Plus Code F886+QC"

    static let opensHour = 8
    static let closesHour = 21
    static let maxDaysAhead = 90

    // MARK: Services

    struct Service: Identifiable, Hashable {
        let id: String
        let name: String
        let tag: String?
        let short: String
        let desc: String
        let symbol: String
        let prep: [String]
        /// Short label for the form and summaries.
        let label: String
        /// Value sent to the clinic inbox (same as the website's <option value>).
        let emailValue: String
    }

    static let services: [Service] = [
        Service(
            id: "usg", name: "Ultrasound", tag: "USG",
            short: "Pregnancy, abdomen and pelvis scans.",
            desc: "See how your baby is growing, or get a clear look at your abdomen and pelvis. We’ll explain what the scan shows before you leave.",
            symbol: "waveform",
            prep: [
                "Abdominal scans: usually nothing to eat for 6–8 hours before. Water is fine.",
                "Pelvic and early pregnancy scans: a full bladder helps. Drink 4–5 glasses of water an hour before and don’t empty your bladder.",
                "Bring previous scan reports and your doctor’s prescription.",
            ],
            label: "Ultrasound (USG)", emailValue: "USG (Ultrasound)"
        ),
        Service(
            id: "echo", name: "Echocardiogram", tag: "ECHO",
            short: "A painless scan of how your heart pumps.",
            desc: "A painless scan that shows how well your heart is pumping. Doctors often suggest one after breathlessness, chest pain or a murmur.",
            symbol: "heart.text.square",
            prep: [
                "No fasting or special preparation is needed.",
                "Wear a loose top that’s easy to open at the front.",
                "Bring earlier ECG or ECHO reports and a list of your medicines.",
            ],
            label: "Echocardiogram (ECHO)", emailValue: "ECHO (Echocardiogram)"
        ),
        Service(
            id: "gyn", name: "Gynecology & maternal care", tag: nil,
            short: "Before, during and after pregnancy.",
            desc: "Check-ups before, during and after pregnancy, and someone to talk to about periods, PCOS or anything else on your mind.",
            symbol: "figure.and.child.holdinghands",
            prep: [
                "Note the first day of your last period.",
                "Bring your antenatal card and previous reports, if you have them.",
                "List any medicines or supplements you take.",
            ],
            label: "Gynecology & maternal care", emailValue: "Gynecology & Maternal Care"
        ),
        Service(
            id: "cardio", name: "Cardiology", tag: nil,
            short: "BP, palpitations and heart check-ups.",
            desc: "For blood pressure, palpitations or chest discomfort, or simply a check-up if heart trouble runs in your family.",
            symbol: "waveform.path.ecg",
            prep: [
                "Bring earlier ECG, ECHO and blood test reports.",
                "Bring your current medicines, or a list of them.",
                "If you check your blood pressure at home, bring your recent readings.",
            ],
            label: "Cardiology", emailValue: "Cardiology Consultation"
        ),
        Service(
            id: "opd", name: "General OPD", tag: nil,
            short: "A doctor every day, for the whole family.",
            desc: "Fever, cough, a stomach upset or a routine check-up. See a doctor any day of the week, for anyone in the family.",
            symbol: "stethoscope",
            prep: [
                "Bring previous prescriptions and reports.",
                "Note when the symptoms started and any medicines already taken.",
            ],
            label: "General OPD", emailValue: "General OPD"
        ),
    ]

    static func service(_ id: String?) -> Service? { services.first { $0.id == id } }
    static func service(label: String) -> Service? { services.first { $0.label == label } }

    // MARK: Time slots

    struct Slot: Identifiable, Hashable {
        let id: String
        let title: String
        let range: String
        let emailValue: String
        let startHour: Int
        /// Hour (Kolkata) at which this slot is over for today.
        let endHour: Int
    }

    static let slots: [Slot] = [
        Slot(id: "morning", title: "Morning", range: "8 AM – 12 PM", emailValue: "Morning (8 AM – 12 PM)", startHour: 8, endHour: 12),
        Slot(id: "afternoon", title: "Afternoon", range: "12 – 4 PM", emailValue: "Afternoon (12 PM – 4 PM)", startHour: 12, endHour: 16),
        Slot(id: "evening", title: "Evening", range: "4 – 9 PM", emailValue: "Evening (4 PM – 9 PM)", startHour: 16, endHour: 21),
    ]

    static func slot(_ id: String?) -> Slot? { slots.first { $0.id == id } }

    // MARK: Kolkata time. The clinic's day decides what can be booked, wherever the phone is.

    static let timeZone = TimeZone(identifier: "Asia/Kolkata")!

    static var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = timeZone
        return c
    }

    static func hourNow() -> Int { calendar.component(.hour, from: Date()) }
    static func isOpen(hour: Int = hourNow()) -> Bool { hour >= opensHour && hour < closesHour }

    static func statusText(hour: Int = hourNow()) -> String {
        if isOpen(hour: hour) { return "Open now · until 9 PM" }
        return hour < opensHour ? "Closed · opens 8 AM" : "Closed · opens 8 AM tomorrow"
    }

    static func greeting(hour: Int = hourNow()) -> String {
        hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening"
    }

    private static func formatter(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.timeZone = timeZone
        f.dateFormat = pattern
        return f
    }

    private static let isoFormat = formatter("yyyy-MM-dd")
    private static let longFormat = formatter("EEEE, d MMMM")
    private static let shortFormat = formatter("EEE, d MMM")
    private static let emailFormat = formatter("EEE, d MMM yyyy")
    private static let dayMonthFormat = formatter("d MMM")
    private static let weekdayFormat = formatter("EEE")
    private static let monthFormat = formatter("MMM")

    /// yyyy-MM-dd for a Kolkata calendar day.
    static func iso(_ date: Date) -> String { isoFormat.string(from: date) }
    static func todayISO() -> String { iso(Date()) }
    static func date(_ iso: String) -> Date? { isoFormat.date(from: iso) }

    static func addDays(_ iso: String, _ days: Int) -> String {
        guard let d = date(iso), let next = calendar.date(byAdding: .day, value: days, to: d) else { return iso }
        return self.iso(next)
    }

    static func daysBetween(_ from: String, _ to: String) -> Int {
        guard let a = date(from), let b = date(to) else { return 0 }
        return calendar.dateComponents([.day], from: a, to: b).day ?? 0
    }

    static func longDate(_ iso: String) -> String { date(iso).map { longFormat.string(from: $0) } ?? iso }
    static func shortDate(_ iso: String) -> String { date(iso).map { shortFormat.string(from: $0) } ?? iso }
    static func emailDate(_ iso: String) -> String { date(iso).map { emailFormat.string(from: $0) } ?? iso }
    static func dayMonth(_ iso: String) -> String { date(iso).map { dayMonthFormat.string(from: $0) } ?? iso }
    static func weekday(_ iso: String) -> String { date(iso).map { weekdayFormat.string(from: $0) } ?? "" }
    static func month(_ iso: String) -> String { date(iso).map { monthFormat.string(from: $0) } ?? "" }
    static func dayNumber(_ iso: String) -> String { date(iso).map { String(calendar.component(.day, from: $0)) } ?? "" }

    /// [hour]:00 Kolkata time on [iso].
    static func moment(_ iso: String, hour: Int) -> Date {
        let day = date(iso) ?? Date()
        return calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day) ?? day
    }

    // MARK: Phone numbers: same rules as the website.

    static func normalisePhone(_ raw: String) -> String {
        var digits = raw.filter(\.isNumber)
        if digits.count == 12 && digits.hasPrefix("91") { digits.removeFirst(2) }
        if digits.count == 11 && digits.hasPrefix("0") { digits.removeFirst() }
        return digits
    }

    static func isValidPhone(_ raw: String) -> Bool {
        let d = normalisePhone(raw)
        guard d.count == 10, let first = d.first else { return false }
        return "6789".contains(first)
    }

    /// "98765 43210"
    static func prettyPhone(_ digits: String) -> String {
        guard digits.count == 10 else { return digits }
        return "\(digits.prefix(5)) \(digits.suffix(5))"
    }
}
