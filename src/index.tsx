import type { EventSubscription } from 'react-native';
import NativeWifiAware, {
  type NativeCapabilities,
  type NativeDiscoveryOptions,
  type NativeAwareEvent,
} from './NativeWifiAware';

export type CapabilitySnapshot = Readonly<NativeCapabilities>;
export type AwareSessionHandle = string;
export type DiscoverySessionHandle = string;
export type PeerHandle = string;
export type DiscoveryOptions = Readonly<NativeDiscoveryOptions>;
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
): Promise<DiscoverySessionHandle> => NativeWifiAware.publish(handle, options);
export const subscribe = (
  handle: AwareSessionHandle,
  options: DiscoveryOptions
): Promise<DiscoverySessionHandle> =>
  NativeWifiAware.subscribe(handle, options);
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

function wifiAwareError(
  code: WifiAwareErrorCode,
  message: string
): WifiAwareError {
  return Object.assign(new Error(message), { code });
}
