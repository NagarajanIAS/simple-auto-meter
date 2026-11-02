import Constants from 'expo-constants';
import { router, useLocalSearchParams } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { ComponentType, Ref, useEffect, useMemo, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { WebView, WebViewMessageEvent, WebViewProps } from 'react-native-webview';
import { DEFAULT_TILE_URL } from '../data/AppSettings';
import { useSettings } from '../data/SettingsRepository';
import { useT } from '../i18n';
import { pickedDestinationStore } from '../routing/pickedDestination';
import { LatLng } from '../tracking/Geo';
import { currentLocation } from '../tracking/LocationHelper';
import { liveMeterStore } from '../tracking/MeterService';
import { useStore } from '../util/Store';
import { Button } from '../ui/components';
import { mapHtml } from '../ui/mapHtml';
import { useColors } from '../ui/theme';

// Identifies the app to the tile server, as the OSM tile usage policy asks.
const USER_AGENT = `com.nammameter.app/${Constants.expoConfig?.version ?? '0'}`;

// The library types WebView as a function component without its imperative ref.
interface WebViewHandle {
  injectJavaScript: (script: string) => void;
}
const MapWebView = WebView as unknown as ComponentType<WebViewProps & { ref?: Ref<WebViewHandle> }>;

export default function MapScreen() {
  const t = useT();
  const c = useColors();
  const pickMode = useLocalSearchParams<{ pick?: string }>().pick === 'true';
  const settings = useSettings()!;
  const live = useStore(liveMeterStore);
  const [here, setHere] = useState<LatLng | null>(null);
  const [picked, setPicked] = useState<LatLng | null>(null);
  const [ready, setReady] = useState(false);
  const web = useRef<WebViewHandle>(null);

  useEffect(() => {
    currentLocation().then(setHere);
  }, []);

  const tileUrl = settings.tileUrl.trim() || DEFAULT_TILE_URL;
  // Rebuilt only when the tile source changes; live data is pushed in below.
  const html = useMemo(() => mapHtml(tileUrl, pickMode, t('you_are_here')), [tileUrl, pickMode, t]);
  const position = live.position ?? here;

  useEffect(() => {
    if (!ready) return;
    const state = { position, track: live.track, picked };
    web.current?.injectJavaScript(`window.updateMap(${JSON.stringify(state)}); true;`);
  }, [ready, position, live.track, picked]);

  const onMessage = (e: WebViewMessageEvent) => {
    try {
      const m = JSON.parse(e.nativeEvent.data) as { type: string; lat?: number; lng?: number };
      if (m.type === 'ready') setReady(true);
      if (m.type === 'tap' && pickMode && typeof m.lat === 'number' && typeof m.lng === 'number') setPicked({ lat: m.lat, lng: m.lng });
    } catch {
      // Ignore malformed messages.
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: c.background }}>
      <Stack.Screen options={{ title: pickMode ? t('pick_destination') : t('map_title') }} />
      <MapWebView
        ref={web}
        source={{ html }}
        originWhitelist={['*']}
        userAgent={USER_AGENT}
        onMessage={onMessage}
        onLoadStart={() => setReady(false)}
        javaScriptEnabled
        setSupportMultipleWindows={false}
        style={{ flex: 1 }}
      />
      {pickMode ? (
        <View style={{ position: 'absolute', left: 16, right: 16, bottom: 16, gap: 8 }}>
          <View style={{ backgroundColor: c.background, opacity: 0.9, borderRadius: 12, padding: 12 }}>
            <Text style={{ fontSize: 16, color: c.onBackground }}>{t('pick_hint')}</Text>
          </View>
          <Button
            title={t('use_destination')}
            disabled={!picked}
            onPress={() => {
              if (!picked) return;
              pickedDestinationStore.set({ to: picked, from: position });
              router.back();
            }}
          />
        </View>
      ) : null}
    </View>
  );
}
