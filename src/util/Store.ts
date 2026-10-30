import { useSyncExternalStore } from 'react';

/** Tiny observable value, the stand-in for Kotlin's StateFlow. */
export class Store<T> {
  private listeners = new Set<() => void>();

  constructor(private value: T) {}

  get = (): T => this.value;

  set(value: T): void {
    if (Object.is(value, this.value)) return;
    this.value = value;
    this.listeners.forEach((l) => l());
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };
}

export function useStore<T>(store: Store<T>): T {
  return useSyncExternalStore(store.subscribe, store.get, store.get);
}
