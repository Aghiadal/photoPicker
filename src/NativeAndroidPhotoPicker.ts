import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { ImageLibraryOptions , Callback} from './types';

export interface Spec extends TurboModule {
  launchImageLibrary(options: ImageLibraryOptions, callback: Callback ): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('AndroidPhotoPicker');
