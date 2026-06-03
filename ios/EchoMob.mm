#import "EchoMob.h"

@implementation EchoMob
- (NSNumber *)multiply:(double)a b:(double)b {
    NSNumber *result = @(a * b);

    return result;
}

- (NSString *)getDayGreeting:(double)n {
    NSArray *days = @[@"dimanche", @"lundi", @"mardi", @"mercredi", @"jeudi", @"vendredi", @"samedi"];
    int index = (((int)n % 7) + 7) % 7;
    return [NSString stringWithFormat:@"bonjour %@", days[index]];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeEchoMobSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"EchoMob";
}

@end
