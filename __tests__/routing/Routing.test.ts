import { directionsUrl, searchUrl } from '../../src/routing/MapsLinks';
import { OpenRouteServiceProvider, parseOrsDistanceMeters, RoutingProviders } from '../../src/routing/OpenRouteServiceProvider';
import { HaversineProvider, ManualEntryProvider } from '../../src/routing/RoutingProvider';
import { isValidTileUrl } from '../../src/ui/tileUrl';

const a = { lat: 13.0827, lng: 80.2707 };
const b = { lat: 13.0827 + 1000 / 111_195, lng: 80.2707 };

describe('routing providers', () => {
  test('haversine provider applies road factor and is approximate', async () => {
    const r = await new HaversineProvider(1.35).route({ from: a, to: b });
    if (!r.ok) throw r.error;
    expect(Math.abs(r.value.distanceKm - 1.35)).toBeLessThan(0.01);
    expect(r.value.approximate).toBe(true);
  });

  test('manual provider returns typed km', async () => {
    const r = await new ManualEntryProvider().route({ manualKm: 4.2 });
    if (!r.ok) throw r.error;
    expect(r.value.distanceKm).toBe(4.2);
    expect(r.value.approximate).toBe(false);
    expect((await new ManualEntryProvider().route({})).ok).toBe(false);
  });

  test('ors is unavailable without key and map pick falls back to haversine', async () => {
    expect(new OpenRouteServiceProvider('').isAvailable).toBe(false);
    expect((await new OpenRouteServiceProvider('').route({ from: a, to: b })).ok).toBe(false);
    expect(RoutingProviders.forMapPick('OPENROUTESERVICE', 1.35, '').id).toBe('HAVERSINE');
  });

  test('parses ors response', () => {
    expect(parseOrsDistanceMeters('{"routes":[{"summary":{"distance":5234.7,"duration":900.1}}]}')).toBe(5234.7);
  });

  test('google maps urls', () => {
    expect(searchUrl(a)).toBe('https://www.google.com/maps/search/?api=1&query=13.082700,80.270700');
    expect(directionsUrl('T. Nagar, Chennai')).toBe('https://www.google.com/maps/dir/?api=1&destination=T.+Nagar%2C+Chennai&travelmode=driving');
  });
});

test('only https tile templates with placeholders are accepted', () => {
  expect(isValidTileUrl('https://tile.openstreetmap.org/{z}/{x}/{y}.png')).toBe(true);
  expect(isValidTileUrl('https://tiles.example.com/v1/{z}/{x}/{y}@2x.png?key=abc')).toBe(true);
  expect(isValidTileUrl('http://tile.openstreetmap.org/{z}/{x}/{y}.png')).toBe(false);
  expect(isValidTileUrl('https://tile.openstreetmap.org/{z}/{x}.png')).toBe(false);
  expect(isValidTileUrl('https://tile.openstreetmap.org/{z}/{x}/{y} .png')).toBe(false);
  expect(isValidTileUrl('not a url {z}{x}{y}')).toBe(false);
  expect(isValidTileUrl('file:///{z}/{x}/{y}')).toBe(false);
});
