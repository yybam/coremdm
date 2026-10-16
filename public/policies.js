// Settings shared by the device console (manage.html) and the organizations page (org.html).
// Keys must match what the Android app reads from devices/{id}.policies.

export const APP_POLICIES = [
  { key:'installAppsBlocked',    label:'Block App Installation',       sub:'Prevents Play Store installs' },
  { key:'uninstallAppsBlocked',  label:'Block App Uninstallation',     sub:'Prevents removing installed apps' },
  { key:'unknownSourcesBlocked', label:'Block Sideloading',            sub:'Blocks unknown-source APK installs' },
  { key:'playStoreHidden',       label:'Hide Play Store',              sub:'Hides Play Store from launcher' },
  { key:'browsersHidden',        label:'Hide Browsers',                sub:'Hides Chrome and all installed browsers' },
  { key:'cameraDisabled',        label:'Disable Camera',               sub:'Blocks all camera access' },
  { key:'screenCaptureDisabled', label:'Disable Screen Capture',       sub:'Blocks screenshots and screen recording' },
  { key:'installerAllowlistOn',  label:'Only chosen apps can install apps', sub:'Apps installed by any other app (stores, file managers, browsers) are removed straight away. Choose the allowed apps below.' },
];
export const SYS_POLICIES = [
  { key:'remoteOnly',          label:'Remote-only mode',                sub:'Locks the CoreMDM app on the phone — settings can only be changed from this website.' },
  { key:'safeBootBlocked',     label:'Prevent Safe Boot',               sub:'Blocks booting into safe mode' },
  { key:'factoryResetBlocked', label:'Prevent Factory Reset',           sub:'Blocks factory reset from settings' },
  { key:'debuggingBlocked',    label:'Disable Developer Options & ADB', sub:'Prevents USB debugging access' },
  { key:'addUserBlocked',      label:'Prevent Multi-User Profiles',     sub:'Blocks adding new user accounts' },
  { key:'userSwitchBlocked',   label:'Block User Switching',            sub:'Prevents switching between users' },
  { key:'statusBarDisabled',   label:'Lock Status Bar',                 sub:'Disables pull-down notification bar' },
];
export const HW_POLICIES = [
  { key:'wifiConfigBlocked',      label:'Lock Wi-Fi Configuration',      sub:'Prevents changing Wi-Fi settings' },
  { key:'mobileNetworksBlocked',  label:'Lock Cellular Data Settings',   sub:'Prevents mobile network changes' },
  { key:'bluetoothConfigBlocked', label:'Lock Bluetooth Settings',       sub:'Prevents Bluetooth configuration' },
  { key:'vpnBlocked',             label:'Block VPN Configuration',       sub:'Prevents adding or changing VPNs' },
  { key:'networkResetBlocked',    label:'Block Network Reset',           sub:'Prevents resetting all network settings' },
  { key:'usbTransferBlocked',     label:'Block USB File Transfer',       sub:'Blocks MTP/USB storage access (API 28+)' },
  { key:'bluetoothDisabled',      label:'Disable Bluetooth Completely',  sub:'Disables all Bluetooth radio' },
  { key:'outgoingCallsBlocked',   label:'Block Outgoing Calls',          sub:'Prevents all outgoing phone calls' },
  { key:'physicalMediaBlocked',   label:'Block Physical Media (SD Card)',sub:'Blocks SD card mounting' },
  { key:'mdmUninstallProtected',  label:'Protect MDM from Uninstall',   sub:'Prevents CORE MDM app removal' },
];
// Quick blocks shown at the top of the Apps tab
export const BLOCK_POLICIES = [
  { key:'whatsappBlocked',    label:'Block WhatsApp',     sub:'Pauses WhatsApp and WhatsApp Business, and blocks whatsapp.com' },
  { key:'socialMediaBlocked', label:'Block social media', sub:'Instagram, Facebook, TikTok, Snapchat, X, Reddit, YouTube, Telegram, Discord and more — apps paused and sites blocked' },
  { key:'aiChatsBlocked',     label:'Block AI chats',     sub:'ChatGPT, Claude, Gemini, Copilot, Perplexity, Grok, Character.AI and more — apps paused and sites blocked' },
  { key:'webviewDisabled',    label:'Block all in-app browsers', sub:'Stops web pages inside every app',
    warn:'Android can\'t allow web pages for only some apps this way — it goes off everywhere, and some apps may show blank screens. For a per-app choice use the "Web in apps" tab instead.' },
];
// Restrictions added to the System tab
export const CONTROL_POLICIES = [
  { key:'appsControlBlocked', label:'Lock app settings',  sub:"Stops uninstalling, force-stopping, or clearing an app's data from Settings" },
  { key:'allCallsBlocked',    label:'Block all calls',    sub:'No calls can be made and the dialler is hidden. Emergency calls always still work.' },
  { key:'smsBlocked',         label:'Block SMS and MMS',  sub:'No text messages can be sent and messaging apps are paused. Incoming texts still arrive — Android gives no way to stop those.' },
  { key:'hotspotBlocked',     label:'Block hotspot',      sub:'Stops sharing the phone’s internet with other devices' },
  { key:'emailBlocked',       label:'Block email apps',   sub:'Hides and pauses Gmail, Outlook and other email apps' },
];
// On/off settings that live on the Internet tab rather than in a list above.
export const NETWORK_POLICIES = [
  { key:'filterRunning', label:'Content filter (DNS)',                 sub:'Blocks ads, trackers and chosen site groups on the whole phone' },
  { key:'webviewGuard',  label:'Only allow web pages in chosen apps',  sub:'Web pages inside any other app are closed' },
];
// Sections inside apps that the phone's screen guard can block. Ids must match
// ContentRules.SECTIONS in the Android app.
export const SECTIONS = [
  { id:'whatsappUpdates',  app:'WhatsApp',  label:'Updates tab (Status + Channels)', description:'The whole Updates tab at once — chats and calls keep working' },
  { id:'whatsappStatus',   app:'WhatsApp',  label:'Status',            description:'The Status (stories) tab — chats and calls keep working' },
  { id:'whatsappChannels', app:'WhatsApp',  label:'Channels',          description:'The Channels / Updates feed — chats and calls keep working' },
  { id:'instagramReels',   app:'Instagram', label:'Reels',             description:'The Reels video feed — messages and posts keep working' },
  { id:'instagramStories', app:'Instagram', label:'Stories',           description:'Stories at the top of the feed and in full screen' },
  { id:'instagramExplore', app:'Instagram', label:'Explore',           description:'The Explore / search discovery feed' },
  { id:'youtubeShorts',    app:'YouTube',   label:'Shorts',            description:'The Shorts feed — normal videos keep working' },
  { id:'facebookReels',    app:'Facebook',  label:'Reels & Stories',   description:"Facebook's Reels feed and Stories viewer" },
  { id:'snapchatStories',  app:'Snapchat',  label:'Stories & Spotlight', description:'The Stories and Spotlight feeds — chats keep working' },
  { id:'tiktokFeed',       app:'TikTok',    label:'For You feed',      description:'The endless video feed — messages keep working' },
];

