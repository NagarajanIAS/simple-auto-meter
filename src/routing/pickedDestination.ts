import { LatLng } from '../tracking/Geo';
import { Store } from '../util/Store';

/** Hands a destination picked on the map back to the estimator. */
export interface PickedDestination {
  to: LatLng;
  /** Position the map already had when the destination was picked, if any. */
  from: LatLng | null;
}

export const pickedDestinationStore = new Store<PickedDestination | null>(null);
