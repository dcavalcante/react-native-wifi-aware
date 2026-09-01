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
  closeDataPath,
  closeDiscoverySession,
  closeSession,
  getCapabilities,
  onDataPathState,
  onPeerFound,
  openDataPath,
  presentPairing,
  publish,
  subscribe,
} from 'react-native-wifi-aware';

const SERVICE_NAME = '_rn-aware._tcp';

async function requestDiscoveryPermission(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;

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
  const [activeMode, setActiveMode] = useState<'publish' | 'subscribe' | null>(
    null
  );
  const [lastPeer, setLastPeer] = useState<{
    discoverySessionHandle: string;
    peerHandle: string;
  } | null>(null);
  const [activeDataPath, setActiveDataPath] = useState<string | null>(null);
  const [dataPathStatus, setDataPathStatus] = useState(
    'Use paired discovery. The publisher starts the server, then the subscriber connects.'
  );
  const [dataPathLog, setDataPathLog] = useState<string[]>([]);

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
    const eventSubscription = onDataPathState(
      ({ dataPathHandle, state, reason }) => {
        setDataPathLog((current) => [
          ...current,
          `${state} ${dataPathHandle}: ${reason}`,
        ]);
        setDataPathStatus(`${state}: ${reason}`);
        if (state === 'failed' || state === 'lost' || state === 'closed') {
          setActiveDataPath((current) =>
            current === dataPathHandle ? null : current
          );
        }
      }
    );
    return () => eventSubscription.remove();
  }, []);

  useEffect(() => {
    const eventSubscription = onPeerFound(
      ({ discoverySessionHandle, peerHandle }) => {
        setLastPeer({ discoverySessionHandle, peerHandle });
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
          ? await publish(session, {
              serviceName: SERVICE_NAME,
              securityMode: 'paired',
            })
          : await subscribe(session, {
              serviceName: SERVICE_NAME,
              securityMode: 'paired',
            });
      setActiveSession(session);
      setActiveDiscovery(discovery);
      setActiveMode(mode);
      setLastPeer(null);
      setActiveDataPath(null);
      setDataPathLog([]);
      setDataPathStatus(
        mode === 'publish'
          ? 'Start the publisher server. Android publishers accept paired clients without a discovered peer; Apple publishers require the paired peer to appear first.'
          : 'Wait for the publisher peer, then start the subscriber client.'
      );
      setDiscoveryStatus(
        `${mode === 'publish' ? 'Publishing' : 'Subscribing'}: ${discovery}`
      );
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setDiscoveryStatus(`${mode} failed: ${detail}`);
    }
  };

  const startDataPath = async (role: 'server' | 'client') => {
    const requiredMode = role === 'server' ? 'publish' : 'subscribe';
    if (activeMode !== requiredMode) {
      setDataPathStatus(
        `The ${role} data path must run on the ${requiredMode} device.`
      );
      return;
    }
    if (!activeDiscovery) {
      setDataPathStatus('Start discovery before opening a data path.');
      return;
    }
    const androidPublisherServer =
      Platform.OS === 'android' && role === 'server';
    if (!lastPeer && !androidPublisherServer) {
      setDataPathStatus(
        Platform.OS === 'ios'
          ? 'No eligible Apple peer is available. Complete pairing first.'
          : 'No publisher peer is available. Discover first.'
      );
      return;
    }
    if (activeDataPath) {
      setDataPathStatus(
        'Close the active data path before starting another one.'
      );
      return;
    }
    try {
      setDataPathStatus(`Requesting ${role} data path...`);
      const handle = await openDataPath(activeDiscovery, lastPeer?.peerHandle, {
        role,
        security: { mode: 'paired' },
      });
      setActiveDataPath(handle);
      if (role === 'server') {
        setDataPathStatus(
          `Publisher server request registered: ${handle}. Start the subscriber client data path.`
        );
      } else {
        setDataPathStatus(`Client request registered: ${handle}.`);
      }
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setDataPathStatus(`${role} data path failed: ${detail}`);
    }
  };

  const startPairing = async () => {
    if (!activeDiscovery) {
      setDiscoveryStatus('Start a publish or subscribe test before pairing.');
      return;
    }
    try {
      await presentPairing(activeDiscovery);
      setDiscoveryStatus(
        activeMode === 'publish'
          ? 'Apple pairing UI is open. Keep this device discoverable.'
          : 'Apple device picker is open. Select the publisher and complete pairing.'
      );
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setDiscoveryStatus(`Pairing UI failed: ${detail}`);
    }
  };

  const stopDataPath = async () => {
    if (!activeDataPath) {
      setDataPathStatus('No active data path to close.');
      return;
    }
    try {
      await closeDataPath(activeDataPath);
      setActiveDataPath(null);
      setDataPathStatus('Data path closed.');
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setDataPathStatus(`Data-path close failed: ${detail}`);
    }
  };

  const stopDiscovery = async () => {
    if (!activeSession || !activeDiscovery) {
      setDiscoveryStatus('No active discovery test to close.');
      return;
    }

    try {
      if (activeDataPath) await closeDataPath(activeDataPath);
      await closeDiscoverySession(activeDiscovery);
      await closeSession(activeSession);
      setActiveDiscovery(null);
      setActiveSession(null);
      setActiveMode(null);
      setLastPeer(null);
      setActiveDataPath(null);
      setDiscoveryStatus('Discovery and parent session closed.');
      setDataPathStatus('Data-path test closed.');
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
      <Text style={styles.value}>
        Paired path: {String(capabilities.isPairedDataPathSupported)}
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
        {Platform.OS === 'ios' ? (
          <View style={styles.button}>
            <Button
              title="Pair Apple device"
              onPress={() => void startPairing()}
            />
          </View>
        ) : null}
        <View style={styles.button}>
          <Button title="Close test" onPress={() => void stopDiscovery()} />
        </View>
        <View style={styles.button}>
          <Button
            title="Start publisher server data path"
            onPress={() => void startDataPath('server')}
            disabled={activeMode !== 'publish'}
          />
        </View>
        <View style={styles.button}>
          <Button
            title="Start subscriber client data path"
            onPress={() => void startDataPath('client')}
            disabled={activeMode !== 'subscribe'}
          />
        </View>
        <View style={styles.button}>
          <Button title="Close data path" onPress={() => void stopDataPath()} />
        </View>
      </View>
      <Text style={styles.status}>{discoveryStatus}</Text>
      <Text style={styles.status}>{dataPathStatus}</Text>
      {dataPathLog.map((entry, index) => (
        <Text key={`${entry}-${index}`} style={styles.log}>
          {entry}
        </Text>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    backgroundColor: '#101214',
    flexGrow: 1,
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
  log: {
    color: '#a9d6ff',
    fontSize: 14,
    marginHorizontal: 24,
    marginTop: 8,
    textAlign: 'center',
  },
});
