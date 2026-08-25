#import "WifiAware.h"
#import "WifiAware-Swift.h"

@implementation WifiAware

- (instancetype)init
{
  self = [super init];
  if (self) {
    __weak WifiAware *weakSelf = self;
    [[WifiAwareCoordinator shared] setEventSink:^(NSDictionary *event) {
      [weakSelf emitOnPeerFound:event];
    }];
  }
  return self;
}

- (NSDictionary *)getCapabilities {
  return [[WifiAwareCoordinator shared] capabilities];
}

- (void)attach:(RCTPromiseResolveBlock)resolve
        reject:(RCTPromiseRejectBlock)reject
{
  [[WifiAwareCoordinator shared] attachWithResolve:resolve reject:reject];
}

- (void)closeSession:(NSString *)handle
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  [[WifiAwareCoordinator shared] closeSessionWithHandle:handle resolve:resolve reject:reject];
}

- (void)publish:(NSString *)handle
        options:(JS::NativeWifiAware::NativeDiscoveryOptions &)options
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject
{
  [[WifiAwareCoordinator shared]
      publishWithSessionHandle:handle
                   serviceName:[NSString stringWithUTF8String:options.serviceName.c_str()]
                       resolve:resolve
                        reject:reject];
}

- (void)subscribe:(NSString *)handle
          options:(JS::NativeWifiAware::NativeDiscoveryOptions &)options
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  [[WifiAwareCoordinator shared]
      subscribeWithSessionHandle:handle
                     serviceName:[NSString stringWithUTF8String:options.serviceName.c_str()]
                         resolve:resolve
                          reject:reject];
}

- (void)presentPairing:(NSString *)handle
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject
{
  [[WifiAwareCoordinator shared]
      presentPairingWithDiscoveryHandle:handle
                                resolve:resolve
                                 reject:reject];
}

- (void)closeDiscoverySession:(NSString *)handle
                      resolve:(RCTPromiseResolveBlock)resolve
                      reject:(RCTPromiseRejectBlock)reject
{
  [[WifiAwareCoordinator shared] closeDiscoveryWithHandle:handle resolve:resolve reject:reject];
}

- (void)sendMessage:(NSString *)discoverySessionHandle
          peerHandle:(NSString *)peerHandle
             payload:(NSArray<NSNumber *> *)payload
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (void)openDataPath:(NSString *)discoverySessionHandle
           peerHandle:(NSString *)peerHandle
              options:(JS::NativeWifiAware::NativeDataPathOptions &)options
              resolve:(RCTPromiseResolveBlock)resolve
               reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (void)closeDataPath:(NSString *)handle
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeWifiAwareSpecJSI>(params);
}

- (void)invalidate
{
  [[WifiAwareCoordinator shared] invalidate];
  [super invalidate];
}

+ (NSString *)moduleName
{
  return @"WifiAware";
}

@end
