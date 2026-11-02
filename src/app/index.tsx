import { router } from 'expo-router';
import { useEffect } from 'react';
import { useSettings } from '../data/SettingsRepository';

/** Start destination: the disclaimer until it has been accepted, then the meter. */
export default function Index() {
  const accepted = useSettings()?.disclaimerAccepted;
  useEffect(() => {
    if (accepted != null) router.replace(accepted ? '/meter' : '/disclaimer');
  }, [accepted]);
  return null;
}
