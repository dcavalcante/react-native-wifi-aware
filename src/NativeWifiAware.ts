import {
  TurboModuleRegistry,
  type EventSubscription,
  type TurboModule,
} from 'react-native';

type EventEmitter<T> = (listener: (event: T) => void) => EventSubscription;

export type NativeCapabilities = {
  isSupported: boolean;
  isAvailable: boolean;
};

export type NativeDiscoveryOptions = { serviceName: string };
export type NativePeerFoundEvent = {
  discoverySessionHandle: string;
  peerHandle: string;
};

export interface Spec extends TurboModule {
  getCapabilities(): NativeCapabilities;
  attach(): Promise<string>;
  closeSession(handle: string): Promise<void>;
  publish(handle: string, options: NativeDiscoveryOptions): Promise<string>;
  subscribe(handle: string, options: NativeDiscoveryOptions): Promise<string>;
  closeDiscoverySession(handle: string): Promise<void>;
  readonly onPeerFound: EventEmitter<NativePeerFoundEvent>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('WifiAware');
