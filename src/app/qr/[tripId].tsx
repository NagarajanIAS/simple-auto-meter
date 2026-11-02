import * as Brightness from 'expo-brightness';
import { useKeepAwake } from 'expo-keep-awake';
import { useLocalSearchParams } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { useEffect, useMemo } from 'react';
import { Platform, Text, useWindowDimensions, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { useSettings } from '../../data/SettingsRepository';
import { TripRepository, tripTotal, useTripQuery } from '../../data/TripRepository';
import { useT } from '../../i18n';
import { Screen } from '../../ui/components';
import { formatRupees, formatRupeesExact, formatUpiNoteTime } from '../../ui/format';
import { payUri } from '../../upi/Upi';

/** Full-screen UPI QR. tripId "static" shows a QR without an amount. */
export default function QrScreen() {
  const t = useT();
  const { tripId } = useLocalSearchParams<{ tripId: string }>();
  const isStatic = tripId === 'static';
  const id = Number(tripId);
  const trip = useTripQuery(() => (isStatic ? Promise.resolve(null) : TripRepository.get(id)), [tripId]);
  const driver = useSettings()!.driver;
  const { width } = useWindowDimensions();

  useKeepAwake();
  useFullBrightness();

  const amount = trip ? tripTotal(trip) : null;
  const note = useMemo(() => (isStatic ? null : t('upi_note', formatUpiNoteTime(new Date()))), [isStatic, t]);
  const uri = useMemo(() => {
    try {
      return payUri(driver.vpa, driver.payeeName, amount, note);
    } catch {
      return null;
    }
  }, [driver.vpa, driver.payeeName, amount, note]);
  const ready = uri != null && (isStatic || amount != null);

  return (
    <Screen background="#FFFFFF">
      <Stack.Screen options={{ title: t('upi_qr_title'), headerStyle: { backgroundColor: '#FFFFFF' }, headerTintColor: '#000000' }} />
      {ready ? (
        <View style={{ alignItems: 'center', gap: 4 }}>
          <Text
            style={{
              fontSize: amount ? 80 : 36,
              lineHeight: amount ? 88 : 44,
              fontWeight: '900',
              color: '#000000',
              textAlign: 'center',
            }}
          >
            {amount ? formatRupees(amount) : t('qr_any_amount')}
          </Text>
          {amount ? <Text style={{ color: '#444444', fontSize: 16 }}>{formatRupeesExact(amount)}</Text> : null}
          <View style={{ paddingVertical: 8 }} accessibilityLabel={t('upi_qr_title')}>
            <QRCode value={uri} size={Math.min(width - 32, 480)} ecl="M" quietZone={16} color="#000000" backgroundColor="#FFFFFF" />
          </View>
          <Text style={{ color: '#000000', fontSize: 24, fontWeight: 'bold' }}>{driver.payeeName}</Text>
          <Text style={{ color: '#000000', fontSize: 18 }}>{driver.vpa}</Text>
          <Text style={{ color: '#B00020', fontWeight: 'bold', textAlign: 'center', fontSize: 16, marginTop: 16 }}>{t('upi_confirm_note')}</Text>
        </View>
      ) : null}
    </Screen>
  );
}

/** Full brightness while the QR is visible, restored on leaving. */
function useFullBrightness() {
  useEffect(() => {
    let previous: number | null = null;
    (async () => {
      try {
        previous = await Brightness.getBrightnessAsync();
        await Brightness.setBrightnessAsync(1);
      } catch {
        // Brightness control unavailable; the QR is still shown.
      }
    })();
    return () => {
      if (Platform.OS === 'android') Brightness.restoreSystemBrightnessAsync().catch(() => undefined);
      else if (previous != null) Brightness.setBrightnessAsync(previous).catch(() => undefined);
    };
  }, []);
}
