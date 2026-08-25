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
  onMessageReceived,
  onPeerFound,
  openDataPath,
  presentPairing,
  publish,
  sendMessage,
  subscribe,
} from 'react-native-wifi-aware';

const SERVICE_NAME =
  Platform.OS === 'ios' ? '_rn-aware._tcp' : 'com.wifiaware.stage4';
const HELLO_MESSAGE = [112, 105, 110, 103];
const DATA_PATH_READY_MESSAGE = [100, 97, 116, 97, 45, 112, 97, 116, 104];
const DATA_PATH_PASSPHRASE = 'stage4-demo-passphrase';

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
  const [messageStatus, setMessageStatus] = useState(
    'Subscribe first, then send hello to the discovered peer.'
  );
  const [messageLog, setMessageLog] = useState<string[]>([]);
  const [activeDataPath, setActiveDataPath] = useState<string | null>(null);
  const [dataPathStatus, setDataPathStatus] = useState(
    'Exchange hello first. The publisher starts the server, then the subscriber connects.'
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

  useEffect(() => {
    const eventSubscription = onMessageReceived(
      ({ discoverySessionHandle, peerHandle, payload }) => {
        const received = String.fromCharCode(...payload);
        setLastPeer({ discoverySessionHandle, peerHandle });
        setMessageLog((current) => [
          ...current,
          `Received from ${peerHandle}: ${received}`,
        ]);
        if (received === String.fromCharCode(...DATA_PATH_READY_MESSAGE)) {
          setDataPathStatus(
            'Publisher server request is ready. Start the subscriber client data path.'
          );
        }
        setMessageStatus(
          'Message received. This device can now send a reply to that peer.'
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
      setActiveMode(mode);
      setLastPeer(null);
      setMessageLog([]);
      setActiveDataPath(null);
      setDataPathLog([]);
      setDataPathStatus(
        mode === 'publish'
          ? 'Wait for subscriber hello, then start the server data path.'
          : 'Discover the publisher, send hello, then wait for data-path ready.'
      );
      setMessageStatus(
        mode === 'subscribe'
          ? 'Waiting for a publisher peer, then send hello.'
          : 'Waiting for subscriber hello; reply after it arrives.'
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
    if (!lastPeer) {
      setDataPathStatus(
        'No peer is available. Discover first; the publisher also needs subscriber hello.'
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
      const handle = await openDataPath(
        lastPeer.discoverySessionHandle,
        lastPeer.peerHandle,
        { role, passphrase: DATA_PATH_PASSPHRASE }
      );
      setActiveDataPath(handle);
      if (role === 'server') {
        await sendMessage(
          lastPeer.discoverySessionHandle,
          lastPeer.peerHandle,
          DATA_PATH_READY_MESSAGE
        );
        setDataPathStatus(
          `Server request registered: ${handle}. Readiness message acknowledged.`
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

  const sendHello = async () => {
    if (!lastPeer) {
      setMessageStatus(
        'No peer is available yet. Discover or receive a message first.'
      );
      return;
    }
    try {
      setMessageStatus('Sending hello...');
      await sendMessage(
        lastPeer.discoverySessionHandle,
        lastPeer.peerHandle,
        HELLO_MESSAGE
      );
      setMessageStatus('Hello acknowledged by the peer.');
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setMessageStatus(`Send failed: ${detail}`);
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
      setMessageStatus('Message test closed.');
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
            title="Send hello to last peer"
            onPress={() => void sendHello()}
          />
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
      <Text style={styles.status}>{messageStatus}</Text>
      <Text style={styles.status}>{dataPathStatus}</Text>
      {messageLog.map((entry, index) => (
        <Text key={`${entry}-${index}`} style={styles.log}>
          {entry}
        </Text>
      ))}
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
