import { NativeModules } from 'react-native';
import type { ImageLibraryOptions, Callback } from './types';

const LINKING_ERROR =
  `The package 'react-native-android-photo-picker' doesn't seem to be linked. Make sure: \n\n` +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';



const {AndroidPhotoPicker} = NativeModules;

// @ts-expect-error
const isTurboModuleEnabled = global.__turboModuleProxy != null;

const AndroidPhotoPickerModule = isTurboModuleEnabled
  ? require('./NativeAndroidPhotoPicker').default
  : AndroidPhotoPicker;

const AndroidPhotoPickerPackage = AndroidPhotoPickerModule
  ? AndroidPhotoPickerModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

    export function launchImageLibrary (options: ImageLibraryOptions, callback: Callback) {
      AndroidPhotoPickerPackage.launchImageLibrary(options, callback)
    }
  