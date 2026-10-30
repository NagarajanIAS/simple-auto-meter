import { router } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { Text } from 'react-native';
import { updateSettings } from '../data/SettingsRepository';
import { useT } from '../i18n';
import { Body, Button, Screen } from '../ui/components';
import { DisclaimerCard } from '../ui/DisclaimerCard';
import { useColors } from '../ui/theme';

export default function DisclaimerScreen() {
  const t = useT();
  const c = useColors();
  return (
    <Screen padding={24}>
      <Stack.Screen options={{ headerShown: false }} />
      <Text style={{ fontSize: 56, color: c.error }}>⚠</Text>
      <Text style={{ fontSize: 32, fontWeight: '900', color: c.onBackground }}>{t('app_name')}</Text>
      <DisclaimerCard />
      <Body>{t('disclaimer_more')}</Body>
      <Body>{t('privacy_summary')}</Body>
      <Button
        title={t('i_understand')}
        height={64}
        onPress={async () => {
          await updateSettings((s) => ({ ...s, disclaimerAccepted: true }));
          router.replace('/meter');
        }}
      />
    </Screen>
  );
}
