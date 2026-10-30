import Decimal from 'decimal.js';
import { Stack } from 'expo-router/stack';
import { useMemo } from 'react';
import { Text } from 'react-native';
import { Trip, TripRepository, useTripQuery } from '../data/TripRepository';
import { paiseToRupees } from '../fare/FareCalculator';
import { plural, useT } from '../i18n';
import { Card, KeyValueRow, Screen, Small } from '../ui/components';
import { formatDate, formatKm, formatRupees } from '../ui/format';
import { FareTextStyle, useColors } from '../ui/theme';

interface DayEarnings {
  date: Date;
  key: string;
  trips: number;
  distanceMeters: number;
  total: Decimal;
}

const dayKey = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;

/** Local-only totals per day for the last 30 days (driver mode). */
function groupByDay(trips: Trip[]): DayEarnings[] {
  const days = new Map<string, DayEarnings & { paise: number }>();
  for (const trip of trips) {
    const end = new Date(trip.endEpochMs);
    const key = dayKey(end);
    const day = days.get(key) ?? {
      date: new Date(end.getFullYear(), end.getMonth(), end.getDate()),
      key,
      trips: 0,
      distanceMeters: 0,
      paise: 0,
      total: new Decimal(0),
    };
    day.trips++;
    day.distanceMeters += trip.distanceMeters;
    day.paise += trip.totalPaise;
    day.total = paiseToRupees(day.paise);
    days.set(key, day);
  }
  return [...days.values()].sort((a, b) => b.date.getTime() - a.date.getTime());
}

export default function EarningsScreen() {
  const t = useT();
  const c = useColors();
  const since = useMemo(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29).getTime();
  }, []);
  const trips = useTripQuery(() => TripRepository.since(since), [since]);
  const days = useMemo(() => groupByDay(trips ?? []), [trips]);
  const today = days.find((d) => d.key === dayKey(new Date()));

  return (
    <Screen>
      <Stack.Screen options={{ title: t('earnings_title') }} />
      <Card color={c.primaryContainer}>
        <Text style={{ color: c.onPrimaryContainer, fontSize: 16 }}>{t('earnings_today')}</Text>
        <Text style={[FareTextStyle, { color: c.onPrimaryContainer }]}>{formatRupees(today?.total ?? new Decimal(0))}</Text>
        <Text style={{ color: c.onPrimaryContainer, fontSize: 16 }}>
          {plural('earnings_trips', today?.trips ?? 0) + ' · ' + formatKm(today?.distanceMeters ?? 0)}
        </Text>
      </Card>
      <Text style={{ fontSize: 17, fontWeight: 'bold', color: c.onBackground, marginTop: 16 }}>{t('earnings_last_30')}</Text>
      {days.map((d) => (
        <KeyValueRow key={d.key} label={`${formatDate(d.date)} (${plural('earnings_trips', d.trips)})`} value={formatRupees(d.total)} />
      ))}
      <Small style={{ marginTop: 12 }}>{t('earnings_note')}</Small>
    </Screen>
  );
}
