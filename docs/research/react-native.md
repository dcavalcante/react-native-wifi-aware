# React Native, packaging and Expo

The generated platform-native TurboModule (`kotlin-objc`) is the current official library path: Codegen consumes the TypeScript spec and native implementations extend generated interfaces. [RN library guide](https://reactnative.dev/docs/the-new-architecture/create-module-library)

Swift business logic should sit behind a thin Objective-C++ TurboModule adapter because Swift/C++ interop is not a pure-Swift boundary. [Swift TurboModules](https://reactnative.dev/docs/0.81/the-new-architecture/turbo-modules-with-swift) Native-to-JS events must have removable subscriptions; native cleanup belongs in Android invalidation / iOS invalidation lifecycles.

Expo Go cannot load unbundled native code. Expo development builds can; a future optional config plugin may configure manifests, entitlements and service declarations without making Expo a runtime dependency. [Expo custom native code](https://docs.expo.dev/workflow/customizing/) · [Library plugins](https://docs.expo.dev/config-plugins/development-for-libraries/)
