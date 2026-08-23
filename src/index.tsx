import NativeWifiAware, { type NativeCapabilities } from './NativeWifiAware';

export type CapabilitySnapshot = Readonly<NativeCapabilities>;

/** Returns feature support and current system availability without attaching. */
export function getCapabilities(): CapabilitySnapshot {
  return NativeWifiAware.getCapabilities();
}
