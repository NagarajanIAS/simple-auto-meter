import { router } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { FlatList, Pressable, Text, View } from 'react-native';
import { useSettings } from '../data/SettingsRepository';
import { TripRepository, tripTotal, useTripQuery } from '../data/TripRepository';
import { useT } from '../i18n';
import { Body, Card, HeaderButton } from '../ui/components';
import { formatDateTime, formatDuration, formatKm, formatRupees } from '../ui/format';
import { useColors } from '../ui/theme';

export default function HistoryScreen() {
  const t = useT();
  const c = useColors();
  const trips = useTripQuery(() => TripRepository.all(), []);
  const driverMode = useSettings()!.driver.enabled;

  return (
    <>
      <Stack.Screen
        options={{
          title: t('history_title'),
          headerRight: driverMode ? () => <HeaderButton glyph="₹" label={t('earnings_title')} onPress={() => router.push('/earnings')} /> : undefined,
        }}
      />
      {trips && trips.length === 0 ? (
        <View style={{ padding: 16 }}>
          <Body>{t('history_empty')}</Body>
        </View>
      ) : (
        <FlatList
          data={trips ?? []}
          keyExtractor={(trip) => String(trip.id)}
          contentContainerStyle={{ padding: 16 }}
          style={{ backgroundColor: c.background }}
          renderItem={({ item: trip }) => (
            <Pressable
              accessibilityRole="button"
              onPress={() => router.push({ pathname: '/summary/[tripId]', params: { tripId: String(trip.id) } })}
            >
              <Card style={{ marginBottom: 8, flexDirection: 'row', alignItems: 'center' }}>
                <View style={{ flex: 1 }}>
                  <Body style={{ fontWeight: 'bold' }}>{formatDateTime(trip.startEpochMs)}</Body>
                  <Body>
                    {formatKm(trip.distanceMeters) +
                      ' · ' +
                      formatDuration((trip.movingSeconds + trip.waitingSeconds) * 1000) +
                      (trip.isNight ? ' · ' + t('night') : '')}
                  </Body>
                </View>
                <Text style={{ fontSize: 24, fontWeight: 'bold', color: c.onBackground }}>{formatRupees(tripTotal(trip))}</Text>
              </Card>
            </Pressable>
          )}
        />
      )}
    </>
  );
}
