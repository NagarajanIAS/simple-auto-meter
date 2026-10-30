import { router } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { useState } from 'react';
import { Text } from 'react-native';
import { DriverSettings, hasPin } from '../data/AppSettings';
import { updateSettings, useSettings } from '../data/SettingsRepository';
import { useT } from '../i18n';
import { Body, Button, ErrorText, Field, Screen, SwitchRow } from '../ui/components';
import { EnterPin, PinField } from '../ui/EnterPin';
import { useColors } from '../ui/theme';
import { setPin } from '../upi/PinGate';
import { isValidPin } from '../upi/PinHasher';
import { isValidVpa } from '../upi/Upi';

const updateDriver = (transform: (d: DriverSettings) => DriverSettings) => updateSettings((s) => ({ ...s, driver: transform(s.driver) }));

/** Driver mode settings, behind the PIN. Unlocking lasts while this screen is open. */
export default function DriverScreen() {
  const t = useT();
  const d = useSettings()!.driver;
  const [unlocked, setUnlocked] = useState(false);
  const [changingPin, setChangingPin] = useState(false);

  let content;
  if (!hasPin(d)) {
    content = (
      <CreatePin
        onCreated={async (pin) => {
          await setPin(pin);
          setUnlocked(true);
        }}
      />
    );
  } else if (!unlocked) {
    content = <EnterPin onUnlocked={() => setUnlocked(true)} />;
  } else if (changingPin) {
    content = (
      <CreatePin
        onCreated={async (pin) => {
          await setPin(pin);
          setChangingPin(false);
        }}
      />
    );
  } else {
    content = <DriverSettingsForm driver={d} onChangePin={() => setChangingPin(true)} />;
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: t('driver_mode') }} />
      {content}
    </Screen>
  );
}

function CreatePin({ onCreated }: { onCreated: (pin: string) => Promise<void> }) {
  const t = useT();
  const c = useColors();
  // Component state only: PIN digits are never persisted.
  const [pin, setPinText] = useState('');
  const [again, setAgain] = useState('');
  const [saving, setSaving] = useState(false);
  const mismatch = again.length === 4 && pin !== again;
  return (
    <>
      <Text style={{ fontSize: 22, fontWeight: 'bold', color: c.onBackground }}>{t('pin_create_title')}</Text>
      <Body>{t('pin_create_text')}</Body>
      <PinField label={t('pin_new')} value={pin} onValue={setPinText} />
      <PinField label={t('pin_repeat')} value={again} onValue={setAgain} isError={mismatch} />
      {mismatch ? <ErrorText>{t('pin_mismatch')}</ErrorText> : null}
      <Button
        title={t('pin_save')}
        loading={saving}
        disabled={!isValidPin(pin) || pin !== again || saving}
        onPress={async () => {
          setSaving(true);
          try {
            await onCreated(pin);
          } finally {
            setSaving(false);
          }
        }}
      />
    </>
  );
}

function DriverSettingsForm({ driver, onChangePin }: { driver: DriverSettings; onChangePin: () => void }) {
  const t = useT();
  const [vpa, setVpa] = useState(driver.vpa);
  const [name, setName] = useState(driver.payeeName);
  const vpaValid = isValidVpa(vpa);
  const dirty = vpa.trim() !== driver.vpa || name.trim() !== driver.payeeName;

  return (
    <>
      <SwitchRow
        label={t('driver_mode_enable')}
        value={driver.enabled}
        onChange={(on) => updateDriver((d) => ({ ...d, enabled: on }))}
        supporting={t('driver_mode_text')}
      />
      <Field
        label={t('upi_id')}
        value={vpa}
        onChangeText={(v) => setVpa(v.trim())}
        placeholder="name@bank"
        keyboardType="email-address"
        isError={vpa !== '' && !vpaValid}
        supportingText={vpa !== '' && !vpaValid ? t('upi_id_invalid') : null}
      />
      <Field label={t('payee_name')} value={name} onChangeText={setName} />
      <Button
        title={t('save')}
        disabled={!dirty || !vpaValid || name.trim() === ''}
        onPress={() => updateDriver((d) => ({ ...d, vpa: vpa.trim(), payeeName: name.trim() }))}
        style={{ marginTop: 8 }}
      />
      <Button
        kind="outlined"
        title={t('static_qr')}
        disabled={driver.vpa === ''}
        onPress={() => router.push({ pathname: '/qr/[tripId]', params: { tripId: 'static' } })}
        style={{ marginTop: 16 }}
      />
      <Button kind="outlined" title={t('earnings_title')} onPress={() => router.push('/earnings')} />
      <Button kind="outlined" title={t('pin_change')} onPress={onChangePin} />
      <Body style={{ fontWeight: 'bold', marginTop: 16 }}>{t('upi_confirm_note')}</Body>
    </>
  );
}
