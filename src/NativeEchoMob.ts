import { TurboModuleRegistry, type TurboModule } from 'react-native';

export interface Spec extends TurboModule {
  multiply(a: number, b: number): number;
  getDayGreeting(n: number): string;
}

export default TurboModuleRegistry.getEnforcing<Spec>('EchoMob');
