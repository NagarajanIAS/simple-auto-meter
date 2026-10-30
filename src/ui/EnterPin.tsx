import { useEffect, useState } from 'react';
import { Text } from 'react-native';
import { useT } from '../i18n';
import { lockRemainingMs, tryUnlock } from '../upi/PinGate';
import { Button, ErrorText, Field } from './components';
import { useColors } from './theme';

export function PinField({ label, value, onValue, isError }: { label: string; value: string; onValue: (v: string) => void; isError?: boolean }) {
  return (
    <Field
      label={label}
      value={value}
      onChangeText={(v) => {
        if (v.length <= 4 && /^[0-9]*$/.test(v)) onValue(v);
      }}
      isError={isError}
      secure
      keyboardType="number-pad"
      maxLength={4}
      inputStyle={{ fontSize: 28 }}
    />
  );
}

/** PIN entry with the persisted lockout countdown. Calls onUnlocked after a correct PIN. */
export function EnterPin({ onUnlocked }: { onUnlocked: () => void }) {
  const t = useT();
  const c = useColors();
  const [pin, setPin] = useState('');
  const [wrong, setWrong] = useState(false);
  const [checking, setChecking] = useState(false);
  const [lockSeconds, setLockSeconds] = useState(0);

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const poll = async () => {
      const seconds = Math.ceil((await lockRemainingMs()) / 1000);
      if (!active) return;
      setLockSeconds(seconds);
      if (seconds > 0) timer = setTimeout(poll, 1000);
    };
    poll();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [wrong, checking]);

  return (
    <>
      <Text style={{ fontSize: 22, fontWeight: 'bold', color: c.onBackground }}>{t('pin_enter_title')}</Text>
      <PinField
        label={t('pin')}
        value={pin}
        onValue={(v) => {
          setPin(v);
          setWrong(false);
        }}
        isError={wrong}
      />
      {lockSeconds > 0 ? <ErrorText>{t('pin_locked', lockSeconds)}</ErrorText> : wrong ? <ErrorText>{t('pin_wrong')}</ErrorText> : null}
      <Button
        title={t('unlock')}
        disabled={pin.length !== 4 || checking || lockSeconds > 0}
        loading={checking}
        onPress={async () => {
          setChecking(true);
          const ok = await tryUnlock(pin);
          setChecking(false);
          if (ok) {
            onUnlocked();
          } else {
            setWrong(true);
            setPin('');
          }
        }}
      />
    </>
  );
}
