#import "DeepArPlugin.h"
#if __has_include(<deepar_flutter_plus/deepar_flutter_plus-Swift.h>)
#import <deepar_flutter_plus/deepar_flutter_plus-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "deepar_flutter_plus-Swift.h"
#endif

@implementation DeepArPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftDeepArPlugin registerWithRegistrar:registrar];
}
@end
