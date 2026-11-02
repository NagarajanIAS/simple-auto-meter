import { router } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { activeFareConfig, activePreset, AppSettings, hasPin, LanguageTag } from '../data/AppSettings';
import { updateSettings, useSettings } from '../data/SettingsRepository';
import { FareConfig, FareRounding, isValidFare, MAX_KM, MAX_MULTIPLIER, MAX_RUPEES, NightRule } from '../fare/FareConfig';
import { FarePresets } from '../fare/FarePresets';
import { StringKey, useT } from '../i18n';
import { RoutingProviderId } from '../routing/RoutingProvider';
import { GpsFilterParams } from '../tracking/GpsDistanceFilter';
import { isValidMeterParams, MeterParams } from '../tracking/TripAccumulator';
import { Body, Button, Dialog, ErrorText, Field, NumberField, RadioRow, Screen, SectionTitle, Small, SwitchRow } from '../ui/components';
import { EnterPin } from '../ui/EnterPin';
import { hhMmToMinutes, minutesToHhMm, tariffEffectiveText } from '../ui/format';
import { isValidTileUrl } from '../ui/tileUrl';
import { useColors } from '../ui/theme';

const update = (transform: (s: AppSettings) => AppSettings) => updateSettings(transform);

function selectPreset(id: string) {
  // Switching to Custom starts from whatever tariff was active.
  return update((s) => (id === FarePresets.CUSTOM_ID ? { ...s, presetId: id, customFare: activeFareConfig(s) } : { ...s, presetId: id }));
}

/** Editing any fare field turns the active tariff into Custom. */
function editFare(transform: (f: FareConfig) => FareConfig) {
  return update((s) => {
    const edited = transform(activeFareConfig(s));
    return isValidFare(edited) ? { ...s, presetId: FarePresets.CUSTOM_ID, customFare: edited } : s;
  });
}

function editMeter(transform: (m: MeterParams) => MeterParams) {
  return update((s) => {
    const edited = transform(s.meter);
    return isValidMeterParams(edited) ? { ...s, meter: edited } : s;
  });
}

const editGps = (patch: Partial<GpsFilterParams>) => editMeter((m) => ({ ...m, gps: { ...m.gps, ...patch } }));

export default function SettingsScreen() {
  const t = useT();
  const s = useSettings()!;
  // Guards the tariff section while driver mode is on; lasts while this screen is open.
  const [unlocked, setUnlocked] = useState(false);
  const [showPin, setShowPin] = useState(false);
  const tariffLocked = s.driver.enabled && hasPin(s.driver) && !unlocked;
  const preset = activePreset(s);

  const languages: [LanguageTag, string][] = [
    ['', t('language_system')],
    ['en', 'English'],
    ['ta', 'தமிழ்'],
  ];

  return (
    <Screen>
      <Stack.Screen options={{ title: t('settings_title') }} />
      <SectionTitle>{t('language')}</SectionTitle>
      {languages.map(([tag, label]) => (
        <RadioRow key={tag || 'system'} label={label} selected={s.language === tag} onPress={() => update((x) => ({ ...x, language: tag }))} />
      ))}

      {/* Tariff — read-only behind the driver PIN while driver mode is on. */}
      <SectionTitle>{t('tariff')}</SectionTitle>
      {tariffLocked ? (
        <>
          <Body>
            {tariffEffectiveText(preset?.effectiveDate)
              ? t('tariff_label', preset?.name ?? t('tariff_custom'), tariffEffectiveText(preset?.effectiveDate))
              : (preset?.name ?? t('tariff_custom'))}
          </Body>
          <Small>{t('tariff_locked')}</Small>
          <Button kind="outlined" title={t('tariff_unlock')} onPress={() => setShowPin(true)} style={{ marginTop: 8 }} />
          <Dialog visible={showPin} onDismiss={() => setShowPin(false)} actions={<Button kind="text" title={t('cancel')} onPress={() => setShowPin(false)} />}>
            <EnterPin
              onUnlocked={() => {
                setUnlocked(true);
                setShowPin(false);
              }}
            />
          </Dialog>
        </>
      ) : (
        <>
          {FarePresets.all.map((p) => {
            const eff = tariffEffectiveText(p.effectiveDate);
            return <RadioRow key={p.id} label={p.name + (eff ? ` · ${eff}` : '')} selected={s.presetId === p.id} onPress={() => selectPreset(p.id)} />;
          })}
          <RadioRow label={t('tariff_custom')} selected={!preset} onPress={() => selectPreset(FarePresets.CUSTOM_ID)} />
          <Small>{t('tariff_edit_note')}</Small>
          <TariffFields fare={activeFareConfig(s)} />
        </>
      )}

      <SectionTitle>{t('meter_settings')}</SectionTitle>
      <MeterFields meter={s.meter} />
      <MapsAndOtherSections s={s} />
    </Screen>
  );
}

