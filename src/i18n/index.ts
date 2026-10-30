import { getLocales } from 'expo-localization';
import { settingsStore, useSettings } from '../data/SettingsRepository';
import en from './en.json';
import { format } from './format';
import ta from './ta.json';

// Generated from the Android app's res/values*/strings.xml; Tamil falls back to English per key.
export type StringKey = keyof typeof en;
const tables: Record<'en' | 'ta', Partial<Record<StringKey, string>>> = { en, ta };

export function currentLanguage(): 'en' | 'ta' {
  const chosen = settingsStore.get()?.language;
  if (chosen === 'en' || chosen === 'ta') return chosen;
  return getLocales()[0]?.languageCode === 'ta' ? 'ta' : 'en';
}

/** BCP 47 tag for Intl date formatting. */
export const currentLocaleTag = () => (currentLanguage() === 'ta' ? 'ta-IN' : 'en-IN');

export function t(key: StringKey, ...args: unknown[]): string {
  const template = tables[currentLanguage()][key] ?? en[key];
  return args.length > 0 ? format(template, args) : template;
}

/** Plural strings come in _one/_other pairs; Tamil and English share that rule. */
export function plural(base: 'earnings_trips', count: number): string {
  return t(count === 1 ? `${base}_one` : `${base}_other`, count);
}

/** Re-renders the calling component when the language setting changes. */
export function useT(): typeof t {
  useSettings();
  return t;
}
