import Constants from 'expo-constants';
import { Stack } from 'expo-router/stack';
import { Text } from 'react-native';
import { FarePresets } from '../fare/FarePresets';
import { useT } from '../i18n';
import { Body, Screen, SectionTitle } from '../ui/components';
import { DisclaimerCard } from '../ui/DisclaimerCard';
import { useColors } from '../ui/theme';

export default function AboutScreen() {
  const t = useT();
  const c = useColors();
  return (
    <Screen>
      <Stack.Screen options={{ title: t('about_title') }} />
      <Text style={{ fontSize: 24, fontWeight: 'bold', color: c.onBackground }}>
        {t('app_name')} {Constants.expoConfig?.version ?? ''}
      </Text>
      <DisclaimerCard />
      <Body>{t('disclaimer_more')}</Body>
      <SectionTitle>{t('privacy')}</SectionTitle>
      <Body>{t('privacy_summary')}</Body>
      <SectionTitle>{t('tariff_sources')}</SectionTitle>
      {FarePresets.all.map((p) => (
        <Body key={p.id}>{`• ${p.name} — ${p.source}`}</Body>
      ))}
      <Body>{t('tariff_verify')}</Body>
      <SectionTitle>{t('credits')}</SectionTitle>
      <Body>{t('credits_text')}</Body>
    </Screen>
  );
}
