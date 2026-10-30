import { Linking, Platform } from 'react-native';
import { LatLng } from '../tracking/Geo';
import { formEncode } from '../util/urlEncode';

/** Hands navigation off to Google Maps (or the browser) — no Maps SDK or server needed. */
const coord = (v: number) => v.toFixed(6);

export function searchUrl(at: LatLng): string {
  return `https://www.google.com/maps/search/?api=1&query=${coord(at.lat)},${coord(at.lng)}`;
}

export function directionsUrl(destination: string): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${formEncode(destination.trim())}&travelmode=driving`;
}

export function showLocation(at: LatLng): Promise<void> {
  const geo = `geo:${coord(at.lat)},${coord(at.lng)}?q=${coord(at.lat)},${coord(at.lng)}`;
  return open(Platform.OS === 'android' ? geo : searchUrl(at), searchUrl(at));
}

export function directions(destination: string): Promise<void> {
  const url = directionsUrl(destination);
  return open(url, url);
}

/** Tries a maps app first, then the browser. */
async function open(primary: string, fallback: string): Promise<void> {
  try {
    await Linking.openURL(primary);
  } catch {
    try {
      await Linking.openURL(fallback);
    } catch {
      // Nothing can open a URL; ignore.
    }
  }
}
