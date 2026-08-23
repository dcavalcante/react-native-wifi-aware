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
