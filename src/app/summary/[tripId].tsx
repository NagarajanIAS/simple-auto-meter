import { router, useLocalSearchParams } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { useState } from 'react';
import { Share, Text } from 'react-native';
import { canShowQr } from '../../data/AppSettings';
import { useSettings } from '../../data/SettingsRepository';
import { Trip, TripRepository, tripTotal, useTripQuery } from '../../data/TripRepository';
import { paiseToRupees } from '../../fare/FareCalculator';
import { t as translate, useT } from '../../i18n';
import { Body, Button, Dialog, Divider, HeaderButton, KeyValueRow, Screen, Small } from '../../ui/components';
import { formatDateTime, formatDuration, formatKm, formatRupees, formatRupeesExact, tariffEffectiveText } from '../../ui/format';
import { FareTextStyle, useColors } from '../../ui/theme';

export default function SummaryScreen() {
  const t = useT();
  const c = useColors();
  const id = Number(useLocalSearchParams<{ tripId: string }>().tripId);
  const trip = useTripQuery(() => TripRepository.get(id), [id]);
  const settings = useSettings()!;
  const [confirmDelete, setConfirmDelete] = useState(false);

  return (
    <Screen>
      <Stack.Screen
        options={{
          title: t('summary_title'),
          headerRight: () => <HeaderButton glyph="🗑" label={t('delete')} onPress={() => setConfirmDelete(true)} />,
        }}
      />
      {trip ? (
        <>
          <Text style={{ fontSize: 17, fontWeight: 'bold', color: c.onBackground }}>{t('total_payable')}</Text>
          <Text style={[FareTextStyle, { color: c.onBackground }]}>{formatRupees(tripTotal(trip))}</Text>
          <Body>{formatDateTime(trip.startEpochMs) + ' → ' + formatDateTime(trip.endEpochMs)}</Body>
          <Small>{tariffLine(trip)}</Small>
          <Divider />
          <KeyValueRow label={t('distance')} value={formatKm(trip.distanceMeters)} />
          <KeyValueRow label={t('moving_time')} value={formatDuration(trip.movingSeconds * 1000)} />
          <KeyValueRow label={t('waiting_time')} value={formatDuration(trip.waitingSeconds * 1000)} />
          <Divider />
          <KeyValueRow label={t('bd_min_fare')} value={formatRupeesExact(paiseToRupees(trip.minFarePaise))} />
          <KeyValueRow label={t('bd_extra_distance', trip.chargedExtraKm.toFixed(1))} value={formatRupeesExact(paiseToRupees(trip.extraDistancePaise))} />
          <KeyValueRow label={t('bd_waiting', trip.waitingBlocks)} value={formatRupeesExact(paiseToRupees(trip.waitingPaise))} />
          <KeyValueRow
            label={trip.isNight ? t('bd_night') : t('bd_night_none')}
            value={formatRupeesExact(paiseToRupees(trip.nightSurchargePaise))}
          />
          <KeyValueRow label={t('bd_subtotal')} value={formatRupeesExact(paiseToRupees(trip.subtotalPaise))} />
          <KeyValueRow label={t('bd_total')} value={formatRupees(tripTotal(trip))} emphasize />
          <Button title={'⤴  ' + t('share')} onPress={() => shareTrip(trip)} style={{ marginTop: 16 }} />
          {canShowQr(settings.driver) ? (
            <Button
              kind="outlined"
              title={'▦  ' + t('show_upi_qr')}
              onPress={() => router.push({ pathname: '/qr/[tripId]', params: { tripId: String(id) } })}
            />
          ) : null}
          <Small style={{ marginTop: 12 }}>{t('disclaimer_short')}</Small>
        </>
      ) : null}

      <Dialog
        visible={confirmDelete}
        title={t('delete_trip_title')}
        onDismiss={() => setConfirmDelete(false)}
        actions={
          <>
            <Button kind="text" title={t('cancel')} onPress={() => setConfirmDelete(false)} />
            <Button
              title={t('delete')}
              onPress={async () => {
                setConfirmDelete(false);
                await TripRepository.delete(id);
                router.back();
              }}
            />
          </>
        }
      />
    </Screen>
  );
}

function tariffLine(trip: Trip): string {
  const eff = tariffEffectiveText(trip.tariffEffective);
  return eff ? translate('tariff_label', trip.tariffName, eff) : trip.tariffName;
}

function shareTrip(trip: Trip): void {
  const t = translate;
  const lines = [
    t('share_header'),
    formatDateTime(trip.startEpochMs) + ' → ' + formatDateTime(trip.endEpochMs),
    t('distance') + ': ' + formatKm(trip.distanceMeters),
    t('waiting_time') + ': ' + formatDuration(trip.waitingSeconds * 1000),
    t('bd_min_fare') + ': ' + formatRupeesExact(paiseToRupees(trip.minFarePaise)),
    t('bd_extra_distance', trip.chargedExtraKm.toFixed(1)) + ': ' + formatRupeesExact(paiseToRupees(trip.extraDistancePaise)),
    t('bd_waiting', trip.waitingBlocks) + ': ' + formatRupeesExact(paiseToRupees(trip.waitingPaise)),
    ...(trip.isNight ? [t('bd_night') + ': ' + formatRupeesExact(paiseToRupees(trip.nightSurchargePaise))] : []),
    t('bd_total') + ': ' + formatRupees(tripTotal(trip)),
    t('tariff') + ': ' + trip.tariffName,
    t('disclaimer_short'),
  ];
  Share.share({ message: lines.join('\n') }, { dialogTitle: t('share') }).catch(() => undefined);
}
