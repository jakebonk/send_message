package com.example.send_message

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.provider.Telephony
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry


class FlutterSmsPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var mChannel: MethodChannel
  private var activity: Activity? = null
  private val REQUEST_CODE_SEND_SMS = 205

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    setupCallbackChannels(flutterPluginBinding.binaryMessenger)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    teardown()
  }

  private fun setupCallbackChannels(messenger: BinaryMessenger) {
    mChannel = MethodChannel(messenger, "send_message")
    mChannel.setMethodCallHandler(this)
  }

  private fun teardown() {
    mChannel.setMethodCallHandler(null)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "sendSMS" -> {
//                if (!canSendSMS()) {
//                    result.error(
//                        "device_not_capable",
//                        "The current device is not capable of sending text messages.",
//                        "A device may be unable to send messages if it does not support messaging or if it is not currently configured to send messages. This only applies to the ability to send text messages via iMessage, SMS, and MMS."
//                    )
//                    return
//                }
        val message = call.argument<String?>("message") ?: ""
        val recipients = call.argument<String?>("recipients") ?: ""
        val sendDirect = call.argument<Boolean?>("sendDirect") ?: false
        val attachmentPaths = call.argument<List<String>?>("attachmentPaths") ?: listOf()
        sendSMS(result, recipients, message, sendDirect, attachmentPaths)
      }

      "canSendSMS" -> result.success(canSendSMS())
      else -> result.notImplemented()
    }
  }

  @TargetApi(Build.VERSION_CODES.ECLAIR)
  private fun canSendSMS(): Boolean {
    val currentActivity = activity ?: return false

    if (!currentActivity.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
      return false
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("smsto:")
    val activityInfo = intent.resolveActivityInfo(currentActivity.packageManager, intent.flags.toInt())
    return !(activityInfo == null || !activityInfo.exported)
  }

  private fun sendSMS(result: Result, phones: String, message: String, sendDirect: Boolean, attachments: List<String>) {
    if (sendDirect) {
      sendSMSDirect(result, phones, message)
    } else {
      sendSMSDialog(result, phones, message, attachments)
    }
  }

  private fun sendSMSDirect(result: Result, phones: String, message: String) {
    val currentActivity = activity ?: run {
      result.error("no_activity", "Activity is not available", null)
      return
    }

    // SmsManager is android.telephony
    val sentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.getBroadcast(currentActivity, 0, Intent("SMS_SENT_ACTION"), PendingIntent.FLAG_IMMUTABLE)
    } else {
      PendingIntent.getBroadcast(currentActivity, 0, Intent("SMS_SENT_ACTION"), 0)
    }

    val mSmsManager = SmsManager.getDefault()
    val numbers = phones.split(";")

    for (num in numbers) {
      Log.d("Flutter SMS", "msg.length() : " + message.toByteArray().size)
      if (message.toByteArray().size > 80) {
        val partMessage = mSmsManager.divideMessage(message)
        mSmsManager.sendMultipartTextMessage(num, null, partMessage, null, null)
      } else {
        mSmsManager.sendTextMessage(num, null, message, sentIntent, null)
      }
    }

    result.success("SMS Sent!")
  }

  private fun sendSMSDialog(result: Result, phones: String, message: String, attachments: List<String>) {
    val currentActivity = activity ?: run {
      result.error("no_activity", "Activity is not available", null)
      return
    }

    // Try to get default SMS package, with fallback to intent resolution
    var defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(currentActivity)
    if (defaultSmsPackage == null) {
      // Fallback: resolve the default handler for smsto: URIs
      val resolveIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
      val resolveInfo = currentActivity.packageManager.resolveActivity(resolveIntent, PackageManager.MATCH_DEFAULT_ONLY)
      defaultSmsPackage = resolveInfo?.activityInfo?.packageName
      Log.d("FlutterSMS", "Fallback SMS package from intent resolution: $defaultSmsPackage")
    }
    Log.d("FlutterSMS", "Default SMS package: $defaultSmsPackage")
    
    // Normalize recipient separator for MMS/SMS URIs
    val normalizedPhones = phones.replace(";", ",")        

    val intent: Intent
    if (attachments.isEmpty()) {
      intent = Intent(Intent.ACTION_SENDTO)
      intent.data = Uri.parse("smsto:$normalizedPhones")
      intent.putExtra("sms_body", message)
      intent.putExtra(Intent.EXTRA_TEXT, message)
      if (defaultSmsPackage != null) {
        intent.`package` = defaultSmsPackage
      }
      intent.putExtra(Intent.EXTRA_PHONE_NUMBER, normalizedPhones)
    } else {
      // For MMS with attachments, we need to use ACTION_SEND or ACTION_SEND_MULTIPLE
      // but setting type clears the data URI, so we rely on extras for the recipient
      intent = Intent(Intent.ACTION_SEND)
      intent.type = "image/*"
      
      if (attachments.size == 1) {
        val file = java.io.File(attachments[0])
        val uri = androidx.core.content.FileProvider.getUriForFile(currentActivity, currentActivity.packageName + ".send_message.fileprovider", file)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
      } else {
        intent.action = Intent.ACTION_SEND_MULTIPLE
        val uris = java.util.ArrayList<Uri>()
        for (path in attachments) {
          val file = java.io.File(path)
          val uri = androidx.core.content.FileProvider.getUriForFile(currentActivity, currentActivity.packageName + ".send_message.fileprovider", file)
          uris.add(uri)
        }
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
      }
      
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      // Set recipient using multiple extras that different SMS apps recognize
      intent.putExtra("address", normalizedPhones)
      intent.putExtra("sms_body", message)
      intent.putExtra("subject", "")
      intent.putExtra(Intent.EXTRA_PHONE_NUMBER, normalizedPhones)
      // Some apps use these extras
      intent.putExtra("exit_on_sent", true)
      
      if (defaultSmsPackage != null) {
        intent.`package` = defaultSmsPackage
      }
    }

    currentActivity.startActivityForResult(intent, REQUEST_CODE_SEND_SMS)
    result.success("SMS Sent!")
  }
}