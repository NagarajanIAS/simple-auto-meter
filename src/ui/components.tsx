import { ReactNode, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardTypeOptions,
  Modal,
  Pressable,
  ScrollView,
  StyleProp,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TextStyle,
  View,
  ViewStyle,
} from 'react-native';
import { useColors } from './theme';

/** Scrollable screen body with the standard padding. */
export function Screen({ children, padding = 16, background }: { children: ReactNode; padding?: number; background?: string }) {
  const c = useColors();
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: background ?? c.background }}
      contentContainerStyle={{ padding, gap: 8, paddingBottom: 32 }}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  );
}

export function Body({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  const c = useColors();
  return <Text style={[{ fontSize: 18, color: c.onBackground }, style]}>{children}</Text>;
}

export function Small({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  const c = useColors();
  return <Text style={[{ fontSize: 13, color: c.onSurfaceVariant }, style]}>{children}</Text>;
}

export function ErrorText({ children }: { children: ReactNode }) {
  const c = useColors();
  return <Text style={{ fontSize: 16, color: c.error }}>{children}</Text>;
}

export function SectionTitle({ children }: { children: ReactNode }) {
  const c = useColors();
  return <Text style={{ fontSize: 17, fontWeight: 'bold', color: c.secondary, marginTop: 20, marginBottom: 4 }}>{children}</Text>;
}

type ButtonKind = 'filled' | 'outlined' | 'text';

export function Button({
  title,
  onPress,
  disabled,
  kind = 'filled',
  height = 56,
  color,
  textColor,
  fontSize = 18,
  loading,
  style,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  kind?: ButtonKind;
  height?: number;
  color?: string;
  textColor?: string;
  fontSize?: number;
  loading?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const c = useColors();
  const bg = kind === 'filled' ? (color ?? c.primary) : 'transparent';
  const fg = textColor ?? (kind === 'filled' ? c.onPrimary : c.onBackground);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        {
          minHeight: height,
          backgroundColor: bg,
          borderColor: kind === 'outlined' ? c.outline : 'transparent',
          opacity: disabled ? 0.4 : pressed ? 0.75 : 1,
        },
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={fg} style={{ marginRight: 8 }} /> : null}
      <Text style={{ color: fg, fontSize, fontWeight: 'bold', textAlign: 'center' }}>{title}</Text>
    </Pressable>
  );
}

export function Card({ children, color, style }: { children: ReactNode; color?: string; style?: StyleProp<ViewStyle> }) {
  const c = useColors();
  return <View style={[{ backgroundColor: color ?? c.surfaceVariant, borderRadius: 12, padding: 16 }, style]}>{children}</View>;
}

export function Field({
  label,
  value,
  onChangeText,
  isError,
  secure,
  keyboardType,
  placeholder,
  supportingText,
  maxLength,
  style,
  inputStyle,
  onSubmitEditing,
  returnKeyType,
}: {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  isError?: boolean;
  secure?: boolean;
  keyboardType?: KeyboardTypeOptions;
  placeholder?: string;
  supportingText?: string | null;
  maxLength?: number;
  style?: StyleProp<ViewStyle>;
  inputStyle?: StyleProp<TextStyle>;
  onSubmitEditing?: () => void;
  returnKeyType?: 'go' | 'done';
}) {
  const c = useColors();
  return (
    <View style={[{ marginVertical: 4 }, style]}>
      <Text style={{ fontSize: 13, color: isError ? c.error : c.onSurfaceVariant, marginBottom: 2 }}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        secureTextEntry={secure}
        keyboardType={keyboardType}
        placeholder={placeholder}
        placeholderTextColor={c.outline}
        maxLength={maxLength}
        autoCapitalize="none"
        autoCorrect={false}
        onSubmitEditing={onSubmitEditing}
        returnKeyType={returnKeyType}
        accessibilityLabel={label}
        style={[
          {
            borderWidth: isError ? 2 : 1,
            borderColor: isError ? c.error : c.outline,
            borderRadius: 6,
            paddingHorizontal: 12,
            paddingVertical: 10,
            fontSize: 18,
            color: c.onBackground,
          },
          inputStyle,
        ]}
      />
      {supportingText ? <Text style={{ fontSize: 13, color: isError ? c.error : c.onSurfaceVariant }}>{supportingText}</Text> : null}
    </View>
  );
}

/**
 * Text field for a number that only reports values that parse and pass [valid].
 * Keeps its own text so the user can type intermediate states like "1." freely.
 */
export function NumberField({
  label,
  value,
  onValue,
  integer,
  valid = (v) => v >= 0,
}: {
  label: string;
  value: number;
  onValue: (v: number) => void;
  integer?: boolean;
  valid?: (v: number) => boolean;
}) {
  const shown = integer ? String(Math.trunc(value)) : String(value);
  const [text, setText] = useState(shown);
  useEffect(() => setText(shown), [shown]);
  const accepts = (s: string) => {
    if (!/^-?\d*\.?\d+$|^-?\d+\.$/.test(s.trim())) return null;
    const v = Number(s);
    return Number.isFinite(v) && valid(v) && (!integer || Number.isInteger(v)) ? v : null;
  };
  return (
    <Field
      label={label}
      value={text}
      isError={accepts(text) == null}
      keyboardType={integer ? 'number-pad' : 'decimal-pad'}
      onChangeText={(s) => {
        setText(s);
        const v = accepts(s);
        if (v != null) onValue(v);
      }}
    />
  );
}

export function SwitchRow({
  label,
  value,
  onChange,
  supporting,
}: {
  label: string;
  value: boolean;
  onChange: (v: boolean) => void;
  supporting?: string;
}) {
  const c = useColors();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', minHeight: 56, gap: 12, paddingVertical: 4 }}>
      <View style={{ flex: 1 }}>
        <Body>{label}</Body>
        {supporting ? <Small>{supporting}</Small> : null}
      </View>
      <Switch
        value={value}
        onValueChange={onChange}
        accessibilityLabel={label}
        trackColor={{ true: c.primaryContainer, false: c.surfaceVariant }}
        thumbColor={value ? c.primary : c.outline}
      />
    </View>
  );
}

