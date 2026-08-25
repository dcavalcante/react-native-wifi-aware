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
export type NativeAwareEvent = {
  eventType: string;
  discoverySessionHandle: string;
  peerHandle: string;
  payload: number[];
};

export interface Spec extends TurboModule {
  getCapabilities(): NativeCapabilities;
  attach(): Promise<string>;
  closeSession(handle: string): Promise<void>;
  publish(handle: string, options: NativeDiscoveryOptions): Promise<string>;
  subscribe(handle: string, options: NativeDiscoveryOptions): Promise<string>;
  closeDiscoverySession(handle: string): Promise<void>;
  sendMessage(
    discoverySessionHandle: string,
    peerHandle: string,
    payload: number[]
  ): Promise<void>;
  // Keep the Stage-2 native event name: it is the single Codegen event stream.
  // Public wrappers below distinguish peer discovery from received messages.
  readonly onPeerFound: EventEmitter<NativeAwareEvent>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('WifiAware');
