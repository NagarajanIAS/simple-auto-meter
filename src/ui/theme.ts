import { useColorScheme } from 'react-native';

// Auto-rickshaw yellow and black: maximum contrast for reading in sunlight.
export const AutoYellow = '#FFC400';
export const AutoBlack = '#0D0D0D';
export const WaitAmber = '#FF8F00';
export const GoGreen = '#1B8A3A';
export const StopRed = '#C62828';

export interface Colors {
  primary: string;
  onPrimary: string;
  primaryContainer: string;
  onPrimaryContainer: string;
  secondary: string;
  secondaryContainer: string;
  onSecondaryContainer: string;
  background: string;
  onBackground: string;
  surfaceVariant: string;
  onSurfaceVariant: string;
  outline: string;
  error: string;
}

const light: Colors = {
  primary: AutoBlack,
  onPrimary: AutoYellow,
  primaryContainer: AutoYellow,
  onPrimaryContainer: AutoBlack,
  secondary: '#5C4A00',
  secondaryContainer: '#FFE9A6',
  onSecondaryContainer: AutoBlack,
  background: '#FFFFFF',
  onBackground: AutoBlack,
  surfaceVariant: '#F2F0E8',
  onSurfaceVariant: '#2B2B2B',
  outline: '#7A7566',
  error: StopRed,
};

const dark: Colors = {
  primary: AutoYellow,
  onPrimary: AutoBlack,
  primaryContainer: AutoYellow,
  onPrimaryContainer: AutoBlack,
  secondary: '#FFE08A',
  secondaryContainer: '#3A3000',
  onSecondaryContainer: '#FFF4CC',
  background: '#000000',
  onBackground: '#FFFFFF',
  surfaceVariant: '#1E1E1E',
  onSurfaceVariant: '#EDEDED',
  outline: '#9A9483',
  error: '#FF6E6E',
};

export function useColors(): Colors {
  return useColorScheme() === 'dark' ? dark : light;
}

export const FareTextStyle = { fontSize: 72, fontWeight: '900' as const, lineHeight: 80 };
