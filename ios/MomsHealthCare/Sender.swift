import Foundation

/// Sends requests to the clinic inbox through the same FormSubmit form the website uses,
/// so the doctor gets an identical email whichever way the patient books.
enum Sender {

    enum Result {
        case sent
        /// FormSubmit needs the inbox owner to click its activation link first.
        case notActivated
        case failed
    }

    static func send(_ fields: [(String, String)]) async -> Result {
        var request = URLRequest(url: Clinic.formEndpoint, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        // FormSubmit ties the form to the page it lives on; this is the website's form.
        request.setValue(Clinic.website, forHTTPHeaderField: "Referer")
        request.setValue(Clinic.websiteOrigin, forHTTPHeaderField: "Origin")
        request.httpBody = fields.map { "\(encode($0.0))=\(encode($0.1))" }.joined(separator: "&").data(using: .utf8)

        // One quiet retry for flaky mobile connections.
        for attempt in 0..<2 {
            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
                let success = "\(json["success"] ?? "")" == "true"
                let message = "\(json["message"] ?? "")"
                if (200..<300).contains(code) && success { return .sent }
                if message.range(of: "activat", options: .caseInsensitive) != nil { return .notActivated }
                return .failed
            } catch {
                if attempt == 0 { try? await Task.sleep(nanoseconds: 1_200_000_000) }
            }
        }
        return .failed
    }

    private static func encode(_ s: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._*")
        return (s.addingPercentEncoding(withAllowedCharacters: allowed) ?? s)
    }

    /// Field names match the website's form.
    static func bookingFields(_ r: Store.Request, notes: String) -> [(String, String)] {
        var f: [(String, String)] = [
            ("_subject", "New appointment request · Mom’s Health Care"),
            ("_template", "table"),
            ("_captcha", "false"),
            ("Patient Name", r.name),
            ("Phone", r.phone),
            ("Service", Clinic.service(label: r.service)?.emailValue ?? r.service),
            ("Preferred Date", Clinic.emailDate(r.date)),
            ("Preferred Time", Clinic.slot(r.slot)?.emailValue ?? ""),
        ]
        if !notes.isEmpty { f.append(("Notes", notes)) }
        f.append(("Booked via", "iOS app"))
        return f
    }

    static func cancelFields(_ r: Store.Request) -> [(String, String)] {
        [
            ("_subject", "Appointment cancellation · Mom’s Health Care"),
            ("_template", "table"),
            ("_captcha", "false"),
            ("Request", "CANCEL this appointment request"),
            ("Patient Name", r.name),
            ("Phone", r.phone),
            ("Service", Clinic.service(label: r.service)?.emailValue ?? r.service),
            ("Preferred Date", Clinic.emailDate(r.date)),
            ("Preferred Time", Clinic.slot(r.slot)?.emailValue ?? ""),
            ("Booked via", "iOS app"),
        ]
    }
}