function TariffFields({ fare }: { fare: FareConfig }) {
  const t = useT();
  const rupees = (v: number) => v >= 0 && v <= MAX_RUPEES;
  const nightRules: [NightRule, StringKey][] = [
    ['TRIP_START', 'night_rule_start'],
    ['TRIP_END', 'night_rule_end'],
    ['ANY_PART', 'night_rule_any'],
  ];
  const roundings: [FareRounding, StringKey][] = [
    ['NEAREST', 'rounding_nearest'],
    ['UP', 'rounding_up'],
  ];
  return (
    <>
      <NumberField label={t('f_min_fare')} value={fare.minFare} onValue={(v) => editFare((f) => ({ ...f, minFare: v }))} valid={rupees} />
      <NumberField
        label={t('f_min_distance')}
        value={fare.minDistanceKm}
        onValue={(v) => editFare((f) => ({ ...f, minDistanceKm: v }))}
        valid={(v) => v >= 0 && v <= MAX_KM}
      />
      <NumberField label={t('f_per_km')} value={fare.perKmRate} onValue={(v) => editFare((f) => ({ ...f, perKmRate: v }))} valid={rupees} />
      <NumberField
        label={t('f_step')}
        value={fare.distanceStepMeters}
        integer
        onValue={(v) => editFare((f) => ({ ...f, distanceStepMeters: v }))}
        valid={(v) => v >= 1 && v <= 100_000}
      />
      <NumberField label={t('f_waiting_rate')} value={fare.waitingRate} onValue={(v) => editFare((f) => ({ ...f, waitingRate: v }))} valid={rupees} />
      <NumberField
        label={t('f_waiting_block')}
        value={fare.waitingBlockMinutes}
        integer
        onValue={(v) => editFare((f) => ({ ...f, waitingBlockMinutes: v }))}
        valid={(v) => v >= 1 && v <= 1440}
      />
      <NumberField
        label={t('f_night_multiplier')}
        value={fare.nightMultiplier}
        onValue={(v) => editFare((f) => ({ ...f, nightMultiplier: v }))}
        valid={(v) => v >= 1 && v <= MAX_MULTIPLIER}
      />
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <TimeField label={t('f_night_start')} minutes={fare.nightStartMinutes} onValue={(m) => editFare((f) => ({ ...f, nightStartMinutes: m }))} />
        <TimeField label={t('f_night_end')} minutes={fare.nightEndMinutes} onValue={(m) => editFare((f) => ({ ...f, nightEndMinutes: m }))} />
      </View>
      <SwitchRow label={t('f_night_waiting')} value={fare.nightAppliesToWaiting} onChange={(v) => editFare((f) => ({ ...f, nightAppliesToWaiting: v }))} />
      <SubTitle>{t('f_night_rule')}</SubTitle>
      {nightRules.map(([rule, label]) => (
        <RadioRow key={rule} label={t(label)} selected={fare.nightRule === rule} onPress={() => editFare((f) => ({ ...f, nightRule: rule }))} />
      ))}
      <SubTitle>{t('f_rounding')}</SubTitle>
      {roundings.map(([rounding, label]) => (
        <RadioRow key={rounding} label={t(label)} selected={fare.rounding === rounding} onPress={() => editFare((f) => ({ ...f, rounding }))} />
      ))}
    </>
  );
}

function MeterFields({ meter }: { meter: MeterParams }) {
  const t = useT();
  return (
    <>
      <NumberField
        label={t('m_wait_speed')}
        value={meter.autoWaitSpeedKmh}
        onValue={(v) => editMeter((m) => ({ ...m, autoWaitSpeedKmh: v }))}
        valid={(v) => v >= 0 && v <= 1_000}
      />
      <NumberField
        label={t('m_wait_after')}
        value={meter.autoWaitAfterSeconds}
        integer
        onValue={(v) => editMeter((m) => ({ ...m, autoWaitAfterSeconds: v }))}
        valid={(v) => v >= 0 && v <= 86_400}
      />
      <NumberField label={t('m_max_accuracy')} value={meter.gps.maxAccuracyM} onValue={(v) => editGps({ maxAccuracyM: v })} valid={(v) => v >= 5 && v <= 10_000} />
      <NumberField label={t('m_max_speed')} value={meter.gps.maxSpeedKmh} onValue={(v) => editGps({ maxSpeedKmh: v })} valid={(v) => v >= 20 && v <= 1_000} />
      <NumberField label={t('m_min_segment')} value={meter.gps.minSegmentM} onValue={(v) => editGps({ minSegmentM: v })} valid={(v) => v >= 1 && v <= 10_000} />
      <SwitchRow label={t('m_smoothing')} value={meter.gps.smoothing} onChange={(v) => editGps({ smoothing: v })} />
      <Small>{t('meter_settings_note')}</Small>
    </>
  );
}

