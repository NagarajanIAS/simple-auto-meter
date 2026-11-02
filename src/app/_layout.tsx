import { StatusBar } from 'expo-status-bar';
import { Stack } from 'expo-router/stack';
import { useEffect } from 'react';
import { useColorScheme, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { LiveTripStore } from '../data/LiveTripStore';
import { loadSettings, useSettings } from '../data/SettingsRepository';
import { useColors } from '../ui/theme';

export default function RootLayout() {
  const settings = useSettings();
  const c = useColors();
  const dark = useColorScheme() === 'dark';

  useEffect(() => {
    loadSettings();
    LiveTripStore.load();
  }, []);

  return (
    <SafeAreaProvider>
      <StatusBar style={dark ? 'light' : 'dark'} />
      {settings == null ? (
        <View style={{ flex: 1, backgroundColor: c.background }} />
      ) : (
        <Stack
          screenOptions={{
            headerStyle: { backgroundColor: c.background },
            headerTintColor: c.onBackground,
            headerTitleStyle: { fontWeight: 'bold' },
            contentStyle: { backgroundColor: c.background },
          }}
        />
      )}
    </SafeAreaProvider>
  );
}
