import 'dart:async';

import 'package:flutter_web_plugins/flutter_web_plugins.dart';

import 'src/send_message_platform.dart';

class FlutterSmsPlugin extends FlutterSmsPlatform {
  static void registerWith(Registrar registrar) {
    FlutterSmsPlatform.instance = FlutterSmsPlugin();
  }

  @override
  Future<String> sendSMS({
    required String message,
    required List<String> recipients,
    List<String>? attachmentPaths,
    bool sendDirect = false,
  }) async {
    throw UnsupportedError(
      'sendSMS is not supported on web. SMS functionality requires a native device.',
    );
  }

  @override
  Future<bool> canSendSMS() => Future.value(false);

  @override
  Future<bool> launchSms(String? number, [String? body]) {
    throw UnsupportedError(
      'launchSms is not supported on web. SMS functionality requires a native device.',
    );
  }

  @override
  Future<bool> launchSmsMulti(List<String> numbers, [String? body]) {
    throw UnsupportedError(
      'launchSmsMulti is not supported on web. SMS functionality requires a native device.',
    );
  }
}
