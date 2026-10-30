import { router } from 'expo-router';
import { Stack } from 'expo-router/stack';
import { useState } from 'react';
import { Linking, Text } from 'react-native';
import { useT } from '../i18n';
import { requestMeterPermissions } from '../tracking/LocationHelper';
import { Body, Button, ErrorText, Screen } from '../ui/components';
import { useColors } from '../ui/theme';

/** Prominent disclosure shown before the system location permission dialog. */
export default function PermissionScreen() {
  const t = useT();
  const c = useColors();
  const [denied, setDenied] = useState(false);
  const done = () => (router.canGoBack() ? router.back() : router.replace('/meter'));

  return (
    <Screen padding={24}>
      <Stack.Screen options={{ title: t('disclosure_title') }} />
      <Text style={{ fontSize: 48, color: c.secondary }}>📍</Text>
      <Text style={{ fontSize: 24, fontWeight: 'bold', color: c.onBackground }}>{t('disclosure_heading')}</Text>
      <Body>{t('disclosure_body')}</Body>
      <Body>• {t('disclosure_point_1')}</Body>
      <Body>• {t('disclosure_point_2')}</Body>
      <Body>• {t('disclosure_point_3')}</Body>
      <Body>• {t('disclosure_point_4')}</Body>
      {denied ? (
        <>
          <ErrorText>{t('permission_denied')}</ErrorText>
          <Button kind="outlined" title={t('open_app_settings')} onPress={() => Linking.openSettings()} />
        </>
      ) : null}
      <Button
        title={t('continue_')}
        height={64}
        onPress={async () => {
          if (await requestMeterPermissions()) done();
          else setDenied(true);
        }}
      />
      <Button kind="outlined" title={t('not_now')} onPress={done} />
    </Screen>
  );
}
