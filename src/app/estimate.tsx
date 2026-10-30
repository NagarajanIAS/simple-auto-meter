import { router } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { activeFareConfig, activePreset } from '../data/AppSettings';
import { useSettings } from '../data/SettingsRepository';
import { MAX_KM } from '../fare/FareConfig';
import { estimateFare, FareRange } from '../fare/FareEstimator';
import { useT } from '../i18n';
import { directions, showLocation } from '../routing/MapsLinks';
import { RoutingProviders } from '../routing/OpenRouteServiceProvider';
import { pickedDestinationStore } from '../routing/pickedDestination';
import { RouteEstimate } from '../routing/RoutingProvider';
import { LatLng } from '../tracking/Geo';
import { currentLocation, hasAnyLocation } from '../tracking/LocationHelper';
import { useStore } from '../util/Store';
import { Button, Card, ErrorText, Field, Screen, SectionTitle, Small } from '../ui/components';
import { formatRupees } from '../ui/format';
import { useColors } from '../ui/theme';

type RouteState = { kind: 'idle' } | { kind: 'loading' } | { kind: 'done'; estimate: RouteEstimate } | { kind: 'failed'; message: string };

export default function EstimatorScreen() {
  const t = useT();
  const c = useColors();
  const settings = useSettings()!;
  const picked = useStore(pickedDestinationStore);
  const [destination, setDestination] = useState('');
  const [kmText, setKmText] = useState('');
  const [waitText, setWaitText] = useState('0');
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState(false);
  const [route, setRoute] = useState<RouteState>({ kind: 'idle' });

  const routeTo = async (from: LatLng | null, to: LatLng) => {
    setRoute({ kind: 'loading' });
    const provider = RoutingProviders.forMapPick(settings.routingProvider, settings.roadFactor, settings.orsApiKey);
    const result = await provider.route({ from, to });
    if (result.ok) {
      setRoute({ kind: 'done', estimate: result.value });
      setKmText(result.value.distanceKm.toFixed(1));
    } else {
      setRoute({ kind: 'failed', message: result.error.message });
    }
  };

  useEffect(() => {
    if (!picked) return;
    pickedDestinationStore.set(null);
    (async () => routeTo(picked.from ?? (await currentLocation()), picked.to))();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [picked]);

  const openDirections = () => {
    if (destination.trim()) directions(destination);
  };

  const showMyLocation = async () => {
    if (!(await hasAnyLocation())) {
      router.push('/permission');
      return;
    }
    setLocating(true);
    setLocationError(false);
    const here = await currentLocation();
    setLocating(false);
    if (here) showLocation(here);
    else setLocationError(true);
  };

  const km = Number(kmText.replace(',', '.'));
  const parsedWait = Number(waitText);
  const wait = waitText.trim() !== '' && Number.isFinite(parsedWait) ? parsedWait : 0;
  const valid = kmText.trim() !== '' && Number.isFinite(km) && km >= 0 && km <= MAX_KM && Number.isFinite(wait) && wait >= 0 && wait <= 1440;
  const estimate = valid ? estimateFare(activeFareConfig(settings), km, wait) : null;

  let routeNote: string | null = null;
  if (route.kind === 'done') {
    if (route.estimate.approximate) routeNote = t('route_approx', route.estimate.distanceKm, settings.roadFactor);
    else if (route.estimate.providerId === 'OPENROUTESERVICE') routeNote = t('route_ors', route.estimate.distanceKm);
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: t('estimate_title') }} />
      <SectionTitle>{t('plan_trip')}</SectionTitle>
      <Field label={t('destination_hint')} value={destination} onChangeText={setDestination} returnKeyType="go" onSubmitEditing={openDirections} />
      <Button title={'➤  ' + t('open_directions')} onPress={openDirections} disabled={!destination.trim()} />
      <Button kind="outlined" title={'◎  ' + t('show_my_location')} onPress={showMyLocation} loading={locating} />
      {locationError ? <ErrorText>{t('location_unavailable')}</ErrorText> : null}
      <Small>{t('estimate_hint')}</Small>

      <SectionTitle>{t('fare_estimate')}</SectionTitle>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Field style={{ flex: 1 }} label={t('distance_km')} value={kmText} onChangeText={setKmText} keyboardType="decimal-pad" />
        <Field style={{ flex: 1 }} label={t('waiting_minutes')} value={waitText} onChangeText={setWaitText} keyboardType="number-pad" />
      </View>
      <Button kind="outlined" title={'📍  ' + t('pick_on_map')} onPress={() => router.push({ pathname: '/map', params: { pick: 'true' } })} />
      {route.kind === 'loading' ? <ActivityIndicator color={c.onBackground} /> : null}
      {routeNote ? <Text style={{ color: c.secondary, fontWeight: 'bold', fontSize: 16 }}>{routeNote}</Text> : null}
      {route.kind === 'failed' ? <ErrorText>{t('route_failed', route.message)}</ErrorText> : null}

      {estimate ? (
        <Card color={c.primaryContainer}>
          <RangeRow label={t('day')} range={estimate.day} />
          <RangeRow label={t('night')} range={estimate.night} />
          <Text style={{ fontSize: 13, color: c.onPrimaryContainer }}>{t('estimate_range_note')}</Text>
        </Card>
      ) : null}
      <Small>{(activePreset(settings)?.name ?? t('tariff_custom')) + ' · ' + t('disclaimer_short')}</Small>
    </Screen>
  );
}

function RangeRow({ label, range }: { label: string; range: FareRange }) {
  const c = useColors();
  const text = range.low.equals(range.high) ? formatRupees(range.low) : formatRupees(range.low) + ' – ' + formatRupees(range.high);
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 4 }}>
      <Text style={{ flex: 1, fontSize: 22, color: c.onPrimaryContainer }}>{label}</Text>
      <Text style={{ fontSize: 28, fontWeight: 'bold', color: c.onPrimaryContainer }}>{text}</Text>
    </View>
  );
}
