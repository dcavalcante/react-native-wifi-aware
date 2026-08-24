import { useEffect, useState } from 'react';
import {
  Button,
  PermissionsAndroid,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  attach,
  closeDiscoverySession,
  closeSession,
  getCapabilities,
  onPeerFound,
  publish,
  subscribe,
} from 'react-native-wifi-aware';

const SERVICE_NAME = 'com.wifiaware.stage2';

async function requestDiscoveryPermission(): Promise<boolean> {
  if (Platform.OS !== 'android') return false;

  const permission =
    Number(Platform.Version) >= 33
      ? PermissionsAndroid.PERMISSIONS.NEARBY_WIFI_DEVICES
      : PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION;
  return (
    (await PermissionsAndroid.request(permission)) ===
    PermissionsAndroid.RESULTS.GRANTED
  );
}

export default function App() {
  const capabilities = getCapabilities();
  const [attachStatus, setAttachStatus] = useState('Attaching...');
  const [discoveryStatus, setDiscoveryStatus] = useState(
    'Choose Publish on one device and Subscribe on the other.'
  );
  const [activeSession, setActiveSession] = useState<string | null>(null);
  const [activeDiscovery, setActiveDiscovery] = useState<string | null>(null);

  useEffect(() => {
    attach()
      .then(async (handle) => {
        await closeSession(handle);
        setAttachStatus('Attach/close: passed');
      })
      .catch((error) => {
        const detail = error instanceof Error ? error.message : String(error);
        setAttachStatus(`Attach/close: ${detail}`);
      });
  }, []);

  useEffect(() => {
    const eventSubscription = onPeerFound(
      ({ discoverySessionHandle, peerHandle }) => {
        setDiscoveryStatus(
          `Peer found: ${peerHandle} in ${discoverySessionHandle}`
        );
      }
    );
    return () => eventSubscription.remove();
  }, []);

  const startDiscovery = async (mode: 'publish' | 'subscribe') => {
    if (activeSession || activeDiscovery) {
      setDiscoveryStatus(
        'Close the active discovery test before starting another one.'
      );
      return;
    }
    if (!(await requestDiscoveryPermission())) {
      setDiscoveryStatus('Discovery permission was not granted.');
      return;
    }

    try {
      const session = await attach();
      const discovery =
        mode === 'publish'
          ? await publish(session, { serviceName: SERVICE_NAME })
          : await subscribe(session, { serviceName: SERVICE_NAME });
      setActiveSession(session);
      setActiveDiscovery(discovery);
      setDiscoveryStatus(
        `${mode === 'publish' ? 'Publishing' : 'Subscribing'}: ${discovery}`
      );
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setDiscoveryStatus(`${mode} failed: ${detail}`);
    }
  };

  const stopDiscovery = async () => {
    if (!activeSession || !activeDiscovery) {
      setDiscoveryStatus('No active discovery test to close.');
      return;
    }

    try {
      await closeDiscoverySession(activeDiscovery);
      await closeSession(activeSession);
      setActiveDiscovery(null);
      setActiveSession(null);
      setDiscoveryStatus('Discovery and parent session closed.');
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setDiscoveryStatus(`Close failed: ${detail}`);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.value}>
        Supported: {String(capabilities.isSupported)}
      </Text>
      <Text style={styles.value}>
        Available: {String(capabilities.isAvailable)}
      </Text>
      <Text style={styles.value}>{attachStatus}</Text>
      <View style={styles.buttonList}>
        <View style={styles.button}>
          <Button
            title="Publish test service"
            onPress={() => void startDiscovery('publish')}
          />
        </View>
        <View style={styles.button}>
          <Button
            title="Subscribe test service"
            onPress={() => void startDiscovery('subscribe')}
          />
        </View>
        <View style={styles.button}>
          <Button title="Close test" onPress={() => void stopDiscovery()} />
        </View>
      </View>
      <Text style={styles.status}>{discoveryStatus}</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    backgroundColor: '#101214',
    flex: 1,
    justifyContent: 'center',
  },
  value: {
    color: '#ffffff',
    fontSize: 20,
  },
  status: {
    color: '#ffffff',
    fontSize: 14,
    marginHorizontal: 24,
    marginTop: 20,
    textAlign: 'center',
  },
  buttonList: {
    marginTop: 20,
    width: '80%',
  },
  button: {
    marginTop: 12,
  },
});
