# react-native-android-photo-picker

photo picker implementation for Android

## Installation

```sh
npm install react-native-android-photo-picker

yarn add react-native-android-photo-picker
```

## Usage


```js
import AndroidPhotoPicker from 'react-native-android-photo-picker';

// ...

const options = {
  mediaType: 'photo',
  cameraType:cameraType,
  quality: 1,
};

AndroidPhotoPicker.launchImageLibrary(options , (res) => {
    console.log('res', res);
    // handle the response
})
```


## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
