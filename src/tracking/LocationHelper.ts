import * as Location from 'expo-location';
import { PermissionsAndroid, Platform } from 'react-native';
import { LatLng } from './Geo';

const isPrecise = (p: Location.LocationPermissionResponse) =>
  p.granted && (Platform.OS === 'android' ? p.android?.accuracy === 'fine' : p.ios?.accuracy !== 'reduced');

export async function hasFineLocation(): Promise<boolean> {
  return isPrecise(await Location.getForegroundPermissionsAsync());
}

export async function hasAnyLocation(): Promise<boolean> {
  return (await Location.getForegroundPermissionsAsync()).granted;
}

/**
 * Asks for the permissions shown in the in-app disclosure: precise foreground location and,
 * on Android 13+, notifications. Never background location. Returns whether precise location is granted.
 */
export async function requestMeterPermissions(): Promise<boolean> {
  const location = await Location.requestForegroundPermissionsAsync();
  if (Platform.OS === 'android' && Platform.Version >= 33) {
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS).catch(() => undefined);
  }
  return isPrecise(location);
}

/** One-shot current position, or null without permission / fix. */
export async function currentLocation(): Promise<LatLng | null> {
  if (!(await hasAnyLocation())) return null;
  let loc: Location.LocationObject | null = null;
  try {
    loc = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
  } catch {
    loc = null;
  }
  if (!loc) {
    try {
      loc = await Location.getLastKnownPositionAsync();
    } catch {
      loc = null;
    }
  }
  return loc ? { lat: loc.coords.latitude, lng: loc.coords.longitude } : null;
}
