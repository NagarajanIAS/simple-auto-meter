/**
 * Leaflet page shown in a WebView, replacing osmdroid. Tiles come from the user-configurable
 * "{z}/{x}/{y}" template; the © OpenStreetMap attribution is always shown.
 *
 * Messages in:  window.updateMap({ position, track, picked })  (called via injectJavaScript)
 * Messages out: { type: 'tap', lat, lng }  (pick mode only)
 */
export function mapHtml(tileUrl: string, pickMode: boolean, positionLabel: string): string {
  // JSON in a <script>: escape '<' so a template can never close the tag.
  const js = (v: unknown) => JSON.stringify(v).replace(/</g, '\\u003c');
  return `<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
  integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin="">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
  integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
<style>html,body,#map{height:100%;margin:0;padding:0}
.me{width:18px;height:18px;border-radius:50%;background:#1E88E5;border:3px solid #fff;box-shadow:0 0 4px rgba(0,0,0,.5)}</style>
</head><body><div id="map"></div>
<script>
var map = L.map('map', { zoomControl: true }).setView([13.0827, 80.2707], 16); // Chennai until we have a fix
L.tileLayer(${js(tileUrl)}, { maxZoom: 19, attribution: '© OpenStreetMap contributors' }).addTo(map);
var track = L.polyline([], { color: '#E69F00', weight: 6 }).addTo(map);
var me = null, pick = null, centered = false;
var meIcon = L.divIcon({ className: '', html: '<div class="me"></div>', iconSize: [24, 24], iconAnchor: [12, 12] });
function send(m) { if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(m)); }
window.updateMap = function (s) {
  track.setLatLngs((s.track || []).map(function (p) { return [p.lat, p.lng]; }));
  if (s.position) {
    var ll = [s.position.lat, s.position.lng];
    if (!me) me = L.marker(ll, { icon: meIcon, title: ${js(positionLabel)} }).addTo(map); else me.setLatLng(ll);
    if (!centered) { map.setView(ll); centered = true; }
  }
  if (s.picked) {
    var pl = [s.picked.lat, s.picked.lng];
    if (!pick) pick = L.marker(pl).addTo(map); else pick.setLatLng(pl);
  }
};
if (${pickMode ? 'true' : 'false'}) map.on('click', function (e) { send({ type: 'tap', lat: e.latlng.lat, lng: e.latlng.lng }); });
send({ type: 'ready' });
</script></body></html>`;
}