// Search across every setting, whichever tab it lives on. `words` carries what people actually
// type — old Android constant names and plain synonyms.
export const SEARCH_KEYWORDS = {
  whatsappBlocked:'whatsapp chat messaging', socialMediaBlocked:'instagram facebook tiktok snapchat twitter x youtube telegram discord reddit social',
  aiChatsBlocked:'chatgpt claude gemini copilot perplexity grok bard ai chatbot', webviewDisabled:'webview web browser in-app internet',
  remoteOnly:'remote website only lock', appsControlBlocked:'uninstall force stop clear data cache apps control',
  allCallsBlocked:'call phone dialler dialer telephone', smsBlocked:'sms mms text message texting',
  hotspotBlocked:'hotspot tether tethering share internet', installAppsBlocked:'install play store DISALLOW_INSTALL_APPS',
  uninstallAppsBlocked:'uninstall remove delete', unknownSourcesBlocked:'sideload apk unknown sources',
  playStoreHidden:'play store google hide', browsersHidden:'chrome firefox browser hide',
  safeBootBlocked:'safe boot recovery', factoryResetBlocked:'factory reset wipe erase',
  debuggingBlocked:'developer adb usb debugging', addUserBlocked:'user profile multi account',
  userSwitchBlocked:'switch user account', screenCaptureDisabled:'screenshot screen record capture',
  cameraDisabled:'camera photo video', statusBarDisabled:'status bar notification shade quick settings',
  wifiConfigBlocked:'wifi wi-fi network', mobileNetworksBlocked:'mobile data cellular apn carrier sim',
  bluetoothConfigBlocked:'bluetooth pair pairing', vpnBlocked:'vpn proxy tunnel',
  networkResetBlocked:'network reset', usbTransferBlocked:'usb file transfer mtp cable',
  bluetoothDisabled:'bluetooth off disable', outgoingCallsBlocked:'call outgoing phone dial',
  physicalMediaBlocked:'sd card storage usb stick media', mdmUninstallProtected:'protect uninstall coremdm mdm',
  webviewGuard:'webview web pages per-app allow list browser',
  installerAllowlistOn:'installer allow list store aurora file manager sideload install only chosen',
  emailBlocked:'email gmail outlook mail inbox',
};

