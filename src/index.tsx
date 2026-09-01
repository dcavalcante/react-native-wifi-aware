import type { EventSubscription } from 'react-native';
import NativeWifiAware, {
  type NativeAwareEvent,
  type NativeCapabilities,
  type NativeDataPathEvent,
  type NativeDataPathOptions,
  type NativeDiscoveryOptions,
} from './NativeWifiAware';

export type CapabilitySnapshot = Readonly<NativeCapabilities>;
export type AwareSessionHandle = string;
export type DiscoverySessionHandle = string;
export type PeerHandle = string;
export type DataPathHandle = string;
export type DataPathSecurityMode = 'psk' | 'paired';
export type DiscoveryOptions = Readonly<{
  serviceName: string;
  securityMode: DataPathSecurityMode;
}>;
export type DataPathRole = 'server' | 'client';
export type DataPathOptions =
  | Readonly<{
      role: DataPathRole;
      security: Readonly<{ mode: 'psk'; passphrase: string }>;
    }>
  | Readonly<{
      role: DataPathRole;
      security: Readonly<{ mode: 'paired' }>;
    }>;
export type DataPathState = 'connected' | 'failed' | 'lost' | 'closed';
export type DataPathEvent = Readonly<
  Omit<NativeDataPathEvent, 'state'> & { state: DataPathState }
>;
export type PeerFoundEvent = Readonly<
  Pick<NativeAwareEvent, 'discoverySessionHandle' | 'peerHandle'>
>;
export type MessageReceivedEvent = Readonly<
  Pick<NativeAwareEvent, 'discoverySessionHandle' | 'peerHandle' | 'payload'>
>;

export const WIFI_AWARE_ERROR_CODES = [
  'UNSUPPORTED',
  'UNAVAILABLE',
  'PERMISSION_DENIED',
  'INVALID_ARGUMENT',
  'INVALID_HANDLE',
  'SESSION_CLOSED',
  'DISCOVERY_CLOSED',
  'ATTACH_FAILED',
  'DISCOVERY_FAILED',
  'MESSAGE_SEND_FAILED',
  'DATA_PATH_FAILED',
  'DATA_PATH_CLOSED',
  'INTERNAL_ERROR',
] as const;

export type WifiAwareErrorCode = (typeof WIFI_AWARE_ERROR_CODES)[number];
export type WifiAwareError = Error & { code: WifiAwareErrorCode };

export function isWifiAwareError(error: unknown): error is WifiAwareError {
  return (
    error instanceof Error &&
    typeof (error as Partial<WifiAwareError>).code === 'string' &&
    (WIFI_AWARE_ERROR_CODES as readonly string[]).includes(
      (error as Partial<WifiAwareError>).code as string
    )
  );
}

/** Returns feature support and current system availability without attaching. */
export function getCapabilities(): CapabilitySnapshot {
  return NativeWifiAware.getCapabilities();
}

export const attach = (): Promise<AwareSessionHandle> =>
  NativeWifiAware.attach();
export const closeSession = (handle: AwareSessionHandle): Promise<void> =>
  NativeWifiAware.closeSession(handle);
export const publish = (
  handle: AwareSessionHandle,
  options: DiscoveryOptions
): Promise<DiscoverySessionHandle> =>
  NativeWifiAware.publish(handle, normalizeDiscoveryOptions(options));
export const subscribe = (
  handle: AwareSessionHandle,
  options: DiscoveryOptions
): Promise<DiscoverySessionHandle> =>
  NativeWifiAware.subscribe(handle, normalizeDiscoveryOptions(options));
/**
 * Presents the platform pairing UI for a live discovery role. Resolving means
 * the system UI was presented; pairing and connection are asynchronous.
 */
export const presentPairing = (handle: DiscoverySessionHandle): Promise<void> =>
  NativeWifiAware.presentPairing(handle);
