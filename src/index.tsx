import type { EventSubscription } from 'react-native';
import NativeWifiAware, {
  type NativeCapabilities,
  type NativeDiscoveryOptions,
  type NativePeerFoundEvent,
} from './NativeWifiAware';

export type CapabilitySnapshot = Readonly<NativeCapabilities>;
export type AwareSessionHandle = string;
export type DiscoverySessionHandle = string;
export type PeerHandle = string;
export type DiscoveryOptions = Readonly<NativeDiscoveryOptions>;
export type PeerFoundEvent = Readonly<NativePeerFoundEvent>;

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
export const onPeerFound = (
  listener: (event: PeerFoundEvent) => void
): EventSubscription => NativeWifiAware.onPeerFound(listener);
