import { Text } from 'react-native';
import { useT } from '../i18n';
import { Card } from './components';
import { useColors } from './theme';

export function DisclaimerCard() {
  const t = useT();
  const c = useColors();
  return (
    <Card color={c.primaryContainer}>
      <Text style={{ fontSize: 17, fontWeight: 'bold', color: c.onPrimaryContainer }}>{t('disclaimer_full')}</Text>
    </Card>
  );
}