export const closeDiscoverySession = (
  handle: DiscoverySessionHandle
): Promise<void> => NativeWifiAware.closeDiscoverySession(handle);
export function sendMessage(
  discoverySessionHandle: DiscoverySessionHandle,
  peerHandle: PeerHandle,
  payload: ReadonlyArray<number>
): Promise<void> {
  return NativeWifiAware.sendMessage(
    discoverySessionHandle,
    peerHandle,
    normalizeBytePayload(payload)
  );
}
export function openDataPath(
  discoverySessionHandle: DiscoverySessionHandle,
  peerHandle: PeerHandle | undefined,
  options: DataPathOptions
): Promise<DataPathHandle> {
  return NativeWifiAware.openDataPath(
    discoverySessionHandle,
    peerHandle ?? '',
    normalizeDataPathOptions(options)
  );
}
export const closeDataPath = (handle: DataPathHandle): Promise<void> =>
  NativeWifiAware.closeDataPath(handle);
export const onPeerFound = (
  listener: (event: PeerFoundEvent) => void
): EventSubscription =>
  NativeWifiAware.onPeerFound((event) => {
    if (event.eventType !== 'peerFound') return;
    listener({
      discoverySessionHandle: event.discoverySessionHandle,
      peerHandle: event.peerHandle,
    });
  });
export const onMessageReceived = (
  listener: (event: MessageReceivedEvent) => void
): EventSubscription =>
  NativeWifiAware.onPeerFound((event) => {
    if (event.eventType !== 'messageReceived') return;
    listener({
      discoverySessionHandle: event.discoverySessionHandle,
      peerHandle: event.peerHandle,
      payload: event.payload,
    });
  });
export const onDataPathState = (
  listener: (event: DataPathEvent) => void
): EventSubscription =>
  NativeWifiAware.onDataPathState((event) => {
    listener({
      dataPathHandle: event.dataPathHandle,
      state: event.state as DataPathState,
      reason: event.reason,
    });
  });

function normalizeBytePayload(payload: ReadonlyArray<number>): number[] {
  if (!Array.isArray(payload)) {
    throw wifiAwareError(
      'INVALID_ARGUMENT',
      'Message payload must be an array'
    );
  }
  if (
    payload.some(
      (value) => !Number.isInteger(value) || value < 0 || value > 255
    )
  ) {
    throw wifiAwareError(
      'INVALID_ARGUMENT',
      'Message payload values must be integers from 0 to 255'
    );
  }
  return [...payload];
}

function normalizeDiscoveryOptions(
  options: DiscoveryOptions
): NativeDiscoveryOptions {
  if (
    !options ||
    typeof options.serviceName !== 'string' ||
    !options.serviceName
  ) {
    throw wifiAwareError(
      'INVALID_ARGUMENT',
      'Discovery options require a non-empty serviceName'
    );
  }
  if (options.securityMode !== 'psk' && options.securityMode !== 'paired') {
    throw wifiAwareError(
      'INVALID_ARGUMENT',
      'Discovery securityMode must be psk or paired'
    );
  }
  return {
    serviceName: options.serviceName,
    securityMode: options.securityMode,
  };
}

function normalizeDataPathOptions(
  options: DataPathOptions
): NativeDataPathOptions {
  if (!options || (options.role !== 'server' && options.role !== 'client')) {
    throw wifiAwareError(
      'INVALID_ARGUMENT',
      'Data-path options require a server/client role'
    );
  }
  if (!options.security || options.security.mode === 'paired') {
    if (options.security?.mode !== 'paired') {
      throw wifiAwareError(
        'INVALID_ARGUMENT',
        'Data-path security must be psk or paired'
      );
    }
    return { role: options.role, securityMode: 'paired' };
  }
  if (
    options.security.mode !== 'psk' ||
    typeof options.security.passphrase !== 'string' ||
    options.security.passphrase.length === 0
  ) {
    throw wifiAwareError(
      'INVALID_ARGUMENT',
      'PSK data paths require a non-empty passphrase'
    );
  }
  return {
    role: options.role,
    securityMode: 'psk',
    passphrase: options.security.passphrase,
  };
}

function wifiAwareError(
  code: WifiAwareErrorCode,
  message: string
): WifiAwareError {
  return Object.assign(new Error(message), { code });
}
