# Meta Ads

Source preview for Despia V3. This snapshot requires the runtime update currently awaiting release and an approved extension-enabled native build. Uploading these files does not change an installed app.

A native advertiser SDK extension using Meta Facebook Core SDK 19.0.1 on iOS and Android, independently of AppsFlyer. It sends app activation, custom or standard events and purchases through the SDK. It does not display Audience Network ads.

Part of Despia V3 Extensions. First-party V4 support comes through the new package registry.

Source preview. Requires the upcoming approved V3 extension build release. Verify campaign attribution in your advertising account before launch.

## Web frameworks and app builders

Use the Despia SDK with React, Vue, Svelte, Next.js, TanStack or HTML5. Projects built with Lovable, Base44, Replit and other app builders need custom JavaScript and static file hosting. Claude Code, Codex and Cursor can edit the same extension files.

## Install

1. Copy this repository's manifest.json, ios/ and android/ into public/despia/metaads/ in your web app.
2. Add "metaads" to public/despia/extensions.json: `{ "extensions": ["metaads"] }`. The minimal index requires the no-app-identity runtime change to have reached your build. The legacy index remains compatible.
3. Set FacebookAppID, FacebookClientToken and FacebookDisplayName in manifest.json under ios.app.info_plist. Set the matching Android APP_ID and CLIENT_TOKEN in MetaAdsExtension.java. Use app SDK configuration, never a server Graph API token.
4. Approve the extension and its dependency closure, then rebuild and install the native app. Copying files to the website does not update an installed app.

The Swift package is pinned to commit 7c3fb7856063fa1074d330616f4070f2ed10a49b. Android declares com.facebook.android:facebook-core:19.0.1. Dependencies are fetched and pinned during approval. Keep the included automatic initialization and collection flags off until the app explicitly configures and enables collection.

## JavaScript SDK

Install the published SDK in your web app:

```sh
npm install despia-native
```

Import `despia` directly from `despia-native`. The examples use SDK 1.0.28. Run native calls from browser event handlers or mounted client hooks; importing the SDK during server rendering is safe. The installed Despia V3 app must include the approved extension.

Pass the route's exact output variable as the second argument. The SDK resolves an object whose matching property contains the full native envelope: `ok`, `code`, `error` and `data`, with runtime metadata. A native `ok: false` answer does not reject the SDK promise. Check `ok` before reading `data`. A single watched value becomes `undefined` after 30 seconds without an answer. Await calls using the same output variable sequentially.

## Call from the page

Apply the user's collection choice before sending events. Encode JSON query values with `encodeURIComponent(JSON.stringify(...))`.

