import Foundation

/// UserDefaults-backed pairing-token storage - mirrors the Android app's
/// SharedPreferences usage in MainActivity.kt (same key names, separate storage
/// per platform since there's no shared-prefs equivalent across OSes).
enum TokenStore {
    private static let defaults = UserDefaults.standard

    static var laptopToken: String? {
        get { defaults.string(forKey: "laptop_token") }
        set { defaults.set(newValue, forKey: "laptop_token") }
    }

    static var phoneToken: String? {
        get { defaults.string(forKey: "phone_token") }
        set { defaults.set(newValue, forKey: "phone_token") }
    }

    static func forgetLaptopToken() { defaults.removeObject(forKey: "laptop_token") }
    static func forgetPhoneToken() { defaults.removeObject(forKey: "phone_token") }
}
