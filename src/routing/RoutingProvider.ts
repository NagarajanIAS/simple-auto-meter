import { haversineMeters, LatLng } from '../tracking/Geo';

export type RoutingProviderId = 'MANUAL' | 'HAVERSINE' | 'OPENROUTESERVICE';

export interface RouteRequest {
  from?: LatLng | null;
  to?: LatLng | null;
  /** Distance typed by the user (e.g. read from Google Maps). */
  manualKm?: number | null;
}

export interface RouteEstimate {
  distanceKm: number;
  /** True for straight-line based guesses; the UI labels these "approximate". */
  approximate: boolean;
  providerId: RoutingProviderId;
}

export type Result<T> = { ok: true; value: T } | { ok: false; error: Error };
export const success = <T>(value: T): Result<T> => ({ ok: true, value });
export const failure = <T>(message: string): Result<T> => ({ ok: false, error: new Error(message) });

/** Source of a trip distance for the fare estimator. */
export interface RoutingProvider {
  readonly id: RoutingProviderId;
  /** False when the provider cannot be used (e.g. no API key). */
  readonly isAvailable: boolean;
  route(request: RouteRequest): Promise<Result<RouteEstimate>>;
}

/** Default: the user types the km they read from Google Maps. */
export class ManualEntryProvider implements RoutingProvider {
  readonly id = 'MANUAL' as const;
  readonly isAvailable = true;

  async route(request: RouteRequest): Promise<Result<RouteEstimate>> {
    const km = request.manualKm;
    if (km == null) return failure('Enter a distance');
    if (km < 0 || Number.isNaN(km)) return failure('Invalid distance');
    return success({ distanceKm: km, approximate: false, providerId: this.id });
  }
}

/** Offline: straight-line distance × road factor. Always approximate. */
export class HaversineProvider implements RoutingProvider {
  readonly id = 'HAVERSINE' as const;
  readonly isAvailable = true;

  constructor(private readonly roadFactor: number) {}

  async route(request: RouteRequest): Promise<Result<RouteEstimate>> {
    if (!request.from) return failure('Start point unknown');
    if (!request.to) return failure('Destination not chosen');
    const km = (haversineMeters(request.from, request.to) / 1000) * this.roadFactor;
    return success({ distanceKm: km, approximate: true, providerId: this.id });
  }
}
