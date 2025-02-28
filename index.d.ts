import {ImageLibraryOptions, Callback} from './src/types';

declare module 'react-native-android-photo-picker' { 
    const AndroidPhotoPicker: { 
        launchImageLibrary: (options: ImageLibraryOptions, callback: Callback) => void;
    }
}

export default AndroidPhotoPicker;