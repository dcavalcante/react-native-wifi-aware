#import "WifiAware.h"

@implementation WifiAware
- (NSDictionary *)getCapabilities {
  // Stage 1 implements Android capability detection only. This conservative
  // response keeps the shared Codegen contract buildable without claiming
  // Apple Wi-Fi Aware hardware or runtime support.
  return @{
    @"isSupported": @NO,
    @"isAvailable": @NO,
  };
}

- (void)attach:(RCTPromiseResolveBlock)resolve
        reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (void)closeSession:(NSString *)handle
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (void)publish:(NSString *)handle
        options:(JS::NativeWifiAware::NativeDiscoveryOptions &)options
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (void)subscribe:(NSString *)handle
          options:(JS::NativeWifiAware::NativeDiscoveryOptions &)options
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (void)closeDiscoverySession:(NSString *)handle
                      resolve:(RCTPromiseResolveBlock)resolve
                      reject:(RCTPromiseRejectBlock)reject
{
  reject(@"UNSUPPORTED", @"Wi-Fi Aware is not implemented on Apple platforms yet", nil);
}

- (void)sendMessage:(NSString *)discoverySessionHandle
          peerHandle:(NSString *)peerHandle
             payload:(NSArray<NSNumber *> *)payload
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

+ (NSString *)moduleName
{
  return @"WifiAware";
}

@end