function MapsAndOtherSections({ s }: { s: AppSettings }) {
  const t = useT();
  const [tileUrlError, setTileUrlError] = useState(false);
  const providers: [RoutingProviderId, StringKey][] = [
    ['MANUAL', 'routing_manual'],
    ['HAVERSINE', 'routing_haversine'],
    ['OPENROUTESERVICE', 'routing_ors'],
  ];
  return (
    <>
      <SectionTitle>{t('maps_routing')}</SectionTitle>
      <NumberField label={t('road_factor')} value={s.roadFactor} onValue={(v) => update((x) => ({ ...x, roadFactor: v }))} valid={(v) => v >= 1 && v <= 3} />
      <SubTitle>{t('routing_provider')}</SubTitle>
      {providers.map(([id, label]) => (
        <RadioRow
          key={id}
          label={t(label)}
          selected={s.routingProvider === id}
          disabled={id === 'OPENROUTESERVICE' && s.orsApiKey.trim() === ''}
          onPress={() => update((x) => ({ ...x, routingProvider: id }))}
        />
      ))}
      <TextSetting
        label={t('ors_key')}
        value={s.orsApiKey}
        secure
        onValue={(v) =>
          update((x) => ({
            ...x,
            orsApiKey: v.trim(),
            routingProvider: v.trim() === '' && x.routingProvider === 'OPENROUTESERVICE' ? 'MANUAL' : x.routingProvider,
          }))
        }
      />
      <Small>{t('ors_note')}</Small>
      <TextSetting
        label={t('tile_url')}
        value={s.tileUrl}
        isError={tileUrlError}
        onValue={(v) => {
          // Only https templates with all three tile placeholders are accepted.
          const trimmed = v.trim();
          const ok = trimmed === '' || isValidTileUrl(trimmed);
          setTileUrlError(!ok);
          if (ok) update((x) => ({ ...x, tileUrl: trimmed }));
        }}
      />
      {tileUrlError ? <ErrorText>{t('tile_url_invalid')}</ErrorText> : null}
      <Small>{t('tile_note')}</Small>

      <SectionTitle>{t('other')}</SectionTitle>
      <Button kind="outlined" title={t('driver_mode') + (s.driver.enabled ? ' ✓' : '')} onPress={() => router.push('/driver')} />
      <Button kind="outlined" title={t('location_permission')} onPress={() => router.push('/permission')} />
      <Button kind="outlined" title={t('about_title')} onPress={() => router.push('/about')} />
    </>
  );
}

function SubTitle({ children }: { children: string }) {
  const c = useColors();
  return <Text style={{ fontSize: 15, fontWeight: 'bold', color: c.onBackground, marginTop: 8 }}>{children}</Text>;
}

function TimeField({ label, minutes, onValue }: { label: string; minutes: number; onValue: (m: number) => void }) {
  const shown = minutesToHhMm(minutes);
  const [text, setText] = useState(shown);
  useEffect(() => setText(shown), [shown]);
  return (
    <Field
      style={{ flex: 1 }}
      label={label}
      value={text}
      isError={hhMmToMinutes(text) == null}
      keyboardType="numbers-and-punctuation"
      onChangeText={(v) => {
        setText(v);
        const m = hhMmToMinutes(v);
        if (m != null) onValue(m);
      }}
    />
  );
}

function TextSetting({ label, value, secure, isError, onValue }: { label: string; value: string; secure?: boolean; isError?: boolean; onValue: (v: string) => void }) {
  const [text, setText] = useState(value);
  useEffect(() => setText(value), [value]);
  return (
    <Field
      label={label}
      value={text}
      secure={secure}
      isError={isError}
      onChangeText={(v) => {
        setText(v);
        onValue(v);
      }}
    />
  );
}
