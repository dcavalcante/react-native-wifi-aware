import { Text, View, StyleSheet } from 'react-native';
import { getCapabilities } from 'react-native-wifi-aware';

export default function App() {
  const capabilities = getCapabilities();

  return (
    <View style={styles.container}>
      <Text>Supported: {String(capabilities.isSupported)}</Text>
      <Text>Available: {String(capabilities.isAvailable)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
