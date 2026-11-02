import * as ScreenOrientation from 'expo-screen-orientation';
import { router, useFocusEffect } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { useCallback, useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { activeFareConfig, activePreset, canShowQr } from '../data/AppSettings';
import { LiveTripStore, snapshotStore } from '../data/LiveTripStore';
import { useSettings } from '../data/SettingsRepository';
import { FareCalculator } from '../fare/FareCalculator';
import { useT } from '../i18n';
import { hasAnyLocation, hasFineLocation } from '../tracking/LocationHelper';
import { finishedTripStore, liveMeterStore, MeterService } from '../tracking/MeterService';
import { useStore } from '../util/Store';
import { Body, Button, Card, Dialog, HeaderButton, Screen, Small } from '../ui/components';
import { formatDateTime, formatDuration, formatKm, formatRupees, tariffEffectiveText } from '../ui/format';
import { FareTextStyle, GoGreen, StopRed, useColors, WaitAmber } from '../ui/theme';

export default function MeterScreen() {
  const t = useT();
  const c = useColors();
  const settings = useSettings()!;
  const live = useStore(liveMeterStore);
  const finishedId = useStore(finishedTripStore);
  const snapshot = useStore(snapshotStore);
  const [confirmStop, setConfirmStop] = useState(false);
  const [needPrecise, setNeedPrecise] = useState(false);
  // A trip interrupted by a process kill, offered for resumption while the meter is idle.
  const interrupted = live.running ? null : snapshot;

  useFocusEffect(
    useCallback(() => {
      ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP).catch(() => undefined);
      return () => {
        ScreenOrientation.unlockAsync().catch(() => undefined);
      };
    }, []),
  );

  useEffect(() => {
    if (finishedId == null) return;
    MeterService.consumeFinished();
    router.push({ pathname: '/summary/[tripId]', params: { tripId: String(finishedId) } });
    if (canShowQr(settings.driver)) router.push({ pathname: '/qr/[tripId]', params: { tripId: String(finishedId) } });
  }, [finishedId, settings.driver]);

  const startOrAsk = async (resume: boolean) => {
    if (await hasFineLocation()) {
      if (resume) MeterService.resume();
      else MeterService.start();
    } else if (await hasAnyLocation()) {
      setNeedPrecise(true);
    } else {
      router.push('/permission');
    }
  };

  const preset = activePreset(settings);
  const tariffName = live.running ? live.tariffName : (preset?.name ?? t('tariff_custom'));
  const effective = tariffEffectiveText(live.running ? live.tariffEffective : preset?.effectiveDate);
  const fare = live.fare ?? FareCalculator.calculate(activeFareConfig(settings), 0, 0, false);
  const waitStatus = live.manualWait ? t('waiting_manual') : live.autoWaiting ? t('waiting_auto') : null;
  const gpsValue = !live.running ? '—' : live.gpsOk ? t('gps_ok') : t('gps_searching');
  const gpsNote = live.running && live.accuracyM != null && Number.isFinite(live.accuracyM) ? t('gps_accuracy', live.accuracyM) : null;

  return (
    <Screen>
      <Stack.Screen
        options={{
          title: t('app_name'),
          headerTitleStyle: { fontWeight: '900' },
          headerRight: () => (
            <View style={{ flexDirection: 'row' }}>
              <HeaderButton glyph="🧮" label={t('estimate_title')} onPress={() => router.push('/estimate')} />
              <HeaderButton glyph="🗺" label={t('map_title')} onPress={() => router.push({ pathname: '/map', params: { pick: 'false' } })} />
              <HeaderButton glyph="🕘" label={t('history_title')} onPress={() => router.push('/history')} />
              <HeaderButton glyph="⚙" label={t('settings_title')} onPress={() => router.push('/settings')} />
            </View>
          ),
        }}
      />
      <Small>{effective ? t('tariff_label', tariffName, effective) : tariffName}</Small>

      <Card color={c.primaryContainer} style={{ alignItems: 'center' }}>
        <Text style={{ fontSize: 16, color: c.onPrimaryContainer }}>{t('current_fare')}</Text>
        <Text style={[FareTextStyle, { color: c.onPrimaryContainer, textAlign: 'center' }]} adjustsFontSizeToFit numberOfLines={1}>
          {formatRupees(fare.total)}
        </Text>
        {live.running && fare.isNight ? <Text style={{ color: c.onPrimaryContainer, fontSize: 16 }}>{t('night_rate_applied')}</Text> : null}
      </Card>

      <View style={{ flexDirection: 'row', gap: 12 }}>
        <Tile label={t('distance')} value={formatKm(live.distanceMeters)} />
        <Tile label={t('moving_time')} value={formatDuration(live.movingMs)} />
      </View>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <Tile label={t('waiting_time')} value={formatDuration(live.waitingMs)} note={waitStatus} />
        <Tile label={t('gps')} value={gpsValue} note={gpsNote} />
      </View>

      <Button
        title={live.manualWait ? t('wait_on') : t('wait')}
        onPress={() => MeterService.toggleWait()}
        disabled={!live.running}
        height={64}
        fontSize={22}
        color={live.manualWait ? WaitAmber : c.secondaryContainer}
        textColor={live.manualWait ? '#000000' : c.onSecondaryContainer}
      />
      <Button
        title={live.running ? t('stop') : t('start')}
        onPress={() => (live.running ? setConfirmStop(true) : startOrAsk(false))}
        height={96}
        fontSize={34}
        color={live.running ? StopRed : GoGreen}
        textColor="#FFFFFF"
      />
      <Small>{t('disclaimer_short')}</Small>

      <Dialog
        visible={confirmStop}
        title={t('stop_confirm_title')}
        onDismiss={() => setConfirmStop(false)}
        actions={
          <>
            <Button kind="text" title={t('cancel')} onPress={() => setConfirmStop(false)} />
            <Button
              title={t('stop')}
              color={StopRed}
              textColor="#FFFFFF"
              onPress={() => {
                setConfirmStop(false);
                MeterService.stop();
              }}
            />
          </>
        }
      >
        <Body>{t('stop_confirm_text')}</Body>
      </Dialog>

      <Dialog
        visible={needPrecise}
        title={t('precise_needed_title')}
        onDismiss={() => setNeedPrecise(false)}
        actions={
          <Button
            title={t('ok')}
            onPress={() => {
              setNeedPrecise(false);
              router.push('/permission');
            }}
          />
        }
      >
        <Body>{t('precise_needed_text')}</Body>
      </Dialog>

      <Dialog
        visible={interrupted != null}
        title={t('resume_title')}
        onDismiss={() => undefined}
        actions={
          <>
            <Button kind="outlined" title={t('discard')} onPress={() => LiveTripStore.clear()} />
            <Button title={t('resume')} onPress={() => startOrAsk(true)} />
          </>
        }
      >
        {interrupted ? <Body>{t('resume_text', formatDateTime(interrupted.startEpochMs), formatKm(interrupted.distanceMeters))}</Body> : null}
      </Dialog>
    </Screen>
  );
}

function Tile({ label, value, note }: { label: string; value: string; note?: string | null }) {
  const c = useColors();
  return (
    <Card style={{ flex: 1, padding: 12 }}>
      <Text style={{ fontSize: 16, color: c.onSurfaceVariant }}>{label}</Text>
      <Text style={{ fontSize: 30, fontWeight: 'bold', color: c.onBackground }} adjustsFontSizeToFit numberOfLines={1}>
        {value}
      </Text>
      {note ? <Text style={{ fontSize: 13, color: c.secondary }}>{note}</Text> : null}
    </Card>
  );
}
