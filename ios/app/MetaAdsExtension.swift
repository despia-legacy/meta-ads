import Foundation
import FBSDKCoreKit
import AppTrackingTransparency

@MainActor
final class MetaAdsExtension: NativeExtension {
    private var configured = false
    private var enabled = false
    private var sdkReady = false
    required init() {}
    func didLaunch(_ context: NativeExtContext) {}

    func handle(_ call: NativeExtCall) async -> NativeExtResult {
        do {
            if call.route == "status" {
                return .ok(["configured": configured, "enabled": enabled, "sdk": "19.0.1"])
            }
            if call.route == "configure" {
                if configured { return .ok(["configured": true, "enabled": enabled]) }
                let appID = Bundle.main.object(forInfoDictionaryKey: "FacebookAppID") as? String ?? ""
                let token = Bundle.main.object(forInfoDictionaryKey: "FacebookClientToken") as? String ?? ""
                guard !appID.isEmpty, !token.isEmpty, !appID.hasPrefix("REPLACE_"), !token.hasPrefix("REPLACE_") else {
                    return .error(code: "not_configured", message: "Set FacebookAppID and FacebookClientToken before approval")
                }
                configured = true
                return .ok(["configured": true, "enabled": false])
            }
            guard configured else { return .error(code: "not_configured", message: "Call configure first") }
            if call.route == "consent" {
                guard call.isActive else { return .error(code: "timeout", message: "The consent request expired") }
                let requested = call.params["enabled"] as? Bool ?? false
                if !sdkReady {
                    guard requested else { return .ok(["enabled": false]) }
                    ApplicationDelegate.shared.initializeSDK()
                    let appID = Bundle.main.object(forInfoDictionaryKey: "FacebookAppID") as? String
                    let token = Bundle.main.object(forInfoDictionaryKey: "FacebookClientToken") as? String
                    guard Settings.shared.appID == appID, Settings.shared.clientToken == token else {
                        return .error(code: "already_configured", message: "Another Meta app configuration owns this SDK")
                    }
                    sdkReady = true
                }
                enabled = requested
                Settings.shared.isAutoLogAppEventsEnabled = enabled && (call.params["automatic_events"] as? Bool ?? false)
                let authorized = ATTrackingManager.trackingAuthorizationStatus == .authorized
                Settings.shared.isAdvertiserIDCollectionEnabled = enabled && authorized && (call.params["advertiser_id"] as? Bool ?? false)
                if #unavailable(iOS 17) { Settings.shared.isAdvertiserTrackingEnabled = authorized }

                return .ok(["enabled": enabled])
            }
            guard enabled else { return .error(code: "consent_required", message: "Enable event collection first") }
            switch call.route {
            case "event":
                let properties = try MetaAdsValues.properties(call.params["properties"] as? String)
                let parameters = Dictionary(uniqueKeysWithValues: properties.map { (AppEvents.ParameterName($0.key), $0.value) })
                let name = AppEvents.Name(call.params["name"] as? String ?? "")
                if let value = call.params["value"] as? String {
                    AppEvents.shared.logEvent(name, valueToSum: try MetaAdsValues.number(value), parameters: parameters)
                } else { AppEvents.shared.logEvent(name, parameters: parameters) }
            case "purchase":
                AppEvents.shared.logPurchase(amount: try MetaAdsValues.number(call.params["amount"] as? String ?? ""),
                    currency: try MetaAdsValues.currency(call.params["currency"] as? String ?? ""),
                    parameters: Dictionary(uniqueKeysWithValues: try MetaAdsValues.properties(call.params["properties"] as? String).map { (AppEvents.ParameterName($0.key), $0.value) }))
            case "activate": AppEvents.shared.activateApp()
            case "user": AppEvents.shared.userID = call.params["id"] as? String
            case "flush": AppEvents.shared.flush()
            default: return .error(code: "not_registered", message: call.route)
            }
            return .ok(["accepted": true])
        } catch { return .error(code: "bad_request", message: "Expected finite numbers, ISO currency and a flat JSON properties object") }
    }
}

// Validate before calling the SDK. No event payload or identity is logged.
enum MetaAdsValues {
    static func number(_ text: String) throws -> Double {
        guard let value = Double(text), value.isFinite else { throw NSError(domain: "metaads", code: 1) }
        return value
    }
    static func currency(_ text: String) throws -> String {
        guard text.range(of: "^[A-Z]{3}$", options: .regularExpression) != nil else { throw NSError(domain: "metaads", code: 2) }
        return text
    }
    static func properties(_ text: String?) throws -> [String: Any] {
        guard let text = text else { return [:] }
        guard let data = text.data(using: .utf8),
              let values = try JSONSerialization.jsonObject(with: data) as? [String: Any], values.count <= 25,
              values.allSatisfy({ !$0.key.isEmpty && $0.key.count <= 40 && ($0.value is String || $0.value is NSNumber) }) else {
            throw NSError(domain: "metaads", code: 3)
        }
        return values
    }
}