```javascript
import despia from 'despia-native';

const configuredReply = await despia('extension://metaads/configure', ['metaadsConfigureResult']);
const configured = configuredReply.metaadsConfigureResult;
if (!configured?.ok) throw new Error(configured?.error ?? 'No native answer');

const consentReply = await despia('extension://metaads/consent?enabled=true&advertiser_id=false&automatic_events=false', ['metaadsConsentResult']);
const consent = consentReply.metaadsConsentResult;
if (!consent?.ok) throw new Error(consent?.error ?? 'No native answer');
if (!consent.data.enabled) throw new Error('Event collection is disabled');

const activationReply = await despia('extension://metaads/activate', ['metaadsActivateResult']);
const activation = activationReply.metaadsActivateResult;
if (!activation?.ok) throw new Error(activation?.error ?? 'No native answer');

const properties = { registration_method: 'email' };
const loggedReply = await despia(
  `extension://metaads/event?name=fb_mobile_complete_registration&properties=${encodeURIComponent(JSON.stringify(properties))}`,
  ['metaadsEventResult']
);
const logged = loggedReply.metaadsEventResult;
if (!logged?.ok) throw new Error(logged?.error ?? 'No native answer');
```

| Route | Parameters | Successful data | SDK output variable |
| --- | --- | --- | --- |
| configure | None | configured, enabled. iOS checks native configuration without starting the SDK; Android initializes with automatic collection off. | `metaadsConfigureResult` |
| status | None | configured, enabled, sdk | `metaadsStatusResult` |
| consent | enabled boolean required, advertiser_id and automatic_events optional booleans | iOS: enabled. Android: configured, enabled. Optional flags default false. | `metaadsConsentResult` |
| activate | None | accepted: true. Logs app activation through Meta. | `metaadsActivateResult` |
| event | name required, optional properties JSON string, value numeric string | accepted: true | `metaadsEventResult` |
| purchase | amount numeric string and currency uppercase three-letter code required, optional properties JSON string | accepted: true | `metaadsPurchaseResult` |
| user | Optional id, omit it to clear the SDK user id | accepted: true | `metaadsUserResult` |
| flush | None | accepted: true. Requests a flush, not a delivery receipt. | `metaadsFlushResult` |

Properties are a flat object with at most 25 entries, nonempty keys up to 40 characters and string, number or boolean values. Name limits and parameter types are declared in manifest.json. Numbers must be finite. accepted means handed to the SDK, not received or attributed by Meta. Errors include not_configured, already_configured, consent_required and bad_request; Android can also return sdk_error. Unknown routes are rejected by the runtime before dispatch.

## ATT and attribution configuration

This extension never presents ATT. Use the V3 trackingconsent:// call at your chosen moment. The prepared request/status callback API is a separate runtime change and must be released before it is used. Enable the existing tracking_usage purpose description in Despia and rebuild for ATT support. Existing app-configured NSUserTrackingUsageDescription takes priority. The runtime update under review also allows a required purpose description from the approved manifest when the app configuration has none.

On iOS, configure checks that the native app ID and client token are present. It does not initialize or change the SDK. The first consent call with enabled=true starts the SDK using the approved Info.plist configuration, or reuses its matching existing instance. A different effective app ID or client token returns already_configured without changing that SDK's settings. A false choice before this extension has initialized or adopted the SDK leaves it unchanged. Explicit consent after that point changes the SDK's app-wide automatic event and advertiser ID settings. Android refuses an SDK already initialized by another integration. Its configure step can request vendor configuration even while bridge events are disabled. The first true consent call completes manual SDK initialization so Graph API responses can be processed; this is not a guarantee that configure sends no network traffic.

On iOS, advertiser_id=true enables collection only while ATT is authorized. The SDK reads ATT itself on iOS 17 and later. Enabling event collection is a separate decision from ATT. Turning this extension's collection off prevents its future bridge event calls and disables its automatic event and advertiser-id settings. It does not erase events already sent or guarantee cancellation of SDK work already in flight. The adapter's enabled state is process-local; reapply the app's stored choice after each launch. The vendor SDK can persist its own settings.

Meta's Core package includes FBAEMKit and SKAdNetwork code. Feature availability and campaign attribution depend on Meta account settings and SDK server configuration. Do not have multiple SDKs manage the same conversion values or send duplicate purchases.

SKAdNetworkItems is editable in ios.app.info_plist with custom_keys=true, and starts empty. Supply only the current network identifiers your integration requires. Apple defines this key for source apps displaying ads; adding a publisher list to an advertised app is not by itself install attribution. The advertised app's SDK handles its attribution registration and conversion logic. If your setup requires postback copies, coordinate the build-owned attribution endpoint with Despia instead of replacing another integration's endpoint. No Android SKAdNetwork list exists.

## Platform requirements

Current V3 iOS builds target iOS16.6 and later. Android module minSdk24, compileSdk36. An ATT API introduced in iOS14 does not lower the app's deployment target. Android uses the advertiser SDK and may need the base app's Advertising ID permission for identifier collection. Normal internet/network permissions are in the module manifest. Review the final merged manifest and store disclosures during approval.

[Despia V3 Extensions documentation](https://setup.despia.com/experiments/v3-extensions) and [extension skill](https://github.com/despia-legacy/extension-skill).

Vendor sources: [Meta iOS19.0.1](https://github.com/meta/facebook-ios-sdk/releases/tag/v19.0.1), [Meta Android19.0.1](https://github.com/meta/facebook-android-sdk/releases/tag/sdk-version-19.0.1), [Apple SKAdNetworkItems](https://developer.apple.com/documentation/BundleResources/Information-Property-List/SKAdNetworkItems).

## License

[Despia V3 Extension Source-Available Commercial License](LICENSE).
You may inspect the source. Using or modifying this extension requires a valid
Despia commercial license and a valid Despia project for the particular app.
The standard price is **USD 249 per application per operating system**.
iOS and Android require separate licenses. See [Despia](https://despia.com).
Third-party SDKs keep their own licenses. Experimental V3 extensions do not
include a commitment to maintenance or a V4 port.

## Source and licensing

Browse, clone or fork this source to inspect it. Public GitHub forks remain public. For private development, clone into a private workspace or private repository. Modification, building, execution and deployment require a valid Despia commercial license and a valid Despia project for the application. The standard price is USD 249 per application per operating system. Licensed users may customize the extension or use it as a reference for new functionality in that licensed app. Keep the license and applicable notices. See [LICENSE](LICENSE) for the terms.