// ── Organization rules ────────────────────────────────────────
// organizations/{orgId}/public/profile.rules = { key: { mode: 'locked' | 'default', value: bool } }
// A key that isn't there is "open": each phone's own admin decides.
// App sections use the key `section:<id>`. A phone's admin turning off a section the org blocks
// by default is recorded in policies.sectionsAllowed (absence from blockedSections can't say that).
// The Android app applies exactly the same rules (OrgRules.kt) — keep the two in step.

export const sectionKey = id => `section:${id}`;

/** Every on/off setting an organization can lock or default, grouped for display. */
export const ORG_SETTING_GROUPS = [
  { title:'Quick blocks',               items: BLOCK_POLICIES },
  { title:'Installing and removing apps', items: APP_POLICIES },
  { title:'Keep these rules in place',  items: [...CONTROL_POLICIES, ...SYS_POLICIES] },
  { title:'Hardware',                   items: HW_POLICIES },
  { title:'Internet',                   items: NETWORK_POLICIES },
  { title:'Parts of apps',              items: SECTIONS.map(s => ({ key: sectionKey(s.id), label:`${s.app} — ${s.label}`, sub: s.description })) },
];

/** The org's rule for a setting, or null when the setting is open. */
export function orgRule(rules, key) {
  const r = rules && rules[key];
  return r && (r.mode === 'locked' || r.mode === 'default') ? r : null;
}

/** What a phone actually gets for an on/off setting. */
export function effectivePolicy(policies, rules, key) {
  const r = orgRule(rules, key);
  if (r?.mode === 'locked') return r.value === true;
  if (policies && key in policies) return policies[key] === true;
  if (r?.mode === 'default') return r.value === true;
  return false;
}

/** Whether a phone actually has an app section blocked. */
export function effectiveSection(policies, rules, id) {
  const r = orgRule(rules, sectionKey(id));
  if (r?.mode === 'locked') return r.value === true;
  const blocked = Array.isArray(policies?.blockedSections) && policies.blockedSections.includes(id);
  if (blocked) return true;
  const allowed = Array.isArray(policies?.sectionsAllowed) && policies.sectionsAllowed.includes(id);
  return r?.mode === 'default' && r.value === true && !allowed;
}

// The content filter's built-in site groups. Ids must match the app's blocklist assets
// (LocalSettingsSync.FILTER_GROUPS). policies.filterGroupsOff lists the ones switched off.
export const FILTER_GROUPS = [
  { id:'ads',                    label:'Ads' },
  { id:'analytics',              label:'Analytics & tracking' },
  { id:'social',                 label:'Social media' },
  { id:'video',                  label:'Video (YouTube)' },
  { id:'gifs-images',            label:'GIFs & images' },
  { id:'spotify-music',          label:'Spotify & music images' },
  { id:'google-apple-microsoft', label:'Google, Apple & Microsoft pages' },
  { id:'app-help-pages',         label:'Help & about pages inside apps' },
  { id:'cloud-infra',            label:'Cloud & CDN hosts' },
  { id:'other-sites',            label:'Other sites' },
];