export function RadioRow({ label, selected, onPress, disabled }: { label: string; selected: boolean; onPress: () => void; disabled?: boolean }) {
  const c = useColors();
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected, disabled: !!disabled }}
      onPress={onPress}
      disabled={disabled}
      style={{ flexDirection: 'row', alignItems: 'center', minHeight: 52, gap: 12, opacity: disabled ? 0.5 : 1 }}
    >
      <View style={[styles.radio, { borderColor: selected ? c.primary : c.outline }]}>
        {selected ? <View style={[styles.radioDot, { backgroundColor: c.primary }]} /> : null}
      </View>
      <Body style={{ flex: 1 }}>{label}</Body>
    </Pressable>
  );
}

export function KeyValueRow({ label, value, emphasize }: { label: string; value: string; emphasize?: boolean }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 6, gap: 8 }}>
      <Body style={[{ flex: 1 }, emphasize && { fontSize: 22, fontWeight: 'bold' }]}>{label}</Body>
      <Body style={emphasize ? { fontSize: 28, fontWeight: 'bold' } : undefined}>{value}</Body>
    </View>
  );
}

export function Divider() {
  const c = useColors();
  return <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: c.outline, marginVertical: 12 }} />;
}

/** Material-style alert dialog. */
export function Dialog({
  visible,
  title,
  children,
  actions,
  onDismiss,
}: {
  visible: boolean;
  title?: string;
  children?: ReactNode;
  actions: ReactNode;
  onDismiss: () => void;
}) {
  const c = useColors();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss}>
      <Pressable style={styles.scrim} onPress={onDismiss}>
        <Pressable style={[styles.dialog, { backgroundColor: c.background }]} onPress={() => undefined}>
          {title ? <Text style={{ fontSize: 22, fontWeight: 'bold', color: c.onBackground, marginBottom: 12 }}>{title}</Text> : null}
          {children}
          <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: 8, marginTop: 16, flexWrap: 'wrap' }}>{actions}</View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/** Header icon button with a text glyph (no icon font needed). */
export function HeaderButton({ glyph, label, onPress }: { glyph: string; label: string; onPress: () => void }) {
  const c = useColors();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} hitSlop={8} style={{ paddingHorizontal: 8, paddingVertical: 4 }}>
      <Text style={{ fontSize: 22, color: c.onBackground }}>{glyph}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    borderRadius: 28,
    borderWidth: 1,
    paddingHorizontal: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  radio: { width: 22, height: 22, borderRadius: 11, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  radioDot: { width: 11, height: 11, borderRadius: 6 },
  scrim: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 24 },
  dialog: { borderRadius: 24, padding: 24 },
});
