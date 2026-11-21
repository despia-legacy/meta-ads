import Foundation
func check(_ ok: @autoclosure () throws -> Bool) rethrows { if try !ok() { fatalError("validation mismatch") } }
func rejects(_ operation: () throws -> Any) { do { _ = try operation(); fatalError("invalid value accepted") } catch {} }
try check(try MetaAdsValues.number("12.50") == 12.5)
for value in ["NaN", "Infinity", "1e999", "invalid"] { rejects { try MetaAdsValues.number(value) } }
try check(try MetaAdsValues.currency("USD") == "USD")
for value in ["usd", "US", "123"] { rejects { try MetaAdsValues.currency(value) } }
try check(try MetaAdsValues.properties("{\"text\":\"ok\",\"number\":2,\"flag\":true}").count == 3)
for value in ["[]", "null", "{\"nested\":{}}", "{\"array\":[]}", "{\"null\":null}", "{\"\":1}"] { rejects { try MetaAdsValues.properties(value) } }
let many = Dictionary(uniqueKeysWithValues: (0..<26).map { ("p\($0)", $0) })
let text = String(data: try JSONSerialization.data(withJSONObject: many), encoding: .utf8)!
rejects { try MetaAdsValues.properties(text) }
print("PASS: finite numbers, currency format and flat event properties")
