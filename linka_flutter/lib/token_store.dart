import 'package:shared_preferences/shared_preferences.dart';

/// SharedPreferences-backed pairing-token storage - mirrors TokenStore.swift
/// (UserDefaults) and the Android app's SharedPreferences usage in
/// MainActivity.kt. Same key names, separate storage per platform since
/// there's no shared-prefs equivalent across OSes.
class TokenStore {
  static const _laptopKey = 'laptop_token';
  static const _phoneKey = 'phone_token';

  static Future<String?> get laptopToken async =>
      (await SharedPreferences.getInstance()).getString(_laptopKey);

  static Future<void> setLaptopToken(String token) async =>
      (await SharedPreferences.getInstance()).setString(_laptopKey, token);

  static Future<void> forgetLaptopToken() async =>
      (await SharedPreferences.getInstance()).remove(_laptopKey);

  static Future<String?> get phoneToken async =>
      (await SharedPreferences.getInstance()).getString(_phoneKey);

  static Future<void> setPhoneToken(String token) async =>
      (await SharedPreferences.getInstance()).setString(_phoneKey, token);

  static Future<void> forgetPhoneToken() async =>
      (await SharedPreferences.getInstance()).remove(_phoneKey);
}
