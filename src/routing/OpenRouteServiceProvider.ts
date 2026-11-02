import { failure, HaversineProvider, ManualEntryProvider, Result, RouteEstimate, RouteRequest, RoutingProvider, RoutingProviderId, success } from './RoutingProvider';

const ENDPOINT = 'https://api.openrouteservice.org/v2/directions/driving-car';
const TIMEOUT_MS = 15_000;

/**
 * Road distance from openrouteservice.org using the user's own free API key.
 * This is the only part of the app that sends anything over the network, and only the
 * two coordinates of the requested route are sent.
 */
export class OpenRouteServiceProvider implements RoutingProvider {
  readonly id = 'OPENROUTESERVICE' as const;

  constructor(private readonly apiKey: string) {}

  get isAvailable(): boolean {
    return this.apiKey.trim().length > 0;
  }

  async route(request: RouteRequest): Promise<Result<RouteEstimate>> {
    if (!this.isAvailable) return failure('No OpenRouteService API key');
    const { from, to } = request;
    if (!from) return failure('Start point unknown');
    if (!to) return failure('Destination not chosen');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const response = await fetch(ENDPOINT, {
        method: 'POST',
        // Refuse redirects where the fetch implementation supports it, so the key only goes to ENDPOINT.
        redirect: 'error',
        signal: controller.signal,
        headers: {
          Authorization: this.apiKey.trim(),
          'Content-Type': 'application/json; charset=utf-8',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          coordinates: [
            [from.lng, from.lat],
            [to.lng, to.lat],
          ],
          units: 'm',
        }),
      });
      if (!response.ok) return failure(`OpenRouteService HTTP ${response.status}`);
      const meters = parseOrsDistanceMeters(await response.text());
      return success({ distanceKm: meters / 1000, approximate: false, providerId: this.id });
    } catch (e) {
      return failure(e instanceof Error ? e.message : String(e));
    } finally {
      clearTimeout(timer);
    }
  }
}

/** Reads routes[0].summary.distance (metres) from a directions response. */
export function parseOrsDistanceMeters(json: string): number {
  const distance = JSON.parse(json)?.routes?.[0]?.summary?.distance;
  if (typeof distance !== 'number') throw new Error('Unexpected OpenRouteService response');
  return distance;
}

export const RoutingProviders = {
  create(id: RoutingProviderId, roadFactor: number, orsApiKey: string): RoutingProvider {
    switch (id) {
      case 'MANUAL':
        return new ManualEntryProvider();
      case 'HAVERSINE':
        return new HaversineProvider(roadFactor);
      case 'OPENROUTESERVICE':
        return new OpenRouteServiceProvider(orsApiKey);
    }
  },

  /** Provider used when the user picks a destination on the map. */
  forMapPick(id: RoutingProviderId, roadFactor: number, orsApiKey: string): RoutingProvider {
    const ors = new OpenRouteServiceProvider(orsApiKey);
    return id === 'OPENROUTESERVICE' && ors.isAvailable ? ors : new HaversineProvider(roadFactor);
  },
};
