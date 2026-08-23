import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type NativeCapabilities = {
  isSupported: boolean;
  isAvailable: boolean;
};

export interface Spec extends TurboModule {
  getCapabilities(): NativeCapabilities;
}

export default TurboModuleRegistry.getEnforcing<Spec>('WifiAware');
